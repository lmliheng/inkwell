package com.jscreator.common.web;

import com.jscreator.common.security.TokenPayload;
import jakarta.servlet.http.HttpServletRequest;

/** 从请求属性里取当前登录态（由 {@link AuthInterceptor} 写入），语义同 Express 的 {@code req.user}。 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** 未登录返回 null。 */
    public static TokenPayload of(HttpServletRequest request) {
        Object v = request.getAttribute(AuthInterceptor.TOKEN_ATTR);
        return v instanceof TokenPayload p ? p : null;
    }

    /** 当前用户 id，未登录返回 null。 */
    public static Long id(HttpServletRequest request) {
        TokenPayload p = of(request);
        return p == null ? null : p.id();
    }
}
