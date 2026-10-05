package com.jscreator.auth.controller;

import com.jscreator.auth.service.TotpService;
import com.jscreator.common.api.Resp;
import com.jscreator.common.util.Times;
import com.jscreator.common.web.CurrentUser;
import com.jscreator.common.web.RequireLogin;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /totp/* 的 HTTP 层，逐字段对齐 Express 版 modules/auth/totp/totp.controller：
 * 成功响应恒带 data 键（传 null 时输出 {@code "data": null}），失败是 {@code HTTP 状态码 + {code,success:false,message}}。
 *
 * <p>setup/confirm/disable/status 需要登录（原版路由挂 verifyToken，此处用 @RequireLogin，
 * 401 文案同为「未登录或登录过期」）；/totp/login 公开。
 * 业务异常一律交给 GlobalExceptionHandler（500「服务器内部错误」，与原版 catch 后的输出一致）。
 */
@RestController
public class TotpController {

    private static final Logger log = LoggerFactory.getLogger(TotpController.class);

    private final TotpService totpService;

    public TotpController(TotpService totpService) {
        this.totpService = totpService;
    }

    /** POST /totp/setup */
    @PostMapping("/totp/setup")
    @RequireLogin
    public ResponseEntity<?> setup(HttpServletRequest request) {
        Long id = CurrentUser.id(request);
        if (id == null) {
            return fail(401, "未登录或登录过期");
        }
        TotpService.SetupInfo out = totpService.setup(id);
        if (out == null) {
            return fail(404, "用户不存在");
        }
        return ok(Resp.map("secret", out.secret(), "uri", out.uri()), "获取绑定信息成功");
    }

    /** POST /totp/confirm */
    @PostMapping("/totp/confirm")
    @RequireLogin
    public ResponseEntity<?> confirm(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> body) {
        Long id = CurrentUser.id(request);
        if (id == null) {
            return fail(401, "未登录或登录过期");
        }
        Map<String, Object> in = body == null ? Map.of() : body;
        String secret = jsString(in.get("secret"));
        String code = jsString(in.get("code"));
        if (secret.isEmpty() || code.isEmpty()) {
            return fail(400, "参数缺失");
        }
        if (!totpService.confirm(id, secret, code)) {
            return fail(400, "验证码不正确，请重试");
        }
        return ok(null, "TOTP 绑定成功");
    }

    /** POST /totp/disable */
    @PostMapping("/totp/disable")
    @RequireLogin
    public ResponseEntity<?> disable(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> body) {
        Long id = CurrentUser.id(request);
        if (id == null) {
            return fail(401, "未登录或登录过期");
        }
        Map<String, Object> in = body == null ? Map.of() : body;
        String code = jsString(in.get("code"));
        String out = totpService.disable(id, code);
        if ("not-bound".equals(out)) {
            return fail(400, "尚未绑定 TOTP");
        }
        if ("code-wrong".equals(out)) {
            return fail(400, "验证码不正确");
        }
        return ok(null, "已解绑 TOTP");
    }

    /** GET /totp/status */
    @GetMapping("/totp/status")
    @RequireLogin
    public ResponseEntity<?> status(HttpServletRequest request) {
        Long id = CurrentUser.id(request);
        if (id == null) {
            return fail(401, "未登录或登录过期");
        }
        return ok(totpService.status(id));
    }

    /** POST /totp/login（公开） */
    @PostMapping("/totp/login")
    public ResponseEntity<?> login(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        String account = jsString(in.get("account")).trim();
        String code = jsString(in.get("code")).trim();
        if (account.isEmpty() || code.isEmpty()) {
            return fail(400, "账号和动态码不能为空");
        }
        Object out = totpService.login(account, code);
        if (out instanceof String s) {
            switch (s) {
                case "no-account" -> {
                    return fail(401, "账号不存在");
                }
                case "not-bound" -> {
                    return fail(400, "该账号未绑定 TOTP，请先登录后在个人设置中绑定");
                }
                default -> {
                    return fail(401, "动态码错误或已过期");
                }
            }
        }
        TotpService.LoginOk ok = (TotpService.LoginOk) out;
        LinkedHashMap<String, Object> u = ok.user();
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", u.get("id"));
        userInfo.put("username", u.get("username"));
        userInfo.put("email", u.get("email"));
        userInfo.put("role_id", u.get("role_id"));
        userInfo.put("avatar", u.get("avatar"));
        userInfo.put("bio", u.get("bio"));
        userInfo.put("area", u.get("area"));
        userInfo.put("name", u.get("name"));
        userInfo.put("vipLevel", u.get("name")); // 与 legacy 一致的字段怪癖
        userInfo.put("checkinDay", u.get("checkinDay"));
        userInfo.put("login_time", Times.loginTime());

        return ResponseEntity.ok(Resp.map(
                "code", 200,
                "success", true,
                "message", "登录成功",
                "token", ok.token(),
                "user_info", userInfo));
    }

    // ------------------------------------------------------------------ 响应构造

    /** 原版 ok()：恒含 data（null 就输出 data: null），message 默认 'ok'。 */
    private static ResponseEntity<Map<String, Object>> ok(Object data) {
        return ok(data, "ok");
    }

    private static ResponseEntity<Map<String, Object>> ok(Object data, String message) {
        return ResponseEntity.ok(Resp.map(
                "code", 200,
                "success", true,
                "message", message,
                "data", data));
    }

    private static ResponseEntity<Map<String, Object>> fail(int code, String message) {
        return ResponseEntity.status(HttpStatus.valueOf(code)).body(Resp.fail(code, message));
    }

    /** 对齐 JS 的 {@code String(body.x ?? '')}：null/缺省 → ''，其余走 String()。 */
    private static String jsString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
