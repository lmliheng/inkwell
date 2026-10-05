package com.jscreator.social.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * message 表的 SQL，逐条对齐 Express 版 modules/dm/dm.dao.ts。
 */
public interface DmMapper {

    /** 两用户会话消息：倒序取最新一页（原版取完再 reverse，交给 service 做）。 */
    @Select("SELECT id, sender_id, receiver_id, content, is_read, created_at FROM message "
            + "WHERE (sender_id = #{userA} AND receiver_id = #{userB}) "
            + "OR (sender_id = #{userB} AND receiver_id = #{userA}) "
            + "ORDER BY id DESC LIMIT #{pageSize} OFFSET #{offset}")
    List<LinkedHashMap<String, Object>> conversation(@Param("userA") Object userA,
                                                     @Param("userB") Object userB,
                                                     @Param("pageSize") int pageSize,
                                                     @Param("offset") int offset);

    /** 会话列表：每会话最后一条 + 未读数。 */
    @Select("SELECT m.id, m.sender_id, m.receiver_id, m.content, m.created_at, "
            + "u.username AS other_username, u.name AS other_name, u.avatar AS other_avatar, "
            + "(SELECT COUNT(*) FROM message un WHERE un.receiver_id = #{userId} "
            + "AND un.sender_id = u.id AND un.is_read = 0) AS unread "
            + "FROM message m "
            + "JOIN (SELECT GREATEST(sender_id, receiver_id) AS a, LEAST(sender_id, receiver_id) AS b, "
            + "MAX(id) AS max_id FROM message WHERE sender_id = #{userId} OR receiver_id = #{userId} "
            + "GROUP BY a, b) latest ON m.id = latest.max_id "
            + "JOIN user u ON u.id = IF(m.sender_id = #{userId}, m.receiver_id, m.sender_id) "
            + "ORDER BY m.created_at DESC")
    List<LinkedHashMap<String, Object>> conversationList(@Param("userId") Object userId);

    @Select("SELECT COUNT(*) AS c FROM message WHERE receiver_id = #{userId} AND is_read = 0")
    Long unreadTotal(@Param("userId") Object userId);

    @Update("UPDATE message SET is_read = 1, read_at = NOW() "
            + "WHERE receiver_id = #{userId} AND sender_id = #{otherId} AND is_read = 0")
    int markRead(@Param("userId") Object userId, @Param("otherId") Object otherId);
}
