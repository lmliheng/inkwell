package com.jscreator.auth.controller;

import com.jscreator.auth.service.EmailAuthService;
import com.jscreator.common.api.Resp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /email/send-code、/email/login，逐字段对齐 Express 版 modules/auth/email/emailAuth.controller。
 *
 * <p>两个接口都是公开的（原版路由没有挂 verifyToken）。校验失败是 HTTP 400 + code:400；
 * 发送失败 500「验证码发送失败」；登录内部异常 500「服务器内部错误」。
 */
@RestController
public class EmailAuthController {

    private static final Logger log = LoggerFactory.getLogger(EmailAuthController.class);

    private final EmailAuthService emailAuthService;

    public EmailAuthController(EmailAuthService emailAuthService) {
        this.emailAuthService = emailAuthService;
    }

    /** POST /email/send-code */
    @PostMapping("/email/send-code")
    public ResponseEntity<?> sendCode(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        String email = jsString(in.get("email")).trim();
        if (email.isEmpty()) {
            return fail(400, "邮箱不能为空");
        }
        try {
            emailAuthService.sendCode(email);
            return ResponseEntity.ok(Resp.ok("验证码已发送，请查收邮件"));
        } catch (Exception e) {
            log.error("发送验证码错误: {}", e.getMessage());
            return fail(500, "验证码发送失败");
        }
    }

    /** POST /email/login */
    @PostMapping("/email/login")
    public ResponseEntity<?> login(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        // 原版这里不做 trim，且是对原值判真假（0/空串都当缺参）
        String email = truthy(in.get("email")) ? jsString(in.get("email")) : "";
        String code = truthy(in.get("code")) ? jsString(in.get("code")) : "";
        if (email.isEmpty() || code.isEmpty()) {
            return fail(400, "邮箱和验证码不能为空");
        }
        if (!emailAuthService.validateCode(email, code)) {
            return fail(400, "验证码错误或已过期");
        }
        try {
            Object out = emailAuthService.login(email);
            if (out instanceof String) {
                return fail(500, "注册失败");
            }
            EmailAuthService.LoginOk ok = (EmailAuthService.LoginOk) out;
            LinkedHashMap<String, Object> u = ok.user();
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", u.get("id"));
            user.put("username", u.get("username"));
            user.put("email", u.get("email"));
            user.put("role_id", u.get("role_id"));
            user.put("avatar", u.get("avatar"));
            user.put("name", u.get("name"));

            return ResponseEntity.ok(Resp.map(
                    "code", 200,
                    "success", true,
                    "message", "登录成功",
                    "token", ok.token(),
                    "user", user));
        } catch (Exception e) {
            log.error("邮箱登录错误: {}", e.getMessage());
            return fail(500, "服务器内部错误");
        }
    }

    // ------------------------------------------------------------------ 小工具

    private static ResponseEntity<Map<String, Object>> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }

    /** 对齐 JS 的 String(v)：null/缺省 → ''。 */
    private static String jsString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** 对齐 JS 的真假判断（用于 body.email ? ... : ''）。 */
    private static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof String s) {
            return !s.isEmpty();
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        return true;
    }
}
