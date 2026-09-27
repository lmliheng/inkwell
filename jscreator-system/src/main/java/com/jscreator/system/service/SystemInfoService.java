package com.jscreator.system.service;

import com.jscreator.system.monitor.ApiMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 系统监控数据采集，逐字段对齐 Express 版 modules/systemmon/systemmon.controller：
 * 返回 {@code { system, cpu, memory, process, db, timestamp }} 六块，键名与嵌套结构照抄。
 *
 * <p>几处无法与原版取同一来源的字段（Node 与 JVM 的世界不同），键名保持不变、只换数据来源，
 * 详见 README「已知的、刻意的差异」：
 * <ul>
 *   <li>{@code process.nodeVersion} → JVM 版本（{@code java.version}）</li>
 *   <li>{@code system.arch} → {@code os.arch}（amd64，原版是 Node 的 x64）</li>
 *   <li>{@code cpu.usage} → 同样按「两次 /proc/stat 采样求差」计算，首次返回 null</li>
 * </ul>
 */
@Service
public class SystemInfoService {

    private static final Logger log = LoggerFactory.getLogger(SystemInfoService.class);

    /** Node 的 new Date().toISOString()：UTC、毫秒三位、以 Z 结尾。 */
    private static final DateTimeFormatter ISO_MS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;
    private final ApiMonitor apiMonitor;

    /** CPU 采样缓存：首次返回 null，第二次起返回两次采样之间的使用率（与原版一致）。 */
    private CpuSample lastCpuSample;

    public SystemInfoService(JdbcTemplate jdbc, ApiMonitor apiMonitor) {
        this.jdbc = jdbc;
        this.apiMonitor = apiMonitor;
    }

    // ================= /system-monitor =================

