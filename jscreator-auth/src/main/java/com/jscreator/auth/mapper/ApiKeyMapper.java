package com.jscreator.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * api_key 表的 SQL，逐条对齐 Express 版 modules/openapi/apiKey.dao。
 * 安全约定同原版：库里只存 {@code key_hash}（SHA-256 hex）+ {@code key_prefix}，明文只在创建时返回一次。
 *
 * <p>id / user_id 用 {@code Object} 声明，因为原版把路由参数原样塞进 SQL（字符串 "6" 与数字 6 都行）。
 */
public interface ApiKeyMapper {

    @Insert("INSERT INTO api_key (user_id, name, key_hash, key_prefix, scopes) "
            + "VALUES (#{userId}, #{name}, #{keyHash}, #{keyPrefix}, #{scopes})")
    int insertKey(@Param("userId") Object userId,
                  @Param("name") String name,
                  @Param("keyHash") String keyHash,
                  @Param("keyPrefix") String keyPrefix,
                  @Param("scopes") String scopes);

    @Select("SELECT id, name, key_prefix, scopes, status, last_used_at, created_at "
            + "FROM api_key WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<LinkedHashMap<String, Object>> listByUser(@Param("userId") Object userId);

    @Update("UPDATE api_key SET status = #{status} WHERE id = #{id} AND user_id = #{userId}")
    int setStatus(@Param("id") Object id, @Param("userId") Object userId, @Param("status") Integer status);

    @Delete("DELETE FROM api_key WHERE id = #{id} AND user_id = #{userId}")
    int delete(@Param("id") Object id, @Param("userId") Object userId);

    @Select("SELECT id, user_id, scopes, status FROM api_key WHERE key_hash = #{keyHash} LIMIT 1")
    LinkedHashMap<String, Object> findByHash(@Param("keyHash") String keyHash);

    @Update("UPDATE api_key SET last_used_at = NOW() WHERE id = #{id}")
    int touchLastUsed(@Param("id") Object id);
}
