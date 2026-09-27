package com.jscreator.social.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * follow / article_like / article_favorite / user_notification 的 SQL，逐条对齐 Express 版
 * modules/social/social.dao.ts（即 utils/db_social.js）。
 *
 * <p>Map 结果用 {@link LinkedHashMap} 声明，键序与 SELECT 列序一致（MyBatis 关了下划线转驼峰，
 * 列名与别名原样进 JSON：follow_time / article_title / actor_username …）。
 *
 * <p>「原版直接塞进 SQL」的参数（路径上的 articleId、请求体里的 id）声明成 {@code Object}，
 * 字符串 "8" 与数字 8 都能用，保持与原版一致的隐式转换。
 */
public interface SocialMapper {

    // ===== 关注 =====

    @Insert("INSERT IGNORE INTO follow (follower_id, followee_id) VALUES (#{followerId}, #{followeeId})")
    int followAdd(@Param("followerId") Object followerId, @Param("followeeId") Object followeeId);

    @Delete("DELETE FROM follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    int followRemove(@Param("followerId") Object followerId, @Param("followeeId") Object followeeId);

    @Select("SELECT id FROM follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId} LIMIT 1")
    Object followRowId(@Param("followerId") Object followerId, @Param("followeeId") Object followeeId);

    @Select("SELECT u.id, u.username, u.avatar, u.name, u.bio, u.area, f.created_at AS follow_time "
            + "FROM follow f JOIN user u ON u.id = f.followee_id "
            + "WHERE f.follower_id = #{userId} ORDER BY f.created_at DESC")
    List<LinkedHashMap<String, Object>> followListByFollower(@Param("userId") Object userId);

    @Select("SELECT u.id, u.username, u.avatar, u.name, u.bio, u.area, f.created_at AS follow_time "
            + "FROM follow f JOIN user u ON u.id = f.follower_id "
            + "WHERE f.followee_id = #{userId} ORDER BY f.created_at DESC")
    List<LinkedHashMap<String, Object>> followListByFollowee(@Param("userId") Object userId);

    @Select("SELECT COUNT(*) AS c FROM follow WHERE follower_id = #{userId}")
    Long followCountByFollower(@Param("userId") Object userId);

    @Select("SELECT COUNT(*) AS c FROM follow WHERE followee_id = #{userId}")
    Long followCountByFollowee(@Param("userId") Object userId);

    // ===== 点赞 =====

    @Select("SELECT id FROM article_like WHERE article_id = #{articleId} AND user_id = #{userId} LIMIT 1")
    Object likeRowId(@Param("articleId") Object articleId, @Param("userId") Object userId);

    @Insert("INSERT INTO article_like (article_id, user_id) VALUES (#{articleId}, #{userId})")
    int likeAdd(@Param("articleId") Object articleId, @Param("userId") Object userId);

    @Delete("DELETE FROM article_like WHERE id = #{id}")
    int likeDeleteById(@Param("id") Object id);

    @Select("SELECT COUNT(*) AS c FROM article_like WHERE article_id = #{articleId}")
    Long likeCountByArticle(@Param("articleId") Object articleId);

    @Select("SELECT COUNT(*) AS c FROM article_like al JOIN article a ON a.article_id = al.article_id "
            + "WHERE a.user = #{userId}")
    Long likeCountReceived(@Param("userId") Object userId);

    @Select("<script>SELECT COUNT(*) AS total FROM article_like al "
            + "JOIN article a ON a.article_id = al.article_id JOIN user u ON u.id = al.user_id "
            + "WHERE 1=1"
            + "<if test='keyword != null'> AND (a.title LIKE #{keyword} OR u.username LIKE #{keyword})</if>"
            + "</script>")
    Long likeManageTotal(@Param("keyword") String keyword);

    @Select("<script>SELECT al.id, al.article_id, a.title AS article_title, a.user AS author_id, "
            + "u.username AS username, u.name AS nickname, al.created_at "
            + "FROM article_like al JOIN article a ON a.article_id = al.article_id "
            + "JOIN user u ON u.id = al.user_id "
            + "WHERE 1=1"
            + "<if test='keyword != null'> AND (a.title LIKE #{keyword} OR u.username LIKE #{keyword})</if> "
            + "ORDER BY al.created_at DESC LIMIT #{pageSize} OFFSET #{offset}</script>")
    List<LinkedHashMap<String, Object>> likeManageList(@Param("keyword") String keyword,
                                                       @Param("pageSize") int pageSize,
                                                       @Param("offset") int offset);

    @Delete("DELETE FROM article_like WHERE id = #{id}")
    int likeManageDelete(@Param("id") Object id);

    // ===== 收藏 =====

    @Select("SELECT id FROM article_favorite WHERE article_id = #{articleId} AND user_id = #{userId} LIMIT 1")
    Object favoriteRowId(@Param("articleId") Object articleId, @Param("userId") Object userId);

    @Insert("INSERT INTO article_favorite (article_id, user_id) VALUES (#{articleId}, #{userId})")
    int favoriteAdd(@Param("articleId") Object articleId, @Param("userId") Object userId);

    @Delete("DELETE FROM article_favorite WHERE id = #{id}")
    int favoriteDeleteById(@Param("id") Object id);

    @Select("SELECT COUNT(*) AS c FROM article_favorite WHERE article_id = #{articleId}")
    Long favoriteCountByArticle(@Param("articleId") Object articleId);

    @Select("SELECT a.article_id, a.title, a.content, a.status, a.user AS user_id, "
            + "u.username AS author_name, a.created_at, a.updated_at, af.created_at AS favorited_at, "
            + "GROUP_CONCAT(DISTINCT ac.category_id ORDER BY ac.category_id ASC) AS category_ids, "
            + "GROUP_CONCAT(DISTINCT ac.category_name ORDER BY ac.category_id ASC) AS category_names "
            + "FROM article_favorite af "
            + "JOIN article a ON a.article_id = af.article_id AND a.status = 1 "
            + "JOIN user u ON a.user = u.id "
            + "LEFT JOIN articleandcategory_middle acm ON acm.article_id = a.article_id "
            + "LEFT JOIN article_category ac ON ac.category_id = acm.category_id "
            + "WHERE af.user_id = #{userId} "
            + "GROUP BY a.article_id, a.title, a.content, a.status, a.user, u.username, a.created_at, "
            + "a.updated_at, af.created_at ORDER BY af.created_at DESC")
    List<LinkedHashMap<String, Object>> favoriteListByUser(@Param("userId") Object userId);

    @Select("<script>SELECT COUNT(*) AS total FROM article_favorite af "
            + "JOIN article a ON a.article_id = af.article_id JOIN user u ON u.id = af.user_id "
            + "WHERE 1=1"
            + "<if test='keyword != null'> AND (a.title LIKE #{keyword} OR u.username LIKE #{keyword})</if>"
            + "</script>")
    Long favoriteManageTotal(@Param("keyword") String keyword);

    @Select("<script>SELECT af.id, af.article_id, a.title AS article_title, a.user AS author_id, "
            + "u.username AS username, u.name AS nickname, af.created_at "
            + "FROM article_favorite af JOIN article a ON a.article_id = af.article_id "
            + "JOIN user u ON u.id = af.user_id "
            + "WHERE 1=1"
            + "<if test='keyword != null'> AND (a.title LIKE #{keyword} OR u.username LIKE #{keyword})</if> "
            + "ORDER BY af.created_at DESC LIMIT #{pageSize} OFFSET #{offset}</script>")
    List<LinkedHashMap<String, Object>> favoriteManageList(@Param("keyword") String keyword,
                                                           @Param("pageSize") int pageSize,
                                                           @Param("offset") int offset);

    @Delete("DELETE FROM article_favorite WHERE id = #{id}")
    int favoriteManageDelete(@Param("id") Object id);

    // ===== 互动通知（user_notification） =====

    /** 不给自己发通知（原版在 dao 里判 Number(userId) === Number(actorId)）。 */
    @Insert("INSERT INTO user_notification (user_id, actor_id, type, article_id, content) "
            + "VALUES (#{userId}, #{actorId}, #{type}, #{articleId}, #{content})")
    int notificationAdd(@Param("userId") Object userId, @Param("actorId") Object actorId,
                        @Param("type") String type, @Param("articleId") Object articleId,
                        @Param("content") String content);

    @Select("SELECT COUNT(*) AS total FROM user_notification WHERE user_id = #{userId}")
    Long notificationTotal(@Param("userId") Object userId);

    @Select("SELECT n.id, n.type, n.article_id, n.content, n.is_read, n.created_at, "
            + "u.username AS actor_username, u.name AS actor_name, u.avatar AS actor_avatar "
            + "FROM user_notification n JOIN user u ON u.id = n.actor_id "
            + "WHERE n.user_id = #{userId} ORDER BY n.created_at DESC "
            + "LIMIT #{pageSize} OFFSET #{offset}")
    List<LinkedHashMap<String, Object>> notificationList(@Param("userId") Object userId,
                                                         @Param("pageSize") int pageSize,
                                                         @Param("offset") int offset);

    @Select("SELECT COUNT(*) AS c FROM user_notification WHERE user_id = #{userId} AND is_read = 0")
    Long notificationUnreadCount(@Param("userId") Object userId);

    @Update("UPDATE user_notification SET is_read = 1, read_at = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId}")
    int notificationRead(@Param("id") Object id, @Param("userId") Object userId);

    @Update("UPDATE user_notification SET is_read = 1, read_at = NOW() "
            + "WHERE user_id = #{userId} AND is_read = 0")
    int notificationReadAll(@Param("userId") Object userId);

    // ===== 杂项查询（原版是 legacy 路由里的内联 SQL） =====

    @Select("SELECT id FROM user WHERE username = #{username} LIMIT 1")
    Object userIdByUsername(@Param("username") String username);

    @Select("SELECT article_id, user FROM article WHERE article_id = #{articleId}")
    LinkedHashMap<String, Object> articleOwner(@Param("articleId") Object articleId);

    @Select("SELECT username FROM user WHERE id = #{id}")
    String usernameById(@Param("id") Object id);
}
