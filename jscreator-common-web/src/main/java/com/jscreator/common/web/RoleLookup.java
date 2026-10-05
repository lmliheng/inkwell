package com.jscreator.common.web;

/**
 * 角色查询钩子。管理端鉴权需要「查库确认 role_id === 1」，而 user 表归 auth 服务独占，
 * 因此由各服务提供实现（auth 查库、其他服务可走 Feign 或本地只读查询）。
 */
@FunctionalInterface
public interface RoleLookup {
    /** 返回用户角色 id；查不到返回 null。 */
    Long roleIdOf(Long userId);
}
