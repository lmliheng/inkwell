package com.jscreator.system.monitor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口调用统计（内存态，进程重启清零），逐字段对齐 Express 版 legacy-utils/api-monitor：
 *
 * <ul>
 *   <li>启动时把已注册路由登记进表，未调用的条目 count=0、avgTime=null（原版 {@code Math.round(0/0)} 得到 NaN，
 *       JSON 序列化后就是 null）、lastAt=null</li>
 *   <li>每次请求结束时累加 count / totalTime，HTTP 状态码 ≥ 400 计入 errorCount</li>
 *   <li>key 是 {@code "METHOD 路径"}，路径是**实际请求路径**（含路径参数的实值），与 Express 的 {@code req.path} 一致</li>
 *   <li>返回按 count 降序排列（原版 {@code list.sort((a, b) => b.count - a.count)}）</li>
 * </ul>
 *
 * 与原版的差异：原版是单体进程，统计覆盖全部 111 个接口；这里统计的是 system 服务自身收到的请求，
 * 接口形状、字段与计算规则完全一致。详见 README「已知的、刻意的差异」。
 */
@Component
public class ApiMonitor {

    /** 可变的统计行。读改写都在 synchronized(entry) 里做，配合 ConcurrentHashMap 保证线程安全。 */
    static final class Entry {
        long count;
        long totalTime;
        long errorCount;
        Long lastAt;
    }

    private final Map<String, Entry> stats = new ConcurrentHashMap<>();

    /** 请求结束时调用。 */
    public void record(String method, String path, int statusCode, long elapsedMillis) {
        Entry entry = stats.computeIfAbsent(method + " " + path, k -> new Entry());
        synchronized (entry) {
            entry.count += 1;
            entry.totalTime += elapsedMillis;
            if (statusCode >= 400) {
                entry.errorCount += 1;
            }
            entry.lastAt = System.currentTimeMillis();
        }
    }

    /** 启动时登记路由：已存在（已被调用过）就不覆盖。 */
    public void register(String method, String path) {
        stats.putIfAbsent(method + " " + path, new Entry());
    }

    /** 统计列表，按 count 降序。 */
    public List<Map<String, Object>> list() {
        List<Map.Entry<String, Entry>> entries = new ArrayList<>(stats.entrySet());
        // 稳定排序，count 相同时保持登记/首次调用的先后顺序，避免同一次运行里列表自己抖动
        entries.sort((a, b) -> Long.compare(b.getValue().count, a.getValue().count));

        List<Map<String, Object>> out = new ArrayList<>(entries.size());
        for (Map.Entry<String, Entry> e : entries) {
            Entry v = e.getValue();
            long count;
            long totalTime;
            long errorCount;
            Long lastAt;
            synchronized (v) {
                count = v.count;
                totalTime = v.totalTime;
                errorCount = v.errorCount;
                lastAt = v.lastAt;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", e.getKey());
            row.put("count", count);
            // count=0 时原版得到 NaN，JSON 里是 null
            row.put("avgTime", count == 0 ? null : Math.round((double) totalTime / count));
            row.put("errorCount", errorCount);
            row.put("lastAt", lastAt);
            out.add(row);
        }
        return out;
    }
}
