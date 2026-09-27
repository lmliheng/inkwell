package com.jscreator.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * oauth_client / oauth_code 的 SQL，逐条对齐 Express 版 modules/oauth/oauth.dao（即 utils/db_oauth.js）。
 *
 * <p>Map 结果用 {@link LinkedHashMap} 声明，键序与 SELECT 列序一致（MyBatis 关了下划线转驼峰，
 * 列名原样进 JSON：client_id / redirect_uris / created_at …）。
 * id 这类「原版直接塞进 SQL」的参数用 {@code Object} 声明，字符串 "3" 与数字 3 都能用。
 */
public interface OauthMapper {

    @Select("SELECT id, client_id, name, description, redirect_uris, scopes, grant_types, logo, status, created_at "
            + "FROM oauth_client ORDER BY created_at DESC")
    List<LinkedHashMap<String, Object>> listClients();

    @Insert("INSERT INTO oauth_client (client_id, client_secret, name, description, redirect_uris, scopes, grant_types, logo) "
            + "VALUES (#{clientId}, #{clientSecret}, #{name}, #{description}, #{redirectUris}, #{scopes}, #{grantTypes}, #{logo})")
    int insertClient(@Param("clientId") String clientId,
                     @Param("clientSecret") String clientSecret,
                     @Param("name") String name,
                     @Param("description") String description,
                     @Param("redirectUris") String redirectUris,
                     @Param("scopes") String scopes,
                     @Param("grantTypes") String grantTypes,
                     @Param("logo") String logo);

    /** 取刚插入行的自增 id（原版用的是 result.insertId）。 */
    @Select("SELECT id FROM oauth_client WHERE client_id = #{clientId} LIMIT 1")
    Object selectClientIdByClientId(@Param("clientId") String clientId);

    @Update("UPDATE oauth_client SET name = #{name}, description = #{description}, redirect_uris = #{redirectUris}, "
            + "scopes = #{scopes}, grant_types = #{grantTypes}, logo = #{logo} WHERE id = #{id}")
    int updateClient(@Param("id") Object id,
                     @Param("name") String name,
                     @Param("description") String description,
                     @Param("redirectUris") String redirectUris,
                     @Param("scopes") String scopes,
                     @Param("grantTypes") String grantTypes,
                     @Param("logo") String logo);

    @Select("SELECT * FROM oauth_client WHERE client_id = #{clientId} LIMIT 1")
    LinkedHashMap<String, Object> getClientByClientId(@Param("clientId") String clientId);

    @Update("UPDATE oauth_client SET status = #{status} WHERE id = #{id}")
    int setClientStatus(@Param("id") Object id, @Param("status") Integer status);

    @Delete("DELETE FROM oauth_client WHERE id = #{id}")
    int deleteClient(@Param("id") Object id);

    @Insert("INSERT INTO oauth_code (code, client_id, user_id, scope, code_challenge, redirect_uri, expires_at) "
            + "VALUES (#{code}, #{clientId}, #{userId}, #{scope}, #{challenge}, #{redirectUri}, #{expiresAt})")
    int insertCode(@Param("code") String code,
                   @Param("clientId") String clientId,
                   @Param("userId") Object userId,
                   @Param("scope") String scope,
                   @Param("challenge") String challenge,
                   @Param("redirectUri") String redirectUri,
                   @Param("expiresAt") LocalDateTime expiresAt);

    @Select("SELECT * FROM oauth_code WHERE code = #{code} LIMIT 1")
    LinkedHashMap<String, Object> getCode(@Param("code") String code);

    @Update("UPDATE oauth_code SET used = 1 WHERE id = #{id}")
    int markCodeUsed(@Param("id") Object id);
}
