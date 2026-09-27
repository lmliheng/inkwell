package com.jscreator.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jscreator.common.api.Resp;
import com.jscreator.common.security.JwtUtil;
import com.jscreator.common.security.TokenPayload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 鉴权拦截器，语义对齐 Express 版 common/middleware/auth：
 *
 * <ul>
 *   <li>每个请求都尝试解析 {@code Authorization: Bearer <token>}，结果放进请求属性（解析失败为 null，不报错）</li>
 *   <li>处理方法/类上带 {@link RequireLogin} 且未登录 → HTTP 401 + {@code {code:401,success:false,message:'未登录或登录过期'}}</li>
 *   <li>带 {@link RequireAdmin} → 先按登录校验，再查库确认 role_id === 1，否则 403 + {@code '权限不足，仅管理员可操作'}</li>
 * </ul>
 *
 * 行为与 Node 版一致：公开接口不注解即放行。
 */
public class AuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_ATTR = "jscreator.tokenPayload";

    private final JwtUtil jwtUtil;
    private final RoleLookup roleLookup;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(JwtUtil jwtUtil, RoleLookup roleLookup) {
        this.jwtUtil = jwtUtil;
        this.roleLookup = roleLookup;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        TokenPayload payload = jwtUtil.verify(request.getHeader("Authorization"));
        request.setAttribute(TOKEN_ATTR, payload);

        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        boolean needLogin = AnnotatedElementUtils.hasAnnotation(method.getMethod(), RequireLogin.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), RequireLogin.class);
        boolean needAdmin = AnnotatedElementUtils.hasAnnotation(method.getMethod(), RequireAdmin.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), RequireAdmin.class);

        if (!needLogin && !needAdmin) {
            return true;
        }
        if (payload == null || payload.id() == null) {
            reject(response, 401, "未登录或登录过期");
            return false;
        }
        if (needAdmin) {
            Long role = roleLookup.roleIdOf(payload.id());
            if (role == null || role != 1L) {
                reject(response, 403, "权限不足，仅管理员可操作");
                return false;
            }
        }
        return true;
    }

    private void reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Resp.fail(code, message)));
    }
}
