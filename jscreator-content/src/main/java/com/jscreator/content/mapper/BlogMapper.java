package com.jscreator.content.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 博客主页公开数据（user 公开字段 + 文章聚合），SQL 逐条对齐 Express 版
 * modules/blog/blogProfile.dao.ts。
 */
public interface BlogMapper {

    /** 按 username 查公开信息（不含密码/email）。 */
    @Select("""
            SELECT id, username, avatar, bio, name, area, vip, created_at, socials, featured_articles, github_id
            FROM user WHERE username = #{username}
            """)
    LinkedHashMap<String, Object> userPublicByUsername(@Param("username") String username);

    /** 主页精选：按传入 id 顺序返回已发布文章。 */
    @Select("""
            <script>
            SELECT a.article_id, a.title, a.content, a.status, a.user AS user_id,
                    u.username AS author_name, a.created_at, a.updated_at,
                    GROUP_CONCAT(DISTINCT ac.category_id ORDER BY ac.category_id ASC) AS category_ids,
                    GROUP_CONCAT(DISTINCT ac.category_name ORDER BY ac.category_id ASC) AS category_names
            FROM article a
            JOIN user u ON a.user = u.id
            LEFT JOIN articleandcategory_middle acm ON acm.article_id = a.article_id
            LEFT JOIN article_category ac ON ac.category_id = acm.category_id
            WHERE a.article_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            AND a.status = 1
            GROUP BY a.article_id, a.title, a.content, a.status, a.user, u.username, a.created_at, a.updated_at
            </script>
            """)
    List<LinkedHashMap<String, Object>> articlesByIds(@Param("ids") List<Object> ids);

    /** 某用户名下已发布文章分页（keyword / category_id / sort）。 */
    @Select("""
            <script>
            SELECT a.article_id, a.title, a.content, a.status, a.user AS user_id,
                    u.username AS author_name, a.created_at, a.updated_at,
                    GROUP_CONCAT(DISTINCT ac.category_id ORDER BY ac.category_id ASC) AS category_ids,
                    GROUP_CONCAT(DISTINCT ac.category_name ORDER BY ac.category_id ASC) AS category_names
            FROM article a
            JOIN user u ON a.user = u.id
            LEFT JOIN articleandcategory_middle acm ON acm.article_id = a.article_id
            LEFT JOIN article_category ac ON ac.category_id = acm.category_id
            WHERE u.username = #{username} AND a.status = 1
            <if test="keywordLike != null">AND a.title LIKE #{keywordLike}</if>
            <if test="categoryId != null">
                AND EXISTS (SELECT 1 FROM articleandcategory_middle acm2
                    WHERE acm2.article_id = a.article_id AND acm2.category_id = #{categoryId})
            </if>
            GROUP BY a.article_id, a.title, a.content, a.status, a.user, u.username, a.created_at, a.updated_at
            ORDER BY ${orderBy}
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<LinkedHashMap<String, Object>> articlesByUsername(@Param("username") String username,
                                                           @Param("keywordLike") String keywordLike,
                                                           @Param("categoryId") Object categoryId,
                                                           @Param("orderBy") String orderBy,
                                                           @Param("pageSize") int pageSize,
                                                           @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) AS total
            FROM article a JOIN user u ON a.user = u.id
            WHERE u.username = #{username} AND a.status = 1
            <if test="keywordLike != null">AND a.title LIKE #{keywordLike}</if>
            <if test="categoryId != null">
                AND EXISTS (SELECT 1 FROM articleandcategory_middle acm2
                    WHERE acm2.article_id = a.article_id AND acm2.category_id = #{categoryId})
            </if>
            </script>
            """)
    long countArticlesByUsername(@Param("username") String username,
                                 @Param("keywordLike") String keywordLike,
                                 @Param("categoryId") Object categoryId);

    /** 有已发布文章的用户列表（文章数降序）。 */
    @Select("SELECT COUNT(DISTINCT u.id) AS total FROM user u JOIN article a ON a.user = u.id AND a.status = 1")
    long countUsersWithArticles();

    @Select("""
            SELECT u.id, u.username, u.avatar, u.bio, u.name, u.area, u.vip, u.created_at,
                    COUNT(a.article_id) AS article_count
            FROM user u
            JOIN article a ON a.user = u.id AND a.status = 1
            GROUP BY u.id, u.username, u.avatar, u.bio, u.name, u.area, u.vip, u.created_at
            ORDER BY article_count DESC, u.created_at DESC
            LIMIT #{pageSize} OFFSET #{offset}
            """)
    List<LinkedHashMap<String, Object>> usersWithArticles(@Param("pageSize") int pageSize,
                                                          @Param("offset") int offset);

    /** 全站最新已发布文章。 */
    @Select("""
            SELECT a.article_id, a.title, a.content, a.status, a.user AS user_id,
                    u.username AS author_name, u.name AS author_nick, u.avatar AS author_avatar,
                    a.created_at, a.updated_at,
                    GROUP_CONCAT(DISTINCT ac.category_id ORDER BY ac.category_id ASC) AS category_ids,
                    GROUP_CONCAT(DISTINCT ac.category_name ORDER BY ac.category_id ASC) AS category_names
            FROM article a
            JOIN user u ON a.user = u.id
            LEFT JOIN articleandcategory_middle acm ON acm.article_id = a.article_id
            LEFT JOIN article_category ac ON ac.category_id = acm.category_id
            WHERE a.status = 1
            GROUP BY a.article_id, a.title, a.content, a.status, a.user, u.username, u.name, u.avatar, a.created_at, a.updated_at
            ORDER BY a.created_at DESC
            LIMIT #{limit}
            """)
    List<LinkedHashMap<String, Object>> latestArticles(@Param("limit") int limit);

    /** 全站热议文章（评论数降序）。 */
    @Select("""
            SELECT a.article_id, a.title, a.content, a.status, a.user AS user_id,
                    u.username AS author_name, u.name AS author_nick, u.avatar AS author_avatar,
                    a.created_at, a.updated_at,
                    COUNT(DISTINCT c.comment_id) AS comment_count,
                    GROUP_CONCAT(DISTINCT ac.category_id ORDER BY ac.category_id ASC) AS category_ids,
                    GROUP_CONCAT(DISTINCT ac.category_name ORDER BY ac.category_id ASC) AS category_names
            FROM article a
            JOIN user u ON a.user = u.id
            LEFT JOIN comment c ON c.article_id = a.article_id
            LEFT JOIN articleandcategory_middle acm ON acm.article_id = a.article_id
            LEFT JOIN article_category ac ON ac.category_id = acm.category_id
            WHERE a.status = 1
            GROUP BY a.article_id, a.title, a.content, a.status, a.user, u.username, u.name, u.avatar, a.created_at, a.updated_at
            ORDER BY comment_count DESC, a.created_at DESC
            LIMIT #{limit}
            """)
    List<LinkedHashMap<String, Object>> hotArticles(@Param("limit") int limit);
}
