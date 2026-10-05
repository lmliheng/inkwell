package com.jscreator.auth.controller;

import com.jscreator.auth.service.ApiKeyService;
import com.jscreator.auth.util.OauthOpenapiJs;
import com.jscreator.common.api.Resp;
import com.jscreator.common.web.CurrentUser;
import com.jscreator.common.web.RequireLogin;
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

import static com.jscreator.auth.util.OauthOpenapiJs.str;
import static com.jscreator.auth.util.OauthOpenapiJs.truthy;

/**
 * /api-keys*，逐字段对齐 Express 版 modules/openapi/openapi.controller 的 ApiKeyController：
 * 都是登录接口（{@code verifyToken}），只能管理自己的 key（SQL 里带 user_id 条件）。
 */
@RestController
public class ApiKeyController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyController.class);

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @RequireLogin
    @GetMapping("/api-keys")
    public ResponseEntity<?> list(HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", apiKeyService.list(userId));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("获取 API key 列表错误", e);
            return fail(500, "获取失败");
        }
    }

    @RequireLogin
    @PostMapping("/api-keys")
    public ResponseEntity<?> create(HttpServletRequest request, @RequestBody(required = false) Object body) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        Map<String, Object> in = OauthOpenapiJs.asObjectMap(body);
        String name = truthy(in.get("name")) ? str(in.get("name")) : null;
        try {
            ApiKeyService.CreateResult out = apiKeyService.create(userId, name, in.get("scopes"));
            if (!out.ok()) {
                return fail(out.status(), out.message());
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("plain", out.plain());
            data.put("prefix", out.prefix());
            data.put("scopes", out.scopes());
            return ResponseEntity.ok(Resp.ok("创建成功（明文只显示这一次，请妥善保存）", data));
        } catch (Exception e) {
            log.error("创建 API key 错误", e);
            return fail(500, "创建失败");
        }
    }

    @RequireLogin
    @PutMapping("/api-keys/{id}/status")
    public ResponseEntity<?> setStatus(@PathVariable("id") String id, HttpServletRequest request,
                                       @RequestBody(required = false) Object body) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        Object status = OauthOpenapiJs.asObjectMap(body).get("status");
        try {
            apiKeyService.setStatus(id, userId, status);
            return ResponseEntity.ok(Resp.ok("操作成功"));
        } catch (Exception e) {
            log.error("更新 API key 状态错误", e);
            return fail(500, "操作失败");
        }
    }

    @RequireLogin
    @DeleteMapping("/api-keys/{id}")
    public ResponseEntity<?> remove(@PathVariable("id") String id, HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        if (userId == null) {
            return fail(401, "未登录或登录过期");
        }
        try {
            apiKeyService.remove(id, userId);
            return ResponseEntity.ok(Resp.ok("删除成功"));
        } catch (Exception e) {
            log.error("删除 API key 错误", e);
            return fail(500, "删除失败");
        }
    }

    private static ResponseEntity<?> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }
}