    public Map<String, Object> monitor() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("system", systemInfo());
        data.put("cpu", cpuInfo());
        data.put("memory", memoryInfo());
        data.put("process", processInfo());
        data.put("db", dbInfo());
        data.put("timestamp", ISO_MS.format(Instant.now()));
        return data;
    }

    private Map<String, Object> systemInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        // Node 的 os.hostname() 取自 uname；容器里读 HOSTNAME 更稳（InetAddress 在无 DNS 环境下会卡）
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isBlank()) {
            hostname = readFirstLine("/proc/sys/kernel/hostname", "unknown");
        }
        String osName = System.getProperty("os.name", "");
        m.put("hostname", hostname);
        m.put("platform", osName.toLowerCase(Locale.ROOT));   // Node: 'linux'
        m.put("arch", System.getProperty("os.arch", ""));      // Node: 'x64'，JVM 为 'amd64'
        m.put("osType", osName);                               // Node: os.type() = 'Linux'
        m.put("osRelease", System.getProperty("os.version", "")); // Node: os.release() = 内核版本
        m.put("uptime", uptimeSeconds());
        m.put("cpuModel", cpuModel());
        m.put("cpuCores", Runtime.getRuntime().availableProcessors());
        return m;
    }

    private Map<String, Object> cpuInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usage", cpuUsagePercent());
        m.put("loadavg", loadAverage());
        return m;
    }

    /**
     * 内存取自 {@code /proc/meminfo}，与 Node 的 os.totalmem()/os.freemem() 同源。
     *
     * <p>为什么不用 {@code OperatingSystemMXBean.getTotalMemorySize()}：JDK 会按 cgroup 限制读，
     * 服务跑在容器里时拿到的是**容器配额**（例如 380M）而不是服务器内存，监控页就失真了。
     * 容器的 {@code /proc/meminfo} 默认就是宿主机的视图，读它才与原版一致。
     */
    private Map<String, Object> memoryInfo() {
        long total;
        long free;
        long[] fromProc = readMemInfo();
        if (fromProc != null) {
            total = fromProc[0];
            free = fromProc[1];
        } else {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            total = os.getTotalMemorySize();
            free = os.getFreeMemorySize();
        }
        long used = total - free;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("free", free);
        m.put("used", used);
        m.put("usagePercent", round1(total == 0 ? 0d : (used * 100d) / total));
        return m;
    }

    /** /proc/meminfo 的 MemTotal / MemFree（kB → 字节），对应 Node 的 os.totalmem()/os.freemem()。 */
    private static long[] readMemInfo() {
        Long total = null;
        Long free = null;
        for (String line : readLines("/proc/meminfo")) {
            if (line.startsWith("MemTotal:")) {
                total = kbToBytes(line);
            } else if (line.startsWith("MemFree:")) {
                free = kbToBytes(line);
            }
            if (total != null && free != null) {
                return new long[]{total, free};
            }
        }
        return null;
    }

    private static Long kbToBytes(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        try {
            return Long.parseLong(parts[1]) * 1024L;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> processInfo() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pid", ProcessHandle.current().pid());
        m.put("nodeVersion", System.getProperty("java.version", "")); // 键名照抄，值是 JVM 版本
        m.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime() / 1000d);
        m.put("memoryRss", residentSetSize());
        m.put("heapUsed", usedHeap());
        m.put("heapTotal", maxHeap());
        return m;
    }

    private Map<String, Object> dbInfo() {
        boolean connected;
        String version = null;
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            connected = true;
        } catch (Exception e) {
            connected = false;
        }
        if (connected) {
            try {
                version = jdbc.queryForObject("SELECT VERSION()", String.class);
            } catch (Exception e) {
                version = null;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("connected", connected);
        m.put("version", version);
        return m;
    }

    // ================= /system-monitor/api-stats =================

    public List<Map<String, Object>> apiStats() {
        return apiMonitor.list();
    }

    // ================= 采集细节 =================

    private static final class CpuSample {
        final long totalAll;
        final long idleAll;
        final long ts;

        CpuSample(long totalAll, long idleAll, long ts) {
            this.totalAll = totalAll;
            this.idleAll = idleAll;
            this.ts = ts;
        }
    }

    /**
     * /proc/stat 第一行：{@code cpu user nice system idle iowait irq softirq ...}。
     * 与原版 os.cpus() 的 times 一样只累加 user/nice/sys/idle/irq，不计 iowait 与软中断。
     */
    private synchronized Double cpuUsagePercent() {
        long[] sample = readCpuTimes();
        if (sample == null) {
            return null;
        }
        long totalAll = sample[0];
        long idleAll = sample[1];
        long now = System.currentTimeMillis();

        if (lastCpuSample == null) {
            lastCpuSample = new CpuSample(totalAll, idleAll, now);
            return null;
        }
        long dTotal = totalAll - lastCpuSample.totalAll;
        long dIdle = idleAll - lastCpuSample.idleAll;
        long dt = now - lastCpuSample.ts;
        lastCpuSample = new CpuSample(totalAll, idleAll, now);

        if (dTotal <= 0 || dt <= 0) {
            return null;
        }
        double usage = (1 - (double) dIdle / dTotal) * 100;
        return round1(Math.max(0, Math.min(100, usage)));
    }

    /** 返回 {total, idle}，读不到返回 null。 */
    private static long[] readCpuTimes() {
        List<String> lines = readLines("/proc/stat");
        for (String line : lines) {
            if (!line.startsWith("cpu ")) {
                continue;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 8) {
                return null;
            }
            try {
                long user = Long.parseLong(parts[1]);
                long nice = Long.parseLong(parts[2]);
                long sys = Long.parseLong(parts[3]);
                long idle = Long.parseLong(parts[4]);
                long irq = Long.parseLong(parts[6]);
                return new long[]{user + nice + sys + idle + irq, idle};
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** /proc/loadavg 前三项，对应 Node 的 os.loadavg()。 */
    private static List<Double> loadAverage() {
        String line = readFirstLine("/proc/loadavg", null);
        if (line == null) {
            return List.of(0d, 0d, 0d);
        }
        String[] parts = line.split("\\s+");
        List<Double> out = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            try {
                out.add(Double.parseDouble(parts[i]));
            } catch (Exception e) {
                out.add(0d);
            }
        }
        return out;
    }

    /** Node 的 os.uptime()：系统启动至今的秒数（带小数的浮点数）。 */
    private static Double uptimeSeconds() {
        String line = readFirstLine("/proc/uptime", null);
        if (line == null) {
            return 0d;
        }
        String[] parts = line.split("\\s+");
        try {
            return Double.parseDouble(parts[0]);
        } catch (Exception e) {
            return 0d;
        }
    }

    /** /proc/cpuinfo 的 model name，对应 Node 的 os.cpus()[0].model。 */
    private static String cpuModel() {
        for (String line : readLines("/proc/cpuinfo")) {
            if (line.startsWith("model name")) {
                int idx = line.indexOf(':');
                if (idx >= 0) {
                    return line.substring(idx + 1).trim();
                }
            }
        }
        return "";
    }

    /** 进程常驻内存（字节），对应 Node 的 process.memoryUsage().rss。 */
    private static long residentSetSize() {
        for (String line : readLines("/proc/self/status")) {
            if (line.startsWith("VmRSS:")) {
                String[] parts = line.trim().split("\\s+");
                try {
                    return Long.parseLong(parts[1]) * 1024L;
                } catch (Exception e) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private static long usedHeap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static long maxHeap() {
        long max = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
        return max > 0 ? max : ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted();
    }

    /** 保留一位小数，对齐原版的 Number(x.toFixed(1))。 */
    static double round1(double v) {
        return Math.round(v * 10d) / 10d;
    }

    private static List<String> readLines(String path) {
        try {
            return Files.readAllLines(Path.of(path));
        } catch (IOException e) {
            log.debug("读不到 {}：{}", path, e.getMessage());
            return List.of();
        }
    }

    /** 读文件首行（trim 后），失败返回 fallback。 */
    private static String readFirstLine(String path, String fallback) {
        for (String line : readLines(path)) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return fallback;
    }
}
