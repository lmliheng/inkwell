package com.jscreator.auth.controller;

import com.jscreator.auth.dto.OauthOutcome;
import com.jscreator.auth.service.OauthService;
import com.jscreator.auth.util.OauthOpenapiJs;
import com.jscreator.common.api.Resp;
import com.jscreator.common.web.CurrentUser;
import com.jscreator.common.web.RequireAdmin;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /oauth/**，逐字段对齐 Express 版 modules/oauth/oauth.controller：
 * 管理端 5 条是 {@code [verifyToken, adminOnly]}；{@code /oauth/authorize} 与 {@code /oauth/token} 公开
 * （授权端点自己从 Authorization 解析用户，未登录就跳登录页）。
 *
 * <p>每个端点的兜底 500 文案照抄原版（原版 controller 把 DB 异常吞成了各不相同的文案：
 * 获取失败 / 创建失败 / 更新失败 / 操作失败 / 删除失败；authorize 是纯文本「服务器内部错误」）。
 */
@RestController
public class OauthController {

    private static final Logger log = LoggerFactory.getLogger(OauthController.class);

    private final OauthService oauthService;

    public OauthController(OauthService oauthService) {
        this.oauthService = oauthService;
    }

    // ---------- 管理端 ----------

    @RequireAdmin
    @GetMapping("/oauth/admin/clients")
    public ResponseEntity<?> listClients() {
        try {
            return render(oauthService.listClients());
        } catch (Exception e) {
            log.error("OAuth client list 错误", e);
            return fail(500, "获取失败");
        }
    }

    @RequireAdmin
    @PostMapping("/oauth/admin/clients")
    public ResponseEntity<?> createClient(@RequestBody(required = false) Object body) {
        try {
            return render(oauthService.createClient(OauthOpenapiJs.asObjectMap(body)));
        } catch (Exception e) {
            log.error("OAuth client create 错误", e);
            return fail(500, "创建失败");
        }
    }

    @RequireAdmin
    @PutMapping("/oauth/admin/clients/{id}")
    public ResponseEntity<?> updateClient(@PathVariable("id") String id,
                                          @RequestBody(required = false) Object body) {
        try {
            return render(oauthService.updateClient(id, OauthOpenapiJs.asObjectMap(body)));
        } catch (Exception e) {
            log.error("OAuth client update 错误", e);
            return fail(500, "更新失败");
        }
    }

    @RequireAdmin
    @PutMapping("/oauth/admin/clients/{id}/status")
    public ResponseEntity<?> setClientStatus(@PathVariable("id") String id,
                                            @RequestBody(required = false) Object body) {
        try {
            Object status = OauthOpenapiJs.asObjectMap(body).get("status");
            return render(oauthService.setClientStatus(id, status));
        } catch (Exception e) {
            log.error("OAuth client status 错误", e);
            return fail(500, "操作失败");
        }
    }

    @RequireAdmin
    @DeleteMapping("/oauth/admin/clients/{id}")
    public ResponseEntity<?> deleteClient(@PathVariable("id") String id) {
        try {
            return render(oauthService.deleteClient(id));
        } catch (Exception e) {
            log.error("OAuth client delete 错误", e);
            return fail(500, "删除失败");
        }
    }

    // ---------- 授权端点 ----------

    @GetMapping("/oauth/authorize")
    public ResponseEntity<?> authorize(HttpServletRequest request) {
        try {
            Long userId = CurrentUser.id(request);
            return render(oauthService.authorize(collectQuery(request), userId, queryString(request)));
        } catch (Exception e) {
            log.error("OAuth authorize 错误", e);
            return ResponseEntity.status(500).contentType(MediaType.TEXT_HTML).body("服务器内部错误");
        }
    }

    @PostMapping("/oauth/token")
    public ResponseEntity<?> token(@RequestBody(required = false) Object body) {
        try {
            return render(oauthService.token(OauthOpenapiJs.asObjectMap(body)));
        } catch (Exception e) {
            log.error("OAuth token 错误", e);
            return fail(500, "服务器内部错误");
        }
    }

    // ---------- 输出层 ----------

    private static ResponseEntity<?> render(OauthOutcome out) {
        if (out instanceof OauthOutcome.Json json) {
            return ResponseEntity.status(json.status()).body(json.body());
        }
        if (out instanceof OauthOutcome.Text text) {
            return ResponseEntity.status(text.status()).contentType(MediaType.TEXT_HTML).body(text.text());
        }
        OauthOutcome.Redirect redirect = (OauthOutcome.Redirect) out;
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, redirect.location()).build();
    }

    private static ResponseEntity<?> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }

    /** 原版 {@code req.query}：同名参数出现多次时 Express 给数组，String(array) 是逗号拼接。 */
    private static Map<String, Object> collectQuery(HttpServletRequest request) {
        Map<String, Object> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values == null || values.length == 0) {
                return;
            }
            query.put(name, values.length == 1 ? values[0] : String.join(",", values));
        });
        return query;
    }

    /** 原版换取登录跳转用的是 {@code new URLSearchParams(req.query).toString()} 重新编码后的串。 */
    private static String queryString(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        request.getParameterMap().forEach((name, values) -> {
            if (values == null) {
                return;
            }
            for (String value : values) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
            }
        });
        return sb.toString();
    }
}
