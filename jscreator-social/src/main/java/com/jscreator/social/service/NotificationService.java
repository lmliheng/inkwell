package com.jscreator.social.service;

import com.jscreator.common.exception.BizException;
import com.jscreator.social.mapper.NotificationMapper;
import com.jscreator.social.util.Js;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 平台广播通知业务，逐条对齐 Express 版 modules/notification/notification.controller.ts + notification.dao.ts。
 *
 * <p>与 /social/notifications*（user_notification 互动通知）是两套表、两套路由，不要混。
 *
 * <p>校验失败一律抛 {@link BizException}（= 原版的 {@code fail(res, 400, ...)}）。
 */
@Service
public class NotificationService {

    private static final Set<String> VALID_TARGET_TYPES = Set.of("all", "user", "role");
    private static final Set<String> VALID_NOTIF_TYPES = Set.of("system", "announcement", "reminder");
    private static final Set<String> VALID_IMPORTANCE = Set.of("high", "medium", "low");

    /** 原版 update 的白名单，同时也是建表列名（键名与列名一致）。 */
    private static final List<String> UPDATABLE = List.of(
            "title", "content", "target_type", "target_id", "type", "importance");

    private final NotificationMapper mapper;

    public NotificationService(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    /** POST /notification/add（admin 发布），返回新插入的 notification_id。 */
    public Long add(Map<String, Object> body, Object senderId) {
        String title = Js.truthy(body.get("title")) ? Js.str(body.get("title")) : "";
        String content = Js.truthy(body.get("content")) ? Js.str(body.get("content")) : "";
        if (title.isEmpty() || content.isEmpty()) {
            throw BizException.badRequest("标题和内容不能为空");
        }
        String targetType = Js.truthy(body.get("target_type")) ? Js.str(body.get("target_type")) : "";
        if (!VALID_TARGET_TYPES.contains(targetType)) {
            throw BizException.badRequest("target_type 必须为 'all' | 'user' | 'role'");
        }
        if (!"all".equals(targetType) && !Js.truthy(body.get("target_id"))) {
            throw BizException.badRequest("target_type 为 'user' 或 'role' 时必须提供 target_id");
        }
        String type = VALID_NOTIF_TYPES.contains(Js.str(body.get("type"))) ? Js.str(body.get("type")) : "announcement";
        String importance = VALID_IMPORTANCE.contains(Js.str(body.get("importance")))
                ? Js.str(body.get("importance")) : "medium";

        NotificationMapper.BroadcastRow row = new NotificationMapper.BroadcastRow();
        row.setTitle(title);
        row.setContent(content);
        row.setSenderId(senderId);
        row.setTargetType(targetType);
        row.setTargetId(body.get("target_id") != null ? Js.str(body.get("target_id")) : null);
        row.setType(type);
        row.setImportance(importance);
        mapper.notificationAdd(row);
        return row.getId();
    }

    /** GET /notification/list（当前用户可见列表，原版不分页） */
    public Map<String, Object> list(Object userId) {
        List<LinkedHashMap<String, Object>> rows = mapper.notificationForUser(userId);
        for (Map<String, Object> row : rows) {
            row.put("is_read", Js.tinyInt(row.get("is_read")));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("list", rows);
        body.put("total", rows.size());
        return body;
    }

    public Long unreadCount(Object userId) {
        return mapper.notificationUnreadCount(userId);
    }

    public void markRead(Object notificationId, Object userId) {
        mapper.notificationMarkRead(notificationId, userId);
    }

    /**
     * PUT /notification/update：按「请求体里出现过该键」拼 SET 片段（值显式为 null 也照写，
     * 于是 title=null 会撞 NOT NULL 约束 → 500，与原版一致）。没有可更新字段时 400。
     */
    public void update(Object notificationId, Map<String, Object> body) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String key : UPDATABLE) {
            if (!body.containsKey(key)) {
                continue;
            }
            fields.put(key, body.get(key));
            clauses.add(key + " = #{fields." + key + "}");
        }
        if (clauses.isEmpty()) {
            throw BizException.badRequest("没有需要更新的字段");
        }
        mapper.notificationUpdate(clauses, fields, notificationId);
    }

    public void remove(Object notificationId) {
        mapper.notificationRemove(notificationId);
    }
}
