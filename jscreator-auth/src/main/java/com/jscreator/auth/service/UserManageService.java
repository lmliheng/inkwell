package com.jscreator.auth.service;

import com.jscreator.auth.mapper.UserManageMapper;
import com.jscreator.auth.mapper.UserManageSqlProvider;
import com.jscreator.common.exception.BizException;
import com.jscreator.common.security.PasswordUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * user 域业务规则，对齐 Express 版 modules/user/user.service + user.dao：
 *
 * <ul>
 *   <li>能不能改目标：本人，或操作者是管理员（role_id === 1）</li>
 *   <li>管理员更新「无字段可更新」返回 false → controller 翻成 400；本人更新不看返回值（照常 200）</li>
 *   <li>新增用户先查邮箱、再查用户名，重复即 400（文案照抄）</li>
 *   <li>密码只走 {@link PasswordUtil#toHash}（SHA-256 hex），否则存量用户掉线</li>
 * </ul>
 *
 * <p>SQL 失败一律不吞：交给 GlobalExceptionHandler 渲染成
 * HTTP 500 + {@code {code:500,success:false,message:'服务器内部错误'}}，与原版 catch 分支一致。
 */
@Service
public class UserManageService {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** {@code user_getAllByPage} 的 SELECT 列序（用于补回值为 NULL 的列，见 {@link #withNullColumns}）。 */
    private static final List<String> LIST_COLUMNS = List.of(
            "id", "username", "email", "avatar", "created_at", "updated_at", "name", "area", "bio",
            "vip", "checkinDay", "role_id", "role_name");

    /** {@code user_getById} 的 SELECT 列序。 */
    private static final List<String> DETAIL_COLUMNS = List.of(
            "id", "username", "email", "avatar", "bio", "vip", "checkinDay", "name", "area",
            "socials", "featured_articles", "github_id", "created_at", "updated_at", "role_id", "role_name");

    /** 写库前要 JSON.stringify 的 JSON 列（原版 user_updateProfile 里的两个特例）。 */
    private static final List<String> JSON_COLUMNS = List.of("socials", "featured_articles");

    private final UserManageMapper userManageMapper;

    public UserManageService(UserManageMapper userManageMapper) {
        this.userManageMapper = userManageMapper;
    }

    /** actor 是否可编辑 target：本人或管理员（原版 UserService.canEditTarget）。 */
    public boolean canEditTarget(Object targetId, Long actorId) {
        Double target = jsNumber(targetId);
        if (target != null && actorId != null && target.doubleValue() == actorId.doubleValue()) {
            return true;
        }
        Long role = userManageMapper.roleIdOf(actorId);
        return role != null && role == 1L;
    }

    /** 本人更新资料：原版不关心有没有字段可更新（没有也照常 200）。 */
    public void updateSelfProfile(Object id, Map<String, Object> fields) {
        updateProfile(id, fields);
    }

    /** 管理员更新：返回 false 表示无字段可更新（对应原版 400 分支）。 */
    public boolean updateByAdmin(Object id, Map<String, Object> fields) {
        return updateProfile(id, fields);
    }

    public void unbindGithub(Long userId) {
        userManageMapper.clearGithubId(userId);
    }

    public void resetMyPassword(Long userId, String password) {
        userManageMapper.setPasswordHash(userId, PasswordUtil.toHash(password));
    }

    public void adminResetPassword(Object id, String password) {
        userManageMapper.setPasswordHash(id, PasswordUtil.toHash(password));
    }

    /** 分页列表：{@code {list,total,page,pageSize,size}}，size 是本页行数。 */
    public Map<String, Object> list(long page, long pageSize, String keyword) {
        String kw = (keyword == null || keyword.isEmpty()) ? null : "%" + keyword + "%";
        long offset = (page - 1) * pageSize;
        List<LinkedHashMap<String, Object>> rows = userManageMapper.listPage(kw, pageSize, offset);
        Long total = userManageMapper.countPage(kw);

        List<LinkedHashMap<String, Object>> list = new ArrayList<>(rows.size());
        for (LinkedHashMap<String, Object> row : rows) {
            list.add(withNullColumns(row, LIST_COLUMNS));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("total", total == null ? 0L : total);
        out.put("page", page);
        out.put("pageSize", pageSize);
        out.put("size", list.size());
        return out;
    }

    /** 详情：查不到返回 null（controller 翻成 404）。 */
    public LinkedHashMap<String, Object> detail(Object id) {
        LinkedHashMap<String, Object> found = userManageMapper.findDetailById(id);
        if (found == null) {
            return null;
        }
        LinkedHashMap<String, Object> row = withNullColumns(found, DETAIL_COLUMNS);
        // 与原版一样就地改这两个键（键序已在 withNullColumns 里固定成 SELECT 列序）
        row.put("socials", parseJsonArray(row.get("socials")));
        row.put("featured_articles", parseJsonArray(row.get("featured_articles")));
        return row;
    }

    /** 新增用户：查重失败抛 400（文案与原版完全一致），否则返回自增 id。 */
    public Object add(String username, String email, String password, Object roleId) {
        if (!userManageMapper.idsByEmail(email).isEmpty()) {
            throw new BizException(400, "邮箱已存在");
        }
        if (!userManageMapper.idsByUsername(username).isEmpty()) {
            throw new BizException(400, "用户名已存在");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("username", username);
        params.put("email", email);
        params.put("password", PasswordUtil.toHash(password));

        boolean hasRole = roleId != null && !"".equals(roleId);
        if (hasRole) {
            params.put("role_id", roleId);
            userManageMapper.insertUserWithRole(params);
        } else {
            userManageMapper.insertUser(params);
        }
        return params.get("id");
    }

    public void remove(Object id) {
        userManageMapper.deleteById(id);
    }

    /** 批量删除（filteredIds 已排除操作者本人），返回实际删除行数。 */
    public int removeBatch(List<?> filteredIds) {
        return userManageMapper.deleteBatchByIds(filteredIds);
    }

    // ================= 内部 =================

    private boolean updateProfile(Object id, Map<String, Object> fields) {
        if (!hasUpdatable(fields)) {
            return false;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(fields);
        for (String column : JSON_COLUMNS) {
            if (normalized.containsKey(column)) {
                normalized.put(column, toJsonColumnValue(normalized.get(column)));
            }
        }
        userManageMapper.updateProfile(id, normalized);
        return true;
    }

    /**
     * 原版 {@code user_updateProfile} 对 JSON 列的写法：{@code JSON.stringify(Array.isArray(v) ? v : [])}
     * —— 不是数组（含 null）一律落成 {@code []}，字符串再交给 MySQL 当 JSON 解析。
     */
    static String toJsonColumnValue(Object value) {
        try {
            return JSON.writeValueAsString(value instanceof List<?> list ? list : List.of());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    /**
     * 按 SELECT 列序重建一行，把缺失的列补成 {@code null}。
     *
     * <p>为什么需要：mysql2 会把值为 NULL 的列照样放进 JSON（{@code "name": null}），
     * 而 MyBatis 的 Map 结果默认<b>跳过</b> NULL 列——不补回来，Java 侧就会少键。
     * 共享的 application.yml 不让改，所以在这一层按列序补齐（键序也因此与原版一致）。
     */
    static LinkedHashMap<String, Object> withNullColumns(Map<String, Object> row, List<String> columns) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (String column : columns) {
            out.put(column, row.get(column));
        }
        return out;
    }

    /** 是否至少有一个白名单列出现在请求里（原版 user_updateProfile 返回 false 的条件）。 */
    static boolean hasUpdatable(Map<String, Object> fields) {
        if (fields == null) {
            return false;
        }
        for (String column : UserManageSqlProvider.UPDATABLE_COLUMNS) {
            if (fields.containsKey(column)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 原版 {@code user_getById} 的 parseJson：null / 非数组 / 解析失败都返回空数组，
     * 只有「数组」或「能解析成数组的字符串」才原样返回。
     */
    static List<Object> parseJsonArray(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof String text && !text.isEmpty()) {
            try {
                Object parsed = JSON.readValue(text, Object.class);
                if (parsed instanceof List<?> list) {
                    return new ArrayList<>(list);
                }
            } catch (Exception ignored) {
                // 与原版一致：解析失败当空数组
            }
        }
        return new ArrayList<>();
    }

    /** JS 的 Number(v)：空串是 0、布尔是 0/1、解析不出来是 NaN（这里用 null 表示）。批量删除的「剔除自己」也用它。 */
    public static Double jsNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1d : 0d;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 0d;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
