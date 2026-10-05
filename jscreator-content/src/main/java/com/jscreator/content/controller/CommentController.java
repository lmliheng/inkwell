package com.jscreator.content.controller;

import com.jscreator.common.api.Resp;
import com.jscreator.common.exception.BizException;
import com.jscreator.common.security.TokenPayload;
import com.jscreator.common.web.CurrentUser;
import com.jscreator.common.web.RequireAdmin;
import com.jscreator.content.service.CommentService;
import com.jscreator.content.util.ContentJs;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /comment/* 的 HTTP 层，逐字段对齐 Express 版 modules/comment/comment.controller：
 * 列表/发表公开（发表允许匿名），管理端三个接口是 [verifyToken, adminOnly]。
 */
@RestController
public class CommentController {

    private static final Logger log = LoggerFactory.getLogger(CommentController.class);

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/comment/list/{articleId}")
    public ResponseEntity<Map<String, Object>> list(@PathVariable("articleId") String articleId,
                                                    HttpServletRequest request) {
        Double id = ContentJs.number(articleId);
        if (id == null || id.isNaN() || id == 0) {
            return fail(400, "文章id不能为空");
        }
        try {
            Map<String, Object> data = commentService.listByArticle(id,
                    request.getParameter("page"), request.getParameter("pageSize"));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("获取评论列表错误", e);
            return fail(500, "获取评论列表失败");
        }
    }

    @PostMapping("/comment/add")
    public ResponseEntity<Map<String, Object>> add(HttpServletRequest request,
                                                   @RequestBody(required = false) Object rawBody) {
        Map<String, Object> body = ContentJs.objectMap(rawBody);
        try {
            TokenPayload viewer = CurrentUser.of(request);
            Map<String, Object> data = commentService.create(
                    body.get("article_id"),
                    body.get("content"),
                    body.get("parent_id"),
                    body.get("nickname"),
                    viewer == null ? null : viewer.id());
            return ResponseEntity.ok(Resp.ok("评论成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("发表评论错误", e);
            return fail(500, "发表评论失败");
        }
    }

    @RequireAdmin
    @GetMapping("/comment/manage/list")
    public ResponseEntity<Map<String, Object>> manageList(HttpServletRequest request) {
        try {
            Map<String, Object> data = commentService.manageList(
                    request.getParameter("page"), request.getParameter("pageSize"),
                    request.getParameter("article_id"), request.getParameter("keyword"));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("评论管理-获取列表错误", e);
            return fail(500, "获取评论列表失败");
        }
    }

    @RequireAdmin
    @PutMapping("/comment/manage/update")
    public ResponseEntity<Map<String, Object>> manageUpdate(@RequestBody(required = false) Object rawBody) {
        Map<String, Object> body = ContentJs.objectMap(rawBody);
        try {
            commentService.manageUpdate(body.get("comment_id"), body);
            return ResponseEntity.ok(Resp.ok("更新成功"));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("评论管理-更新错误", e);
            return fail(500, "更新失败");
        }
    }

    @RequireAdmin
    @DeleteMapping("/comment/manage/delete")
    public ResponseEntity<Map<String, Object>> manageDelete(@RequestBody(required = false) Object rawBody) {
        Map<String, Object> body = ContentJs.objectMap(rawBody);
        try {
            long deleted = commentService.manageDeleteCascade(body.get("comment_ids"));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("deleted", deleted);
            return ResponseEntity.ok(Resp.ok("删除成功（" + deleted + " 条）", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("评论管理-删除错误", e);
            return fail(500, "删除失败");
        }
    }

    static ResponseEntity<Map<String, Object>> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }
}
