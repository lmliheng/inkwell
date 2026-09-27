package com.jscreator.auth.controller;

import com.jscreator.auth.service.UserService;
import com.jscreator.common.api.Resp;
import com.jscreator.common.security.TokenPayload;
import com.jscreator.common.util.Times;
import com.jscreator.common.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /sys/profile。注意原版这条路由<b>没有挂 verifyToken</b>，token 无效时不是 401 而是
 * HTTP 200 + code 500 —— 前端按这个怪癖写的，照抄。
 */
@RestController
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/sys/profile")
    public Map<String, Object> profile(HttpServletRequest request) {
        try {
            TokenPayload payload = CurrentUser.of(request);
            if (payload == null || payload.id() == null) {
                return Resp.fail(500, "获取用户信息失败");
            }
            Map<String, Object> out = userService.profile(payload.id());
            if (out == null) {
                return Resp.fail(401, "找不到用户信息，是否未登录或登录过期");
            }
            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("user_detail", out.get("user_detail"));
            userInfo.put("user_permission", out.get("user_permission"));
            userInfo.put("login_time", Times.loginTime());

            return Resp.map(
                    "code", 200,
                    "success", true,
                    "message", "获取用户信息成功",
                    "user_info", userInfo);
        } catch (Exception e) {
            log.error("获取用户信息错误", e);
            return Resp.fail(500, "获取用户信息失败");
        }
    }
}
