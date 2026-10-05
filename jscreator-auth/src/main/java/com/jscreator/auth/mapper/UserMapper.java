package com.jscreator.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jscreator.auth.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface UserMapper extends BaseMapper<User> {

    /**
     * /sys/profile 的联表查询，SQL 与原版 db_curd.getUserInfoByToken 一致（含 JOIN 语义：
     * 没有配权限的用户查不出行，前端会收到「找不到用户信息」）。
     */
    @Select("""
            SELECT u.username, u.email, u.id, u.avatar, u.created_at, u.name, u.vip, u.area, u.bio, u.checkinDay,
                   r.role_name, r.role_id, p.permission_name, p.permission_id
            FROM user u
            JOIN role r ON u.role_id = r.role_id
            JOIN roleandpermission_middle rp ON rp.role_id = r.role_id
            JOIN permission p ON p.permission_id = rp.permission_id
            WHERE u.id = #{userId}
            """)
    List<LinkedHashMap<String, Object>> profileRows(@Param("userId") Long userId);

    /** /sys/profile 统计：已发布文章数 */
    @Select("SELECT COUNT(*) FROM article WHERE `user` = #{userId} AND status = 1")
    long countPublishedArticles(@Param("userId") Long userId);

    /** /sys/profile 统计：评论数 */
    @Select("SELECT COUNT(*) FROM comment WHERE user_id = #{userId}")
    long countComments(@Param("userId") Long userId);

    /** 管理端守卫用：查用户 role_id */
    @Select("SELECT role_id FROM user WHERE id = #{userId}")
    Long selectRoleId(@Param("userId") Long userId);
}
