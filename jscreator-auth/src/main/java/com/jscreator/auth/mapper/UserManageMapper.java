package com.jscreator.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * user 域（{@code /userInfo}、{@code /user-manage/*}）的 SQL，逐条对齐 Express 版
 * {@code modules/user/user.dao} + {@code db_curd} 对应函数，SQL 原样搬运（参数化）。
 *
 * <p>结果用 {@link LinkedHashMap} 承接：MyBatis 关掉了下划线转驼峰，列名（{@code checkinDay}、
 * {@code role_name}）原样进 JSON，键序也与 SELECT 列序一致。
 */
public interface UserManageMapper {

    // ================= 查询 =================

    /**
     * 管理员分页列表（db_curd.user_getAllByPage）。keyword 为空时不加 WHERE；
     * LIMIT/OFFSET 原样透传（原版也是把算好的 offset 直接塞进 SQL）。
     */
    @Select("<script>"
            + "SELECT u.id, u.username, u.email, u.avatar, u.created_at, u.updated_at, u.name, u.area, u.bio, u.vip, u.checkinDay, r.role_id, r.role_name "
            + "FROM user u JOIN role r ON u.role_id = r.role_id "
            + "<if test=\"kw != null\">WHERE (u.username LIKE #{kw} OR u.email LIKE #{kw} OR u.name LIKE #{kw}) </if>"
            + "ORDER BY u.id DESC LIMIT #{pageSize} OFFSET #{offset}"
            + "</script>")
    List<LinkedHashMap<String, Object>> listPage(@Param("kw") String kw,
                                                 @Param("pageSize") Object pageSize,
                                                 @Param("offset") Object offset);

    /** 与列表同条件的总数（db_curd.user_getAllByPage 的 countSql）。 */
    @Select("<script>"
            + "SELECT COUNT(*) AS total FROM user u JOIN role r ON u.role_id = r.role_id "
            + "<if test=\"kw != null\">WHERE (u.username LIKE #{kw} OR u.email LIKE #{kw} OR u.name LIKE #{kw}) </if>"
            + "</script>")
    Long countPage(@Param("kw") String kw);

    /** 详情（db_curd.user_getById，LEFT JOIN role；socials/featured_articles 由 service 防御性 parse）。 */
    @Select("SELECT u.id, u.username, u.email, u.avatar, u.bio, u.vip, u.checkinDay, u.name, u.area, "
            + "u.socials, u.featured_articles, u.github_id, "
            + "u.created_at, u.updated_at, u.role_id, r.role_name "
            + "FROM user u LEFT JOIN role r ON u.role_id = r.role_id "
            + "WHERE u.id = #{id}")
    LinkedHashMap<String, Object> findDetailById(@Param("id") Object id);

    /** 权限校验用：查用户 role_id（原版 userDao.getRoleId）。 */
    @Select("SELECT role_id FROM user WHERE id = #{id}")
    Long roleIdOf(@Param("id") Long id);

    /** 新增用户前的查重（原版 SELECT * ... 后判有无行，这里只取主键）。 */
    @Select("SELECT id FROM user WHERE email = #{email}")
    List<Object> idsByEmail(@Param("email") String email);

    @Select("SELECT id FROM user WHERE username = #{username}")
    List<Object> idsByUsername(@Param("username") String username);

    // ================= 写入 =================

    /** db_curd.user_add：没给 role_id 就走不带 role_id 的 INSERT（让列默认值生效）。 */
    @Insert("INSERT INTO user (username, email, password) VALUES (#{username}, #{email}, #{password})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(Map<String, Object> params);

    @Insert("INSERT INTO user (username, email, password, role_id) VALUES (#{username}, #{email}, #{password}, #{role_id})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUserWithRole(Map<String, Object> params);

    /** 动态字段更新（列白名单见 {@link UserManageSqlProvider}）。 */
    @UpdateProvider(type = UserManageSqlProvider.class, method = "updateProfile")
    int updateProfile(@Param("id") Object id, @Param("fields") Map<String, Object> fields);

    @Update("UPDATE user SET password = #{hash} WHERE id = #{id}")
    int setPasswordHash(@Param("id") Object id, @Param("hash") String hash);

    @Update("UPDATE user SET github_id = NULL WHERE id = #{id}")
    int clearGithubId(@Param("id") Object id);

    @Delete("DELETE FROM user WHERE id = #{id}")
    int deleteById(@Param("id") Object id);

    @Delete("<script>DELETE FROM user WHERE id IN "
            + "<foreach collection=\"ids\" item=\"one\" open=\"(\" separator=\",\" close=\")\">#{one}</foreach>"
            + "</script>")
    int deleteBatchByIds(@Param("ids") List<?> ids);
}
