package com.jscreator.content.config;

import com.jscreator.common.api.Resp;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * content 模块自己的兜底映射：把「路径存在、方法不对」从 500 拉回 404。
 *
 * <p>原版 Express 没有按方法注册的路由会落到默认 404（「Cannot GET /ad/click/3」），
 * 而 common-web 的全局兜底会把 HttpRequestMethodNotSupportedException 当未捕获异常 → 500
 * 「服务器内部错误」—— 同一个 URL 上，原版 404、我们 500，是能观测到的差异。
 * 这里把方法不匹配拉回 404：状态码对齐原版，信封沿用本项目的形状。
 *
 * <p>不加包限定：方法不匹配时 DispatcherServlet 传给 @ExceptionHandler 的 handler 是 null，
 * 带 basePackages 限定的 advice 在这种场景不适用（会被 common-web 的兜底抢走），只能全局生效
 * ——content 应用只服务本模块的接口，全局即等价。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class MethodNotSupportedAdvice {

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Resp.fail(404, "接口不存在"));
    }
}
