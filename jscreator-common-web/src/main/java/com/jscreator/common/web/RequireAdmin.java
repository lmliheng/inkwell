package com.jscreator.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅管理员可访问，对应 Express 版 {@code adminOnly(getRoleId)}：先校验 token，再查库确认 role_id === 1。
 *
 * <p>{@link #message()} 用来对齐原版里文案不一致的那几处：默认是 {@code adminOnly} 的
 * 「权限不足，仅管理员可操作」，而 {@code modules/systemmon} 自己写了 adminGuard，
 * 403 文案是「权限不足，仅管理员可查看系统监控」。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {

    /** 非管理员时的 403 文案（对齐原版各自的写法）。 */
    String message() default "权限不足，仅管理员可操作";
}
