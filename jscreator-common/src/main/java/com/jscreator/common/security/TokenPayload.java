package com.jscreator.common.security;

/** JWT 载荷，字段与原版 token_creator 一致：{ id, role_id }。id / roleId 都可能为空（老 token）。 */
public record TokenPayload(Long id, Long roleId) {
}
