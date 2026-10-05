package com.jscreator.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.LinkedHashMap;

/**
 * /email/* 用到的 user 表 SQL，对齐 Express 版 modules/user/user.dao.ts：
 * findByEmail（SELECT *）、registerEmailUser（username 同时写 name、role_id=2、password 原样存）。
 */
public interface EmailAuthMapper {

    @Select("SELECT * FROM user WHERE email = #{email}")
    LinkedHashMap<String, Object> findByEmail(@Param("email") String email);

    @Insert("INSERT INTO user (username, name, email, password, role_id) VALUES (#{username}, #{username}, #{email}, #{password}, 2)")
    int registerEmailUser(@Param("username") String username,
                          @Param("email") String email,
                          @Param("password") String password);
}
