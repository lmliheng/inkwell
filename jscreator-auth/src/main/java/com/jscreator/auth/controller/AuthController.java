package com.jscreator.auth.controller;

import com.jscreator.auth.entity.User;
import com.jscreator.auth.service.AuthService;
import com.jscreator.common.api.Resp;
import com.jscreator.common.util.Times;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录/注册 HTTP 层。响应体逐字段对齐 Express 版 modules/auth/auth.controller，
 * 包括它的状态码怪癖：
 * <ul>
 *   <li>校验失败：HTTP 200 + body.code=400/500（注册路径）</li>
 *   <li>用户名登录异常：HTTP 200 + body.code=500</li>
 *   <li>邮箱登录异常：HTTP 500 且 body 里<b>没有 code 字段</b></li>
 * </ul>
 */
@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sys/login")
    public ResponseEntity<?> login(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;

        // ---------- 邮箱登录 ----------
        if (truthy(in.get("email"))) {
            try {
                AuthService.LoginOk out = authService.loginByEmail(str(in.get("email")), strOrEmpty(in.get("password")));
                if (out == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Resp.fail(401, "邮箱或密码错误"));
                }
                User u = out.user();
                Map<String, Object> userInfo = new LinkedHashMap<>();
                userInfo.put("id", u.getId());
                userInfo.put("username", u.getUsername());
                userInfo.put("email", u.getEmail());
                userInfo.put("role_id", u.getRoleId());
                userInfo.put("avatar", u.getAvatar());
                userInfo.put("login_time", Times.loginTime());

                return ResponseEntity.ok(Resp.map(
                        "code", 200,
                        "success", true,
                        "message", "登录成功",
                        "token", out.token(),
                        "user_info", userInfo));
            } catch (Exception e) {
                log.error("登录错误", e);
                // 原版此处不带 code 键，照抄
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("success", false);
                m.put("message", "服务器内部错误");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(m);
            }
        }

        // ---------- 用户名登录 ----------
        try {
            AuthService.LoginOk out = authService.loginByUsername(strOrEmpty(in.get("username")), strOrEmpty(in.get("password")));
            if (out == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Resp.fail(401, "用户名或密码错误"));
            }
            User u = out.user();
            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("id", u.getId());
            userInfo.put("username", u.getUsername());
            userInfo.put("email", u.getEmail());
            userInfo.put("role_id", u.getRoleId());
            userInfo.put("avatar", u.getAvatar());
            userInfo.put("bio", u.getBio());
            userInfo.put("area", u.getArea());
            userInfo.put("name", u.getName());
            userInfo.put("vipLevel", u.getName()); // 原版字段怪癖：vipLevel 装的其实是 name
            userInfo.put("checkinDay", u.getCheckinDay());
            userInfo.put("login_time", Times.loginTime());

            return ResponseEntity.ok(Resp.map(
                    "code", 200,
                    "success", true,
                    "message", "登录成功",
                    "token", out.token(),
                    "user_info", userInfo));
        } catch (Exception e) {
            log.error("登录错误", e);
            return ResponseEntity.ok(Resp.fail(500, "登录失败")); // 原版：HTTP 200 + code 500
        }
    }

    @PostMapping("/sys/register")
    public ResponseEntity<?> register(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;

        String mode = truthy(in.get("register_mode")) ? str(in.get("register_mode")) : "";
        if ("email".equals(mode)) {
            return ResponseEntity.ok(Resp.ok("邮箱注册模式，未开发"));
        }
        String username = truthy(in.get("username")) ? str(in.get("username")) : null;
        String email = truthy(in.get("email")) ? str(in.get("email")) : null;
        String password = truthy(in.get("password")) ? str(in.get("password")) : null;
        if (username == null || email == null || password == null) {
            return ResponseEntity.ok(Resp.fail(400, "用户名、邮箱或密码不能为空"));
        }
        try {
            Object out = authService.register(username, email, password);
            if (out instanceof AuthService.RegisterFail fail) {
                return ResponseEntity.ok(Resp.fail(fail.code(), fail.message()));
            }
            AuthService.RegisterOk ok = (AuthService.RegisterOk) out;
            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("id", ok.id());
            userInfo.put("username", ok.username());
            userInfo.put("email", ok.email());
            return ResponseEntity.ok(Resp.map(
                    "code", 200,
                    "success", true,
                    "message", "注册成功",
                    "token", ok.token(),
                    "user_info", userInfo));
        } catch (Exception e) {
            log.error("注册用户错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("注册用户失败");
        }
    }

    private static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0;
        }
        String s = String.valueOf(v);
        return !s.isEmpty() && !"false".equals(s) && !"0".equals(s);
    }

    private static String str(Object v) {
        return String.valueOf(v);
    }

    private static String strOrEmpty(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
