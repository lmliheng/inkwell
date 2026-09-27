package com.jscreator.auth.controller;

import com.jscreator.auth.service.GithubAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * /auth/github*（302 重定向流，非 JSON 响应），对齐 Express 版 modules/auth/github/githubAuth.controller：
 * 三个接口都公开，返回 302 + Location；响应体也照抄 Express 的
 * {@code Found. Redirecting to <url>}（text/plain），方便逐字节对照。
 *
 * <p>查询参数用 {@code @RequestParam(required = false)}：缺省是 null，与原版 req.query 的 undefined 对应；
 * {@code ?redirect=} 这种空串仍然进入服务层（与原版一致，服务层再做真假判断）。
 */
@RestController
public class GithubAuthController {

    private final GithubAuthService githubAuthService;

    public GithubAuthController(GithubAuthService githubAuthService) {
        this.githubAuthService = githubAuthService;
    }

    /** GET /auth/github */
    @GetMapping("/auth/github")
    public ResponseEntity<byte[]> login(@RequestParam(value = "redirect", required = false) String redirect) {
        return redirect(githubAuthService.loginAuthorizeUrl(redirect));
    }

    /** GET /auth/github/bind */
    @GetMapping("/auth/github/bind")
    public ResponseEntity<byte[]> bind(@RequestParam(value = "redirect", required = false) String redirect,
                                      @RequestParam(value = "token", required = false) String token) {
        return redirect(githubAuthService.bindAuthorizeUrl(redirect, token));
    }

    /** GET /auth/github/callback */
    @GetMapping("/auth/github/callback")
    public ResponseEntity<byte[]> callback(@RequestParam(value = "code", required = false) String code,
                                           @RequestParam(value = "state", required = false) String state) {
        return redirect(githubAuthService.handleCallback(code, state));
    }

    /** 302 + Location，响应体与 Content-Type 也照抄 Express 的 res.redirect（text/plain; charset=utf-8）。 */
    private static ResponseEntity<byte[]> redirect(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LOCATION, url);
        headers.set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8");
        byte[] body = ("Found. Redirecting to " + url).getBytes(StandardCharsets.UTF_8);
        return new ResponseEntity<>(body, headers, HttpStatus.FOUND);
    }
}
