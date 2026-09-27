package com.jscreator.system.monitor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把每个请求的耗时与状态码记进 {@link ApiMonitor}。
 *
 * <p>对应 Express 版 app.ts 里挂在所有路由之前的那个中间件（在 {@code res.on('finish')} 里记录），
 * 因此这里也放在过滤器链最前面：404 / 401 这类没走到处理方法的请求同样会被统计。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiMonitorFilter extends OncePerRequestFilter {

    private final ApiMonitor monitor;

    public ApiMonitorFilter(ApiMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            // 与 Express 的 req.path 对齐：不含查询串，保留原始编码
            monitor.record(request.getMethod(), request.getRequestURI(), response.getStatus(),
                    System.currentTimeMillis() - start);
        }
    }
}
