package com.jscreator.system.service;

import com.jscreator.common.util.Times;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@code GET /backup/download} 的数据导出。
 *
 * <p>原版用 npm 的 {@code mysqldump} 包（纯 JS 实现，不调 mysqldump 二进制）自己拼 SQL 再打成 zip。
 * 这里同样**不依赖外部二进制**：JDBC 逐表读 {@code SHOW CREATE TABLE} 与 {@code SELECT *}，
 * 自己生成可重复导入的 SQL，再按原版的格式包进 zip —— 因此运行镜像不用装 mysql-client。
 *
 * <p>与原版对齐的部分：头部注释与 {@code SET NAMES / SET FOREIGN_KEY_CHECKS} 包裹、
 * 每张表先 {@code DROP TABLE IF EXISTS} 再建表、{@code utf8mb4_0900_ai_ci} 替换成
 * {@code utf8mb4_general_ci}（兼容 MySQL 5.7）、zip 内除 SQL 外还有一个 README.txt。
 */
@Service
public class BackupService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataSource dataSource;

    public BackupService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 一次备份的全部产物：文件名（不含路径）+ zip 字节。 */
    public record Archive(String zipName, String sqlName, byte[] bytes) {
    }

    /** 当前连接的库名，用作备份文件名前缀（原版取的是环境变量 DB_NAME）。 */
    public String databaseName() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();
            return catalog != null ? catalog : "database";
        }
    }

    public Archive build() throws SQLException, IOException {
        String databaseName = databaseName();
        LocalDateTime now = LocalDateTime.now();
        String stamp = STAMP.format(now);
        String sqlName = databaseName + "-" + stamp + "-dump.sql";
        String zipName = databaseName + "-" + stamp + "-dump.zip";
        String sql = buildSql();

        ByteArrayOutputStream buf = new ByteArrayOutputStream(Math.max(8192, sql.length() / 4));
        try (ZipOutputStream zip = new ZipOutputStream(buf, StandardCharsets.UTF_8)) {
            zip.setLevel(6); // 与原版 archiver('zip', { zlib: { level: 6 } }) 一致
            zip.putNextEntry(new ZipEntry(sqlName));
            zip.write(sql.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(readme(databaseName).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new Archive(zipName, sqlName, buf.toByteArray());
    }

    private String readme(String databaseName) {
        return "JScreator 数据库备份\n"
                + "====================\n"
                + "数据库：" + databaseName + "\n"
                + "导出时间：" + Times.loginTime() + "\n"
                + "内容：全部表结构 + 数据（兼容 MySQL 5.7，utf8mb4_general_ci）\n"
                + "特性：先 DROP 后 CREATE（可重复导入）+ 禁用外键检查\n"
                + "注意：含敏感数据请妥善保管。\n\n";
    }

    /** 生成整库 SQL（结构 + 数据）。 */
    String buildSql() throws SQLException {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("-- JScreator 数据库备份（结构 + 数据，兼容 MySQL 5.7）\n");
        sb.append("SET NAMES utf8mb4;\n");
        sb.append("SET FOREIGN_KEY_CHECKS = 0;\n\n");

        try (Connection conn = dataSource.getConnection()) {
            for (String table : listTables(conn)) {
                sb.append("# ------------------------------------------------------------\n");
                sb.append("# SCHEMA DUMP FOR TABLE: ").append(table).append('\n');
                sb.append("# ------------------------------------------------------------\n\n");
                sb.append("DROP TABLE IF EXISTS `").append(table).append("`;\n");
                sb.append(compatible(showCreateTable(conn, table))).append(";\n\n");
                appendInserts(conn, table, sb);
            }
        }

        sb.append("\nSET FOREIGN_KEY_CHECKS = 1;\n");
        return sb.toString();
    }

    /** 当前库的全部表名（按库内顺序，保证同一份库导出的表序稳定）。 */
    private List<String> listTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private String showCreateTable(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
            if (rs.next()) {
                return rs.getString(2);
            }
        }
        throw new SQLException("无法读取表结构：" + table);
    }

    private void appendInserts(Connection conn, String table, StringBuilder sb) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM `" + table + "`")) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            StringBuilder columns = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                if (i > 1) {
                    columns.append(", ");
                }
                columns.append('`').append(meta.getColumnName(i)).append('`');
            }

            String prefix = "INSERT INTO `" + table + "` (" + columns + ") VALUES ";
            while (rs.next()) {
                sb.append(prefix).append('(');
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) {
                        sb.append(", ");
                    }
                    sb.append(sqlLiteral(rs.getObject(i)));
                }
                sb.append(");\n");
            }
            if (cols > 0) {
                sb.append('\n');
            }
        }
    }

    /** 原版做的字符集降级替换，保证导出的 SQL 能导进 MySQL 5.7。 */
    static String compatible(String sql) {
        return sql.replace("utf8mb4_0900_ai_ci", "utf8mb4_general_ci");
    }

    /** 把 JDBC 取到的值渲染成 MySQL 字面量。 */
    static String sqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof byte[] bytes) {
            StringBuilder hex = new StringBuilder("0x");
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        String text;
        if (value instanceof LocalDateTime dt) {
            text = DATE_TIME.format(dt);
        } else if (value instanceof LocalDate || value instanceof LocalTime) {
            text = value.toString();
        } else if (value instanceof java.sql.Timestamp ts) {
            text = DATE_TIME.format(ts.toLocalDateTime());
        } else if (value instanceof java.sql.Date d) {
            text = d.toString();
        } else if (value instanceof java.sql.Time t) {
            text = t.toString();
        } else {
            text = value.toString();
        }
        return "'" + escape(text) + "'";
    }

    /** MySQL 字符串字面量转义（与 mysqldump 的写法一致）。 */
    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\0' -> b.append("\\0");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\'' -> b.append("\\'");
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\u001a' -> b.append("\\Z");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
