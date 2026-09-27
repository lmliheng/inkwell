package com.jscreator.social.controller;

import com.jscreator.common.api.Resp;
import com.jscreator.common.exception.BizException;
import com.jscreator.common.web.CurrentUser;
import com.jscreator.common.web.RequireLogin;
import com.jscreator.common.web.RoleLookup;
import com.jscreator.social.service.NotificationService;
import com.jscreator.social.util.Js;
import com.jscreator.social.util.NodeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /notification/*，逐字段对齐 Express 版 modules/notification/notification.controller.ts。
 *
 * <p>这是「平台广播通知」（notification / notification_read），与 /social/notifications*
 * 的互动通知（user_notification）是两套。管理端点（add/update/delete）的 403 文案是
 * 「权限不足，仅管理员可发布通知」，与 /social/admin/* 的「权限不足，仅管理员可操作」不同，
 * 所以这里不能用 @RequireAdmin，改成登录 + 查库判角色。
 */
@RestController
public class NotificationController {

    private static final String NOT_ADMIN = "权限不足，仅管理员可发布通知";

    private final NotificationService service;
    private final RoleLookup roleLookup;

    public NotificationController(NotificationService service, RoleLookup roleLookup) {
        this.service = service;
        this.roleLookup = roleLookup;
    }

    /** POST /notification/add（admin 发布） */
    @RequireLogin
    @PostMapping("/notification/add")
    public ResponseEntity<Map<String, Object>> add(@RequestBody(required = false) Object body,
                                                   HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> denied = adminOnly(request);
        if (denied != null) {
            return denied;
        }
        Long senderId = CurrentUser.id(request);
        Long notificationId = service.add(Js.asObjectMap(body), senderId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notification_id", notificationId);
        return ResponseEntity.ok(Resp.ok("通知发布成功", data));
    }

    /** GET /notification/list（当前用户可见列表，不分页） */
    @RequireLogin
    @GetMapping("/notification/list")
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request) {
        return ResponseEntity.ok(Resp.ok("获取通知列表成功", service.list(CurrentUser.id(request))));
    }

    /** GET /notification/unread-count */
    @RequireLogin
    @GetMapping("/notification/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("unread_count", service.unreadCount(CurrentUser.id(request)));
        return ResponseEntity.ok(Resp.ok("获取未读数成功", data));
    }

    /** POST /notification/read */
    @RequireLogin
    @PostMapping("/notification/read")
    public ResponseEntity<Map<String, Object>> read(@RequestBody(required = false) Object body,
                                                    HttpServletRequest request) {
        Object notificationId = Js.asObjectMap(body).get("notification_id");
        if (!Js.truthy(notificationId)) {
            throw BizException.badRequest("notification_id 不能为空");
        }
        service.markRead(notificationId, CurrentUser.id(request));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notification_id", notificationId);
        return ResponseEntity.ok(Resp.ok("标记已读成功", data));
    }

    /** PUT /notification/update（admin） */
    @RequireLogin
    @PutMapping("/notification/update")
    public ResponseEntity<Map<String, Object>> update(@RequestBody(required = false) Object body,
                                                      HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> denied = adminOnly(request);
        if (denied != null) {
            return denied;
        }
        Map<String, Object> in = Js.asObjectMap(body);
        Object notificationId = in.get("notification_id");
        if (!Js.truthy(notificationId)) {
            throw BizException.badRequest("notification_id 不能为空");
        }
        service.update(notificationId, in);
        return ResponseEntity.ok(Resp.ok("更新通知成功"));
    }

    /** DELETE /notification/delete（admin） */
    @RequireLogin
    @DeleteMapping("/notification/delete")
    public ResponseEntity<Map<String, Object>> remove(@RequestBody(required = false) Object body,
                                                      HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> denied = adminOnly(request);
        if (denied != null) {
            return denied;
        }
        Object notificationId = Js.asObjectMap(body).get("notification_id");
        if (!Js.truthy(notificationId)) {
            throw BizException.badRequest("notification_id 不能为空");
        }
        service.remove(notificationId);
        return ResponseEntity.ok(Resp.ok("删除通知成功"));
    }

    /** 原版 adminNotif：未登录 401（交给 @RequireLogin），非 role_id===1 → 403 + 专属文案。 */
    private ResponseEntity<Map<String, Object>> adminOnly(HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        Long role = userId == null ? null : roleLookup.roleIdOf(userId);
        if (role == null || role != 1L) {
            return NodeResponse.fail(403, NOT_ADMIN);
        }
        return null;
    }
}
