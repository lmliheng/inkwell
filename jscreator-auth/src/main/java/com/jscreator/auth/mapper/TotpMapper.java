package com.jscreator.auth.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.LinkedHashMap;

/**
 * /totp/* 用到的 user 表 SQL，逐条对齐 Express 版 modules/user/user.dao.ts 里给 TOTP 用的那几个方法。
 * 列名原样返回（MyBatis 关了下划线转驼峰），Map 的键顺序与 SELECT 列序一致。
 */
public interface TotpMapper {

    /** userDao.getBasicById */
    @Select("SELECT id, username, email FROM user WHERE id = #{id}")
    LinkedHashMap<String, Object> selectBasicById(@Param("id") Object id);

    /** userDao.getTotpSecret：无行或 totp_secret 为 NULL 都返回 null。 */
    @Select("SELECT totp_secret FROM user WHERE id = #{id}")
    String selectTotpSecret(@Param("id") Object id);

    /** userDao.setTotpSecret：secret 传 null 即解绑。 */
    @Update("UPDATE user SET totp_secret = #{secret} WHERE id = #{id}")
    int updateTotpSecret(@Param("id") Object id, @Param("secret") String secret);

    /** userDao.findByAccountForTotp：用户名或邮箱定位，原样 LIMIT 1。 */
    @Select("""
            SELECT id, username, email, role_id, avatar, bio, area, name, totp_secret, checkinDay
            FROM user WHERE username = #{account} OR email = #{account} LIMIT 1
            """)
    LinkedHashMap<String, Object> findByAccountForTotp(@Param("account") String account);
}
