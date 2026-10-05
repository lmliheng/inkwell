package com.jscreator.social.service;

import com.jscreator.social.mapper.DmMapper;
import com.jscreator.social.util.Js;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 私信 REST 业务，逐条对齐 Express 版 modules/dm/dm.service.ts + dm.dao.ts。
 *
 * <p>注意 dm 的分页没有 social 那套「小于 1 回落」的兜底：page=-1 会算出负 offset 直接让 SQL 报错，
 * 原版如此（500「获取失败」），这里照抄。
 */
@Service
public class DmService {

    private final DmMapper mapper;

    public DmService(DmMapper mapper) {
        this.mapper = mapper;
    }

    public SResult conversations(Object userId) {
        List<LinkedHashMap<String, Object>> list = mapper.conversationList(userId);
        return SResult.dataOnly(data("list", list));
    }

    /** 会话消息：原版倒序取一页后 reverse 成时间正序。 */
    public SResult messages(Object userId, Object otherId, Object page, Object pageSize) {
        Double other = Js.number(otherId);
        if (other == null) {
            // 原版把 Number('abc') = NaN 原样塞进 SQL，MySQL 报错 → 500「获取失败」；照抄这条路径
            throw new IllegalArgumentException("otherId 不是数字：" + otherId);
        }
        int p = Js.parseIntOrDefault(page, 1);
        int ps = Js.parseIntOrDefault(pageSize, 30);
        int offset = (p - 1) * ps;
        List<LinkedHashMap<String, Object>> list = mapper.conversation(userId, jsonNumber(other), ps, offset);
        for (Map<String, Object> row : list) {
            row.put("is_read", Js.tinyInt(row.get("is_read")));
        }
        Collections.reverse(list);
        return SResult.dataOnly(data("list", list));
    }

    public SResult unreadCount(Object userId) {
        return SResult.dataOnly(data("count", mapper.unreadTotal(userId)));
    }

    /** POST /dm/read（原版的判定是 {@code if (!other_id)}） */
    public SResult markRead(Object userId, Map<String, Object> body) {
        Object otherId = body.get("other_id");
        if (!Js.truthy(otherId)) {
            return SResult.fail(400, "参数缺失");
        }
        Double other = Js.number(otherId);
        if (other == null) {
            // 同上：NaN 进 SQL 直接报错 → 500「操作失败」
            throw new IllegalArgumentException("other_id 不是数字：" + otherId);
        }
        mapper.markRead(userId, jsonNumber(other));
        return SResult.ok("ok");
    }

    private static Object jsonNumber(Double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e21) {
            return d.longValue();
        }
        return d;
    }

    private static Map<String, Object> data(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }
}
