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

/**
 * article / article_category / articleandcategory_middle 数据层，SQL 逐条对齐 Express 版
 * modules/article/article.dao.ts（含列顺序、别名、GROUP BY、排序）。
 *
 * <p>status 是 tinyint(1)，出库后在 service 里归一成数字（MySQL Connector/J 会取成 Boolean）。
 */
public interface ArticleMapper {

    /** isAdminOrEditor 用的角色行（原版 getUserRoleById）。 */
    @Select("""
            SELECT u.id, u.username, u.role_id, r.role_name
            FROM user u LEFT JOIN role r ON u.role_id = r.role_id
            WHERE u.id = #{userId}
            """)
    LinkedHashMap<String, Object> userRoleById(@Param("userId") Object userId);

    // ================= 列表 =================

    /** 列表分页：page/pageSize 已按原版 parseInt 规则归一，offset 由调用方算好。 */
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
            <where>
                <choose>
                    <when test="allStatus">1=1</when>
                    <otherwise>a.status = #{statusValue}</otherwise>
                </choose>
                <if test="keywordLike != null">AND (a.title LIKE #{keywordLike} OR a.content LIKE #{keywordLike})</if>
                <if test="authorLike != null">
                    AND EXISTS (SELECT 1 FROM user u2 WHERE u2.id = a.user
                        AND (u2.name LIKE #{authorLike} OR u2.username LIKE #{authorLike}))
                </if>
                <if test="categoryId != null">
                    AND EXISTS (SELECT 1 FROM articleandcategory_middle acm2
                        WHERE acm2.article_id = a.article_id AND acm2.category_id = #{categoryId})
                </if>
            </where>
            GROUP BY a.article_id, a.title, a.content, a.status, a.user, COALESCE(NULLIF(u.name, ''), u.username), a.created_at, a.updated_at
            ORDER BY a.created_at DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<LinkedHashMap<String, Object>> listArticles(@Param("allStatus") boolean allStatus,
                                                     @Param("statusValue") Object statusValue,
                                                     @Param("keywordLike") String keywordLike,
                                                     @Param("authorLike") String authorLike,
                                                     @Param("categoryId") Object categoryId,
                                                     @Param("pageSize") int pageSize,
                                                     @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(DISTINCT a.article_id) AS total
            FROM article a
            <where>
                <choose>
                    <when test="allStatus">1=1</when>
                    <otherwise>a.status = #{statusValue}</otherwise>
                </choose>
                <if test="keywordLike != null">AND (a.title LIKE #{keywordLike} OR a.content LIKE #{keywordLike})</if>
                <if test="authorLike != null">
                    AND EXISTS (SELECT 1 FROM user u2 WHERE u2.id = a.user
                        AND (u2.name LIKE #{authorLike} OR u2.username LIKE #{authorLike}))
                </if>
                <if test="categoryId != null">
                    AND EXISTS (SELECT 1 FROM articleandcategory_middle acm2
                        WHERE acm2.article_id = a.article_id AND acm2.category_id = #{categoryId})
                </if>
            </where>
            </script>
            """)
    long countList(@Param("allStatus") boolean allStatus,
                   @Param("statusValue") Object statusValue,
                   @Param("keywordLike") String keywordLike,
                   @Param("authorLike") String authorLike,
                   @Param("categoryId") Object categoryId);

    // ================= 详情 / 单行 =================

    @Select("SELECT * FROM article WHERE article_id = #{articleId}")
    LinkedHashMap<String, Object> articleRawById(@Param("articleId") Object articleId);

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
    LinkedHashMap<String, Object> detailById(@Param("articleId") Object articleId);

    @Select("""
            SELECT ac.category_id, ac.category_name
            FROM articleandcategory_middle acm
            JOIN article_category ac ON ac.category_id = acm.category_id
            WHERE acm.article_id = #{articleId}
            ORDER BY ac.category_id ASC
            """)
    List<LinkedHashMap<String, Object>> categoriesOfArticle(@Param("articleId") Object articleId);

    // ================= 我的文章 / 归档 =================

    @Select("SELECT COUNT(*) AS total FROM article WHERE user = #{userId}")
    long countMine(@Param("userId") Object userId);

    @Select("""
            SELECT a.article_id, a.title, a.content, a.status, a.user AS user_id,
                    COALESCE(NULLIF(u.name, ''), u.username) AS author_name, a.created_at, a.updated_at,
                    GROUP_CONCAT(DISTINCT ac.category_id ORDER BY ac.category_id ASC) AS category_ids,
                    GROUP_CONCAT(DISTINCT ac.category_name ORDER BY ac.category_id ASC) AS category_names
            FROM article a
            LEFT JOIN user u ON a.user = u.id
            LEFT JOIN articleandcategory_middle acm ON acm.article_id = a.article_id
            LEFT JOIN article_category ac ON ac.category_id = acm.category_id
            WHERE a.user = #{userId}
            GROUP BY a.article_id, a.title, a.content, a.status, a.user, COALESCE(NULLIF(u.name, ''), u.username), a.created_at, a.updated_at
            ORDER BY a.created_at DESC
            LIMIT #{pageSize} OFFSET #{offset}
            """)
    List<LinkedHashMap<String, Object>> mine(@Param("userId") Object userId,
                                             @Param("pageSize") int pageSize,
                                             @Param("offset") int offset);

    @Select("""
            <script>
            SELECT a.article_id, a.title, a.created_at,
                    COALESCE(NULLIF(u.name, ''), u.username) AS author_name,
                    u.username AS author_username,
                    GROUP_CONCAT(DISTINCT ac.category_name ORDER BY ac.category_id ASC) AS category_names
            FROM article a
            LEFT JOIN user u ON a.user = u.id
            LEFT JOIN articleandcategory_middle acm ON acm.article_id = a.article_id
            LEFT JOIN article_category ac ON ac.category_id = acm.category_id
            WHERE a.status = 1
            <if test="username != null">AND u.username = #{username}</if>
            GROUP BY a.article_id, a.title, a.created_at, COALESCE(NULLIF(u.name, ''), u.username), u.username
            ORDER BY a.created_at DESC
            </script>
            """)
    List<LinkedHashMap<String, Object>> archive(@Param("username") String username);

    // ================= 写 =================

    /** 新增文章（自增 id 回填到参数 map 的 articleId 键，等价原版 result.insertId）。 */
    @Insert("INSERT INTO article (title, content, user, status) VALUES (#{title}, #{content}, #{userId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "articleId")
    int insertArticle(Map<String, Object> params);

    @Insert("INSERT INTO articleandcategory_middle (article_id, category_id) VALUES (#{articleId}, #{categoryId})")
    int insertArticleCategory(@Param("articleId") Object articleId, @Param("categoryId") Object categoryId);

    @Delete("DELETE FROM articleandcategory_middle WHERE article_id = #{articleId}")
    int deleteArticleCategories(@Param("articleId") Object articleId);

    /** 更新文章的动态 SET（原版按字段可选拼 SQL）。 */
    @Update("""
            <script>
            UPDATE article
            <set>
                <if test="hasTitle">title = #{title},</if>
                <if test="hasContent">content = #{content},</if>
                <if test="hasStatus">status = #{status},</if>
            </set>
            WHERE article_id = #{articleId}
            </script>
            """)
    int updateArticle(Map<String, Object> params);

    @Delete("DELETE FROM comment WHERE article_id = #{articleId}")
    int deleteCommentsOfArticle(@Param("articleId") Object articleId);

    @Delete("DELETE FROM article WHERE article_id = #{articleId}")
    int deleteArticle(@Param("articleId") Object articleId);

    @Update("UPDATE article SET ai_summary = #{summary} WHERE article_id = #{articleId}")
    int updateAiSummary(@Param("articleId") Object articleId, @Param("summary") String summary);

    // ================= 分类 =================

    @Select("SELECT * FROM article_category WHERE category_id = #{categoryId}")
    LinkedHashMap<String, Object> categoryById(@Param("categoryId") Object categoryId);

    @Select("""
            SELECT c.category_id, c.category_name, c.created_at, c.updated_at, c.user,
                    COALESCE(NULLIF(u.name, ''), u.username) AS author_name
            FROM article_category c
            LEFT JOIN user u ON u.id = c.user
            ORDER BY c.category_id ASC
            """)
    List<LinkedHashMap<String, Object>> categoryAll();

    @Insert("INSERT INTO article_category (category_name, user) VALUES (#{categoryName}, #{userId})")
    @Options(useGeneratedKeys = true, keyProperty = "categoryId")
    int insertCategory(Map<String, Object> params);

    @Update("UPDATE article_category SET category_name = #{categoryName} WHERE category_id = #{categoryId} AND user = #{userId}")
    int updateCategoryOwn(@Param("categoryId") Object categoryId, @Param("categoryName") String categoryName,
                          @Param("userId") Object userId);

    @Update("UPDATE article_category SET category_name = #{categoryName} WHERE category_id = #{categoryId}")
    int updateCategoryAny(@Param("categoryId") Object categoryId, @Param("categoryName") String categoryName);

    @Delete("DELETE FROM article_category WHERE category_id = #{categoryId} AND user = #{userId}")
    int deleteCategoryOwn(@Param("categoryId") Object categoryId, @Param("userId") Object userId);

    @Delete("DELETE FROM article_category WHERE category_id = #{categoryId}")
    int deleteCategoryAny(@Param("categoryId") Object categoryId);
}
