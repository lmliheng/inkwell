package com.jscreator.auth.controller;

import com.jscreator.auth.service.OpenApiService;
import com.jscreator.auth.util.OauthOpenapiJs;
import com.jscreator.auth.util.OpenApiKeyGuard;
import com.jscreator.common.api.Resp;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.jscreator.auth.util.OauthOpenapiJs.number;
import static com.jscreator.auth.util.OauthOpenapiJs.str;
import static com.jscreator.auth.util.OauthOpenapiJs.truthy;

/**
 * /api/v1/*（外部开放接口），逐字段对齐 Express 版 modules/openapi/openapi.controller 的 OpenApiController：
 * 先 API Key 鉴权（{@link OpenApiKeyGuard}），再 scope 校验；失败文案照抄原版
 * （401 无效的 API Key / 403 该 API Key 无 xxx 权限）。
 */
@RestController
public class OpenApiController {

    private static final Logger log = LoggerFactory.getLogger(OpenApiController.class);

    private final OpenApiKeyGuard apiKeyGuard;
    private final OpenApiService openApiService;

    public OpenApiController(OpenApiKeyGuard apiKeyGuard, OpenApiService openApiService) {
        this.apiKeyGuard = apiKeyGuard;
        this.openApiService = openApiService;
    }

    @GetMapping("/api/v1/articles")
    public ResponseEntity<?> listArticles(HttpServletRequest request) {
        OpenApiKeyGuard.Verified apiUser = apiKeyGuard.verify(request);
        if (apiUser == null) {
            return fail(401, "无效的 API Key");
        }
        if (!OpenApiKeyGuard.hasScope(apiUser, "read")) {
            return fail(403, "该 API Key 无 read 权限");
        }
        try {
            Map<String, Object> data = openApiService.list(
                    request.getParameter("page"),
                    request.getParameter("pageSize"),
                    request.getParameter("keyword"),
                    request.getParameter("category_id"));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("API 文章列表错误", e);
            return fail(500, "获取失败");
        }
    }

    @GetMapping("/api/v1/articles/{id}")
    public ResponseEntity<?> detailArticle(@PathVariable("id") String idRaw, HttpServletRequest request) {
        OpenApiKeyGuard.Verified apiUser = apiKeyGuard.verify(request);
        if (apiUser == null) {
            return fail(401, "无效的 API Key");
        }
        if (!OpenApiKeyGuard.hasScope(apiUser, "read")) {
            return fail(403, "该 API Key 无 read 权限");
        }
        // 原版：Number(req.params.id)，NaN 或 0 都算空
        Double id = number(idRaw);
        if (id == null || id == 0) {
            return fail(400, "文章 id 不能为空");
        }
        try {
            LinkedHashMap<String, Object> data = openApiService.detail(id.longValue());
            if (data == null) {
                return fail(404, "文章不存在或未发布");
            }
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("API 文章详情错误", e);
            return fail(500, "获取失败");
        }
    }

    @GetMapping("/api/v1/users/{username}")
    public ResponseEntity<?> getUser(@PathVariable("username") String username, HttpServletRequest request) {
        OpenApiKeyGuard.Verified apiUser = apiKeyGuard.verify(request);
        if (apiUser == null) {
            return fail(401, "无效的 API Key");
        }
        if (!OpenApiKeyGuard.hasScope(apiUser, "read")) {
            return fail(403, "该 API Key 无 read 权限");
        }
        try {
            LinkedHashMap<String, Object> data = openApiService.user(str(username));
            if (data == null) {
                return fail(404, "用户不存在");
            }
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("API 用户查询错误", e);
            return fail(500, "获取失败");
        }
    }

    @PostMapping("/api/v1/articles")
    public ResponseEntity<?> publishArticle(HttpServletRequest request,
                                            @RequestBody(required = false) Object body) {
        OpenApiKeyGuard.Verified apiUser = apiKeyGuard.verify(request);
        if (apiUser == null) {
            return fail(401, "无效的 API Key");
        }
        if (!OpenApiKeyGuard.hasScope(apiUser, "write")) {
            return fail(403, "该 API Key 无 write 权限");
        }
        Map<String, Object> in = OauthOpenapiJs.asObjectMap(body);
        String title = truthy(in.get("title")) ? str(in.get("title")) : "";
        String content = truthy(in.get("content")) ? str(in.get("content")) : "";
        if (title.isEmpty() || content.isEmpty()) {
            return fail(400, "标题和内容不能为空");
        }
        try {
            Map<String, Object> data = openApiService.publish(
                    apiUser.userId(), title, content, in.get("category_ids"), in.get("status"),
                    in.containsKey("status"));
            return ResponseEntity.ok(Resp.ok("发布成功", data));
        } catch (Exception e) {
            log.error("API 发布文章错误", e);
            return fail(500, "发布失败");
        }
    }

    private static ResponseEntity<?> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }
}
