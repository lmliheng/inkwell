package com.jscreator.content.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** comment 表数据层，SQL 逐条对齐 Express 版 modules/comment/comment.dao.ts。 */
public interface CommentMapper {

    @Select("SELECT username FROM user WHERE id = #{userId}")
    String usernameById(@Param("userId") Object userId);

    @Select("SELECT * FROM comment WHERE comment_id = #{commentId}")
    LinkedHashMap<String, Object> commentById(@Param("commentId") Object commentId);

    @Insert("INSERT INTO comment (article_id, user_id, nickname, content, parent_id) "
            + "VALUES (#{articleId}, #{userId}, #{nickname}, #{content}, #{parentId})")
    @Options(useGeneratedKeys = true, keyProperty = "commentId")
    int insertComment(Map<String, Object> params);

    /** 文章作者 id（原版 SELECT `user` FROM article ...，用于 is_author 标志）。 */
    @Select("SELECT `user` FROM article WHERE article_id = #{articleId}")
    Long articleAuthor(@Param("articleId") Object articleId);

    @Select("""
            SELECT c.comment_id, c.article_id, c.user_id, c.nickname, c.content, c.parent_id, c.created_at,
                    COALESCE(NULLIF(u.name, ''), u.username) AS display_name
            FROM comment c
            LEFT JOIN user u ON u.id = c.user_id
            WHERE c.article_id = #{articleId}
            ORDER BY c.created_at ASC, c.comment_id ASC
            """)
    List<LinkedHashMap<String, Object>> commentsOfArticle(@Param("articleId") Object articleId);

    /** 管理端列表（分页 + 按文章/关键词，含文章标题）。 */
    @Select("""
            <script>
            SELECT c.comment_id, c.article_id, c.user_id, c.nickname, c.content, c.parent_id, c.created_at,
                    a.title AS article_title,
                    COALESCE(NULLIF(u.name, ''), u.username) AS display_name
            FROM comment c
            LEFT JOIN article a ON a.article_id = c.article_id
            LEFT JOIN user u ON u.id = c.user_id
            <where>
                <if test="articleId != null">c.article_id = #{articleId}</if>
                <if test="keywordLike != null">AND (c.content LIKE #{keywordLike} OR c.nickname LIKE #{keywordLike})</if>
            </where>
            ORDER BY c.comment_id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<LinkedHashMap<String, Object>> manageList(@Param("articleId") Object articleId,
                                                   @Param("keywordLike") String keywordLike,
                                                   @Param("pageSize") int pageSize,
                                                   @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) AS total FROM comment c
            <where>
                <if test="articleId != null">c.article_id = #{articleId}</if>
                <if test="keywordLike != null">AND (c.content LIKE #{keywordLike} OR c.nickname LIKE #{keywordLike})</if>
            </where>
            </script>
            """)
    long manageCount(@Param("articleId") Object articleId, @Param("keywordLike") String keywordLike);

    @Update("""
            <script>
            UPDATE comment
            <set>
                <if test="hasContent">content = #{content},</if>
                <if test="hasNickname">nickname = #{nickname},</if>
            </set>
            WHERE comment_id = #{commentId}
            </script>
            """)
    int updateComment(Map<String, Object> params);

    /** 级联删除时按父 id 找下一层。 */
    @Select("""
            <script>
            SELECT comment_id FROM comment WHERE parent_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Long> childIds(@Param("ids") List<Object> ids);

    @Delete("""
            <script>
            DELETE FROM comment WHERE comment_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteByIds(@Param("ids") List<Object> ids);
}
