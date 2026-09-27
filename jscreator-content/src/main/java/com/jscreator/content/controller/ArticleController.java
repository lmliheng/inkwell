package com.jscreator.content.controller;

import com.jscreator.common.api.Resp;
import com.jscreator.common.exception.BizException;
import com.jscreator.common.security.TokenPayload;
import com.jscreator.common.web.CurrentUser;
import com.jscreator.common.web.RequireLogin;
import com.jscreator.content.service.ArticleService;
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
 * /article/* 的 HTTP 层，逐字段对齐 Express 版 modules/article/article.controller：
 * 成功 {@code {code:200,success:true,message,data?}}，失败是真实 HTTP 状态码 + {@code {code,success:false,message}}；
 * catch 分支的 500 文案（如「获取文章列表失败」）也照抄。
 *
 * <p>鉴权：list/archive/category/list 公开；detail 可选登录（未发布文章只有作者/admin 可见）；
 * 其余需要登录（原版挂 verifyToken）。
 */
@RestController
public class ArticleController {

    private static final Logger log = LoggerFactory.getLogger(ArticleController.class);

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    // ================= 公开 =================

    @GetMapping("/article/list")
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request) {
        try {
            Map<String, Object> data = articleService.list(
                    request.getParameter("page"),
                    request.getParameter("pageSize"),
                    request.getParameter("category_id"),
                    request.getParameter("keyword"),
                    request.getParameter("status"),
                    request.getParameter("author"));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("获取文章列表错误", e);
            return fail(500, "获取文章列表失败");
        }
    }

    @GetMapping("/article/archive")
    public ResponseEntity<Map<String, Object>> archive(HttpServletRequest request) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", articleService.archive(request.getParameter("username")));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("获取归档文章错误", e);
            return fail(500, "获取归档文章失败");
        }
    }

    @GetMapping("/article/category/list")
    public ResponseEntity<Map<String, Object>> categoryList() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", articleService.categoryList());
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("获取分类列表错误", e);
            return fail(500, "获取分类列表失败");
        }
    }

    @GetMapping("/article/detail/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable("id") String id, HttpServletRequest request) {
        Double articleId = ContentJs.number(id);
        if (articleId == null || articleId.isNaN() || articleId == 0) {
            return fail(400, "文章id不能为空");
        }
        try {
            TokenPayload viewer = CurrentUser.of(request);
            Map<String, Object> article = articleService.detailForViewer(articleId, viewer == null ? null : viewer.id());
            return ResponseEntity.ok(Resp.ok("获取成功", article));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("查询文章详情错误", e);
            return fail(500, "查询文章详情失败");
        }
    }

    // ================= 登录 =================

    @RequireLogin
    @PostMapping("/article/add")
    public ResponseEntity<Map<String, Object>> add(HttpServletRequest request,
                                                   @RequestBody(required = false) Object rawBody) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        Map<String, Object> body = ContentJs.objectMap(rawBody);
        String title = ContentJs.truthy(body.get("title")) ? ContentJs.str(body.get("title")) : "";
        String content = ContentJs.truthy(body.get("content")) ? ContentJs.str(body.get("content")) : "";
        if (title.isEmpty() || content.isEmpty()) {
            return fail(400, "标题和内容不能为空");
        }
        try {
            Map<String, Object> data = articleService.create(userId, title, content, body.get("category_ids"),
                    body.containsKey("status"), body.get("status"));
            return ResponseEntity.ok(Resp.ok("添加成功", data));
        } catch (Exception e) {
            log.error("添加文章错误", e);
            return fail(500, "添加文章失败");
        }
    }

    @RequireLogin
    @PutMapping("/article/update/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable("id") String id, HttpServletRequest request,
                                                      @RequestBody(required = false) Object rawBody) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        Double articleId = ContentJs.number(id);
        if (articleId == null || articleId.isNaN() || articleId == 0) {
            return fail(400, "文章id不能为空");
        }
        try {
            Map<String, Object> data = articleService.updateAs(userId, articleId, ContentJs.objectMap(rawBody));
            return ResponseEntity.ok(Resp.ok("更新成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("更新文章错误", e);
            return fail(500, "更新文章失败");
        }
    }

    @RequireLogin
    @DeleteMapping("/article/delete/{id}")
    public ResponseEntity<Map<String, Object>> remove(@PathVariable("id") String id, HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        Double articleId = ContentJs.number(id);
        if (articleId == null || articleId.isNaN() || articleId == 0) {
            return fail(400, "文章id不能为空");
        }
        try {
            Map<String, Object> data = articleService.removeAs(userId, articleId);
            return ResponseEntity.ok(Resp.ok("删除成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("删除文章错误", e);
            return fail(500, "删除文章失败");
        }
    }

    @RequireLogin
    @GetMapping("/article/mine")
    public ResponseEntity<Map<String, Object>> mine(HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        try {
            Map<String, Object> data = articleService.mine(userId,
                    request.getParameter("page"), request.getParameter("pageSize"));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("获取本人文章错误", e);
            return fail(500, "获取本人文章失败");
        }
    }

    @RequireLogin
    @PostMapping("/article/ai-summary/regenerate/{id}")
    public ResponseEntity<Map<String, Object>> regenerateSummary(@PathVariable("id") String id,
                                                                 HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        Double articleId = ContentJs.number(id);
        if (articleId == null || articleId.isNaN() || articleId == 0) {
            return fail(400, "文章id不能为空");
        }
        try {
            Object summary = articleService.regenerateAiSummary(userId, articleId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ai_summary", summary);
            return ResponseEntity.ok(Resp.ok("AI 总结已生成", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("重新生成 AI 总结错误", e);
            return fail(500, "操作失败");
        }
    }

    // ================= 分类写操作 =================

    @RequireLogin
    @PostMapping("/article/category/add")
    public ResponseEntity<Map<String, Object>> categoryAdd(HttpServletRequest request,
                                                           @RequestBody(required = false) Object rawBody) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        Map<String, Object> body = ContentJs.objectMap(rawBody);
        Object rawName = body.get("category_name");
        String categoryName = rawName == null ? "" : ContentJs.str(rawName);
        if (categoryName.isEmpty()) {
            return fail(400, "分类名称不能为空");
        }
        try {
            Map<String, Object> data = articleService.categoryAdd(userId, categoryName);
            return ResponseEntity.ok(Resp.ok("添加成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("添加分类错误", e);
            return fail(500, "添加分类失败");
        }
    }

    @RequireLogin
    @PutMapping("/article/category/update")
    public ResponseEntity<Map<String, Object>> categoryUpdate(HttpServletRequest request,
                                                              @RequestBody(required = false) Object rawBody) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        Map<String, Object> body = ContentJs.objectMap(rawBody);
        String categoryId = ContentJs.truthy(body.get("category_id")) ? ContentJs.str(body.get("category_id")) : "";
        String categoryName = ContentJs.truthy(body.get("category_name")) ? ContentJs.str(body.get("category_name")) : "";
        if (categoryId.isEmpty() || categoryName.isEmpty()) {
            return fail(400, "分类id和分类名称不能为空");
        }
        try {
            Map<String, Object> data = articleService.categoryUpdate(userId, categoryId, categoryName);
            return ResponseEntity.ok(Resp.ok("更新成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("更新分类错误", e);
            return fail(500, "更新分类失败");
        }
    }

    @RequireLogin
    @DeleteMapping("/article/category/delete")
    public ResponseEntity<Map<String, Object>> categoryDelete(HttpServletRequest request,
                                                              @RequestBody(required = false) Object rawBody) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        Map<String, Object> body = ContentJs.objectMap(rawBody);
        Object rawId = body.get("category_id");
        String categoryId = rawId == null ? "" : ContentJs.str(rawId);
        if (categoryId.isEmpty()) {
            return fail(400, "分类id不能为空");
        }
        try {
            Map<String, Object> data = articleService.categoryDelete(userId, categoryId);
            return ResponseEntity.ok(Resp.ok("删除成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("删除分类错误", e);
            return fail(500, "删除分类失败");
        }
    }

    static ResponseEntity<Map<String, Object>> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }
}
