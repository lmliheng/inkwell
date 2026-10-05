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

/** ad / announcement 表数据层，SQL 逐条对齐 Express 版 modules/content/content.dao.ts。 */
public interface ContentMapper {

    // ===== 广告 =====

    @Select("""
            SELECT id, title, type, image_url, text_title, text_desc, link_url, position, click_count
            FROM ad
            WHERE position = #{position} AND status = 1
            ORDER BY sort_order ASC, id ASC
            LIMIT 1
            """)
    LinkedHashMap<String, Object> adByPosition(@Param("position") String position);

    @Update("UPDATE ad SET click_count = click_count + 1 WHERE id = #{id}")
    int adIncrementClick(@Param("id") Object id);

    @Select("""
            <script>
            SELECT id, title, type, image_url, text_title, text_desc, link_url,
                    position, sort_order, status, click_count, created_at, updated_at
            FROM ad
            <where>
                <if test="keywordLike != null">title LIKE #{keywordLike}</if>
                <if test="position != null">AND position = #{position}</if>
            </where>
            ORDER BY sort_order ASC, id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<LinkedHashMap<String, Object>> adManageList(@Param("keywordLike") String keywordLike,
                                                     @Param("position") Object position,
                                                     @Param("pageSize") int pageSize,
                                                     @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) AS total FROM ad
            <where>
                <if test="keywordLike != null">title LIKE #{keywordLike}</if>
                <if test="position != null">AND position = #{position}</if>
            </where>
            </script>
            """)
    long adManageCount(@Param("keywordLike") String keywordLike, @Param("position") Object position);

    @Insert("""
            INSERT INTO ad (title, type, image_url, text_title, text_desc, link_url, position, sort_order, status)
            VALUES (#{title}, #{type}, #{imageUrl}, #{textTitle}, #{textDesc}, #{linkUrl}, #{position}, #{sortOrder}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int adAdd(Map<String, Object> params);

    @Update("""
            UPDATE ad
            SET title = #{title}, type = #{type}, image_url = #{imageUrl}, text_title = #{textTitle}, text_desc = #{textDesc},
                link_url = #{linkUrl}, position = #{position}, sort_order = #{sortOrder}, status = #{status}
            WHERE id = #{id}
            """)
    int adUpdate(Map<String, Object> params);

    @Update("UPDATE ad SET status = #{status} WHERE id = #{id}")
    int adSetStatus(@Param("id") Object id, @Param("status") Object status);

    @Delete("DELETE FROM ad WHERE id = #{id}")
    int adDelete(@Param("id") Object id);

    @Select("SELECT * FROM ad WHERE id = #{id}")
    LinkedHashMap<String, Object> adById(@Param("id") Object id);

    // ===== 公告 =====

    @Select("""
            SELECT id, title, content, created_at
            FROM announcement
            WHERE status = 1
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    LinkedHashMap<String, Object> announceLatest();

    @Select("""
            <script>
            SELECT id, title, content, status, created_at, updated_at
            FROM announcement
            <where>
                <if test="keywordLike != null">title LIKE #{keywordLike}</if>
                <if test="statusValue != null">AND status = #{statusValue}</if>
            </where>
            ORDER BY created_at DESC, id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<LinkedHashMap<String, Object>> announceManageList(@Param("keywordLike") String keywordLike,
                                                           @Param("statusValue") Object statusValue,
                                                           @Param("pageSize") int pageSize,
                                                           @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) AS total FROM announcement
            <where>
                <if test="keywordLike != null">title LIKE #{keywordLike}</if>
                <if test="statusValue != null">AND status = #{statusValue}</if>
            </where>
            </script>
            """)
    long announceManageCount(@Param("keywordLike") String keywordLike, @Param("statusValue") Object statusValue);

    @Insert("INSERT INTO announcement (title, content) VALUES (#{title}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int announceAdd(Map<String, Object> params);

    @Update("UPDATE announcement SET title = #{title}, content = #{content} WHERE id = #{id}")
    int announceUpdate(@Param("id") Object id, @Param("title") String title, @Param("content") Object content);

    @Update("UPDATE announcement SET status = #{status} WHERE id = #{id}")
    int announceSetStatus(@Param("id") Object id, @Param("status") Object status);

    @Delete("DELETE FROM announcement WHERE id = #{id}")
    int announceDelete(@Param("id") Object id);
}
