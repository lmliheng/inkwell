package com.jscreator.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放 API（/api/v1/*）读写的 article / user 数据层，SQL 逐条对齐 Express 版
 * article.dao.list / detail、blogProfile.dao.getUserPublicByUsername（跨域只读，同一套库，直接查）。
 *
 * <p>保持与文章服务同一份 SQL：列、别名、排序、GROUP BY 都照抄，这样 Java 版与 Node 版出来的
 * JSON 键集合与顺序一致。status 是 tinyint(1)，出库后在 service 里归一成数字。
 */
public interface OpenApiMapper {

    /** 公开文章列表：分页 + 关键词 + 分类（status 固定 1，开放 API 只暴露已发布）。 */
    @Select("""
            <script>
            SELECT a.article_id, a.title, a.content, a.status, a.user AS user_id,
                    COALESCE(NULLIF(u.name, ''), u.username) AS author_name, a.created_at, a.updated_at,
                    COUNT(DISTINCT al.id) AS like_count,
                    COUNT(DISTINCT af.id) AS favorite_count,
                    GROUP_CONCAT(DISTINCT ac.category_id ORDER BY ac.category_id ASC) AS category_ids,
                    GROUP_CONCAT(DISTINCT ac.category_name ORDER BY ac.category_id ASC) AS category_names
            FROM article a
            LEFT JOIN user u ON a.user = u.id
            LEFT JOIN article_like al ON al.article_id = a.article_id
            LEFT JOIN article_favorite af ON af.article_id = a.article_id
            LEFT JOIN articleandcategory_middle acm ON acm.article_id = a.article_id
            LEFT JOIN article_category ac ON ac.category_id = acm.category_id
            WHERE a.status = 1
            <if test="keywordLike != null">AND (a.title LIKE #{keywordLike} OR a.content LIKE #{keywordLike})</if>
            <if test="categoryId != null">AND EXISTS (SELECT 1 FROM articleandcategory_middle acm WHERE acm.article_id = a.article_id AND acm.category_id = #{categoryId})</if>
            GROUP BY a.article_id, a.title, a.content, a.status, a.user, COALESCE(NULLIF(u.name, ''), u.username), a.created_at, a.updated_at
            ORDER BY a.created_at DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<LinkedHashMap<String, Object>> listArticles(@Param("pageSize") int pageSize,
                                                     @Param("offset") int offset,
                                                     @Param("keywordLike") String keywordLike,
                                                     @Param("categoryId") Object categoryId);

    /** 列表同条件的计数（原版 count 单独一条 SQL）。 */
    @Select("""
            <script>
            SELECT COUNT(DISTINCT a.article_id) AS total
            FROM article a
            WHERE a.status = 1
            <if test="keywordLike != null">AND (a.title LIKE #{keywordLike} OR a.content LIKE #{keywordLike})</if>
            <if test="categoryId != null">AND EXISTS (SELECT 1 FROM articleandcategory_middle acm WHERE acm.article_id = a.article_id AND acm.category_id = #{categoryId})</if>
            </script>
            """)
    long countArticles(@Param("keywordLike") String keywordLike, @Param("categoryId") Object categoryId);

    @Select("""
            SELECT a.article_id, a.title, a.content, a.status, a.user AS user_id,
                    COALESCE(NULLIF(u.name, ''), u.username) AS author_name,
                    u.username AS author_username,
                    u.avatar AS author_avatar,
                    u.bio AS author_bio,
                    a.ai_summary,
                    a.created_at, a.updated_at
            FROM article a LEFT JOIN user u ON a.user = u.id
            WHERE a.article_id = #{articleId}
            """)
    LinkedHashMap<String, Object> articleDetail(@Param("articleId") Object articleId);

    @Select("SELECT ac.category_id, ac.category_name "
            + "FROM articleandcategory_middle acm JOIN article_category ac ON ac.category_id = acm.category_id "
            + "WHERE acm.article_id = #{articleId} ORDER BY ac.category_id ASC")
    List<LinkedHashMap<String, Object>> articleCategories(@Param("articleId") Object articleId);

    /** 新增文章：插入 article（自增 id 回填到参数 map 的 articleId 键，等价原版 result.insertId）。 */
    @Insert("INSERT INTO article (title, content, user, status) VALUES (#{title}, #{content}, #{userId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "articleId")
    int insertArticle(Map<String, Object> params);

    @Insert("INSERT INTO articleandcategory_middle (article_id, category_id) VALUES (#{articleId}, #{categoryId})")
    int insertArticleCategory(@Param("articleId") Object articleId, @Param("categoryId") Object categoryId);

    /** 用户公开信息（不含 password / email），JSON 列在 service 里解析。 */
    @Select("SELECT id, username, avatar, bio, name, area, vip, created_at, socials, featured_articles, github_id "
            + "FROM user WHERE username = #{username}")
    LinkedHashMap<String, Object> userPublicByUsername(@Param("username") String username);
}
