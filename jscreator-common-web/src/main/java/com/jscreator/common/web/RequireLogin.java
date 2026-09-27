package com.jscreator.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要登录，对应 Express 版路由上的 {@code verifyToken}。
 * 与 {@link RequireAdmin} 的区别：本注解只校验 token 有效性，不查角色。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
}
