package com.jscreator.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.LinkedHashMap;

/**
 * /auth/github* 用到的 user 表 SQL，对齐 Express 版 modules/user/user.dao.ts：
 * getByGithubId（SELECT *）、registerGithubUser（role_id=2、password 原样存）、setGithubId。
 */
public interface GithubAuthMapper {

    @Select("SELECT * FROM user WHERE github_id = #{githubId}")
    LinkedHashMap<String, Object> getByGithubId(@Param("githubId") Object githubId);

    @Insert("INSERT INTO user (username, name, email, github_id, password, avatar, role_id) "
            + "VALUES (#{username}, #{name}, #{email}, #{githubId}, #{password}, #{avatar}, 2)")
    int registerGithubUser(@Param("githubId") Object githubId,
                           @Param("username") String username,
                           @Param("name") String name,
                           @Param("email") String email,
                           @Param("password") String password,
                           @Param("avatar") String avatar);

    @Update("UPDATE user SET github_id = #{githubId} WHERE id = #{userId}")
    int setGithubId(@Param("userId") Object userId, @Param("githubId") Object githubId);
}
