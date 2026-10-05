package com.jscreator.social.controller;

import com.jscreator.common.web.CurrentUser;
import com.jscreator.common.web.RequireAdmin;
import com.jscreator.common.web.RequireLogin;
import com.jscreator.social.service.SResult;
import com.jscreator.social.service.SocialService;
import com.jscreator.social.util.Js;
import com.jscreator.social.util.NodeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * /social/*，逐字段对齐 Express 版 modules/social/social.controller.ts：
 * 每个端点的 500 文案、成功文案、data 结构全部照抄。
 *
 * <p>鉴权：following / followers / stats 是公开的（stats 可选登录，控制器内自行解析 token），
 * 其余需登录，/social/admin/* 需管理员。
 */
@RestController
public class SocialController {

    private static final Logger log = LoggerFactory.getLogger(SocialController.class);

    private final SocialService service;

    public SocialController(SocialService service) {
        this.service = service;
    }

    // ================= 公开 =================

    @GetMapping("/social/following/{username}")
    public ResponseEntity<Map<String, Object>> following(@PathVariable("username") String username) {
        return wrap("获取失败", () -> service.followingByUsername(username));
    }

    @GetMapping("/social/followers/{username}")
    public ResponseEntity<Map<String, Object>> followers(@PathVariable("username") String username) {
        return wrap("获取失败", () -> service.followersByUsername(username));
    }

    @GetMapping("/social/stats/{username}")
    public ResponseEntity<Map<String, Object>> stats(@PathVariable("username") String username,
                                                     HttpServletRequest request) {
        Long viewer = CurrentUser.id(request);
        return wrap("获取失败", () -> service.statsByUsername(username, viewer));
    }

    // ================= 需登录 =================

    @RequireLogin
    @PostMapping("/social/follow/{username}")
    public ResponseEntity<Map<String, Object>> follow(@PathVariable("username") String username,
                                                      HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        return wrap("操作失败", () -> service.toggleFollow(actor, username));
    }

    @RequireLogin
    @PostMapping("/social/like/{articleId}")
    public ResponseEntity<Map<String, Object>> like(@PathVariable("articleId") String articleId,
                                                    HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        return wrap("操作失败", () -> service.toggleLike(actor, articleId));
    }

    @RequireLogin
    @GetMapping("/social/status")
    public ResponseEntity<Map<String, Object>> status(
            @RequestParam(name = "ids", required = false) List<String> ids, HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        return wrap("查询失败", () -> service.statusBatch(actor, queryParam(ids)));
    }

    @RequireLogin
    @PostMapping("/social/favorite/{articleId}")
    public ResponseEntity<Map<String, Object>> favorite(@PathVariable("articleId") String articleId,
                                                        HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        return wrap("操作失败", () -> service.toggleFavorite(actor, articleId));
    }

    @RequireLogin
    @GetMapping("/social/my-favorites")
    public ResponseEntity<Map<String, Object>> myFavorites(HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        return wrap("获取失败", () -> service.myFavorites(actor));
    }

    @RequireLogin
    @GetMapping("/social/notifications")
    public ResponseEntity<Map<String, Object>> notifications(
            @RequestParam(name = "page", required = false) List<String> page,
            @RequestParam(name = "pageSize", required = false) List<String> pageSize, HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        return wrap("获取失败", () -> service.notificationPage(actor, queryParam(page), queryParam(pageSize)));
    }

    @RequireLogin
    @GetMapping("/social/notifications/unread-count")
    public ResponseEntity<Map<String, Object>> notificationsUnread(HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        return wrap("获取失败", () -> service.notificationUnread(actor));
    }

    @RequireLogin
    @PostMapping("/social/notifications/read")
    public ResponseEntity<Map<String, Object>> notificationsRead(
            @RequestBody(required = false) Object body, HttpServletRequest request) {
        Long actor = CurrentUser.id(request);
        Map<String, Object> in = Js.asObjectMap(body);
        return wrap("操作失败", () -> service.notificationMarkRead(actor, in));
    }

    // ================= 管理端 =================

    @RequireAdmin
    @GetMapping("/social/admin/likes")
    public ResponseEntity<Map<String, Object>> adminLikes(
            @RequestParam(name = "page", required = false) List<String> page,
            @RequestParam(name = "pageSize", required = false) List<String> pageSize,
            @RequestParam(name = "keyword", required = false) List<String> keyword) {
        return wrap("获取失败", () -> service.adminLikes(queryParam(page), queryParam(pageSize), queryParam(keyword)));
    }

    @RequireAdmin
    @DeleteMapping("/social/admin/likes/{id}")
    public ResponseEntity<Map<String, Object>> adminLikeDelete(@PathVariable("id") String id) {
        return wrap("删除失败", () -> service.adminLikeDelete(id));
    }

    @RequireAdmin
    @GetMapping("/social/admin/favorites")
    public ResponseEntity<Map<String, Object>> adminFavorites(
            @RequestParam(name = "page", required = false) List<String> page,
            @RequestParam(name = "pageSize", required = false) List<String> pageSize,
            @RequestParam(name = "keyword", required = false) List<String> keyword) {
        return wrap("获取失败", () -> service.adminFavorites(queryParam(page), queryParam(pageSize), queryParam(keyword)));
    }

    @RequireAdmin
    @DeleteMapping("/social/admin/favorites/{id}")
    public ResponseEntity<Map<String, Object>> adminFavoriteDelete(@PathVariable("id") String id) {
        return wrap("删除失败", () -> service.adminFavoriteDelete(id));
    }

    // ================= 内部工具 =================

    /** 原版的 try/catch：service 抛异常 → 500 + 该端点固定的文案。 */
    private ResponseEntity<Map<String, Object>> wrap(String errorMessage, Supplier<SResult> call) {
        try {
            return NodeResponse.render(call.get());
        } catch (Exception e) {
            log.error("social 操作错误：{}", errorMessage, e);
            return NodeResponse.fail(500, errorMessage);
        }
    }

    /**
     * Express 的 {@code req.query.x}：单个值给字符串，重复出现给数组（{@code String(v)} 时用逗号拼）。
     */
    static Object queryParam(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.size() == 1 ? values.get(0) : values;
    }
}
