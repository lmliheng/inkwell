package com.jscreator.auth.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /userInfo}、{@code /user-manage/update} 的动态 UPDATE SQL，对齐 Express 版
 * {@code user.dao.updateProfile}：只拼「请求体里出现过的列」，列名取自固定白名单，
 * 值仍然走 {@code #{}} 参数化（不拼值，避免注入，也保证与原版一样的类型行为）。
 */
public final class UserManageSqlProvider {

    private UserManageSqlProvider() {
    }

    /**
     * 可更新列，顺序与原版 {@code db_curd.user_updateProfile} 的 allowed 列表一致
     * （决定了 SET 子句的顺序，也决定了「哪些字段算有内容」）。
     */
    public static final List<String> UPDATABLE_COLUMNS = List.of(
            "username", "email", "role_id", "bio", "vip", "checkinDay", "name", "area", "avatar",
            "socials", "featured_articles");

    /** 无字段可更新时原版返回 false（管理员更新会翻成 400），这里由 service 先判断，不会走到空 SET。 */
    @SuppressWarnings("unchecked")
    public static String updateProfile(Map<String, Object> params) {
        Map<String, Object> fields = (Map<String, Object>) params.get("fields");
        List<String> sets = new ArrayList<>();
        for (String column : UPDATABLE_COLUMNS) {
            if (fields != null && fields.containsKey(column)) {
                sets.add(column + " = #{fields." + column + "}");
            }
        }
        if (sets.isEmpty()) {
            throw new IllegalArgumentException("没有需要更新的字段");
        }
        return "UPDATE user SET " + String.join(", ", sets) + " WHERE id = #{id}";
    }
}
