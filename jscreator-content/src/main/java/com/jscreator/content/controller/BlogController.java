package com.jscreator.content.controller;

import com.jscreator.common.api.Resp;
import com.jscreator.common.exception.BizException;
import com.jscreator.content.service.BlogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /blog/* 的 HTTP 层（公开读），逐字段对齐 Express 版 modules/blog/blogProfile.controller。
 */
@RestController
public class BlogController {

    private static final Logger log = LoggerFactory.getLogger(BlogController.class);

    private final BlogService blogService;

    public BlogController(BlogService blogService) {
        this.blogService = blogService;
    }

    @GetMapping("/blog/users")
    public ResponseEntity<Map<String, Object>> users(HttpServletRequest request) {
        try {
            Map<String, Object> data = blogService.users(
                    request.getParameter("page"), request.getParameter("pageSize"));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("获取用户列表错误", e);
            return fail(500, "获取用户列表失败");
        }
    }

    @GetMapping("/blog/feed")
    public ResponseEntity<Map<String, Object>> feed(HttpServletRequest request) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", blogService.latest(request.getParameter("limit")));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("获取全站最新文章错误", e);
            return fail(500, "获取最新文章失败");
        }
    }

    @GetMapping("/blog/hot")
    public ResponseEntity<Map<String, Object>> hot(HttpServletRequest request) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", blogService.hot(request.getParameter("limit")));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("获取热议文章错误", e);
            return fail(500, "获取热门文章失败");
        }
    }

    @GetMapping("/blog/profile/{username}")
    public ResponseEntity<Map<String, Object>> profile(@PathVariable("username") String username,
                                                       HttpServletRequest request) {
        try {
            Map<String, Object> data = blogService.profilePage(username,
                    request.getParameter("page"), request.getParameter("pageSize"));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取博客主页错误", e);
            return fail(500, "获取博客主页失败");
        }
    }

    @GetMapping("/blog/articles/{username}")
    public ResponseEntity<Map<String, Object>> articles(@PathVariable("username") String username,
                                                        HttpServletRequest request) {
        try {
            Map<String, Object> data = blogService.userArticles(username,
                    request.getParameter("page"), request.getParameter("pageSize"),
                    request.getParameter("keyword"), request.getParameter("category_id"),
                    request.getParameter("sort"));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取用户文章列表错误", e);
            return fail(500, "获取用户文章列表失败");
        }
    }

    static ResponseEntity<Map<String, Object>> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }
}
