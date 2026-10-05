package com.jscreator.social.mapper;

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
 * 平台广播通知 notification / notification_read 的 SQL，逐条对齐 Express 版
 * modules/notification/notification.dao.ts（即 utils/db_notification.js）。
 */
public interface NotificationMapper {

    /** 插入广播通知，自增主键回填到 {@code row.id}（原版取 result.insertId）。 */
    @Insert("INSERT INTO notification (title, content, type, importance, sender_id, target_type, target_id) "
            + "VALUES (#{title}, #{content}, #{type}, #{importance}, #{senderId}, #{targetType}, #{targetId})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "notification_id")
    int notificationAdd(BroadcastRow row);

    /** 用户可见通知列表（含已读状态）——规则同 legacy。 */
    @Select("SELECT n.notification_id, n.title, n.content, n.type, n.importance, n.sender_id, n.target_type, "
            + "n.target_id, n.created_at, COALESCE(nr.is_read, 0) AS is_read "
            + "FROM notification n "
            + "LEFT JOIN notification_read nr ON nr.notification_id = n.notification_id AND nr.user_id = #{userId} "
            + "WHERE n.target_type = 'all' "
            + "OR (n.target_type = 'user' AND n.target_id = #{userId}) "
            + "OR (n.target_type = 'role' AND n.target_id = (SELECT role_id FROM user WHERE id = #{userId})) "
            + "ORDER BY n.created_at DESC")
    List<LinkedHashMap<String, Object>> notificationForUser(@Param("userId") Object userId);

    @Select("SELECT COUNT(*) AS unread_count FROM notification n "
            + "LEFT JOIN notification_read nr ON nr.notification_id = n.notification_id AND nr.user_id = #{userId} "
            + "WHERE (nr.id IS NULL OR nr.is_read = 0) "
            + "AND (n.target_type = 'all' "
            + "OR (n.target_type = 'user' AND n.target_id = #{userId}) "
            + "OR (n.target_type = 'role' AND n.target_id = (SELECT role_id FROM user WHERE id = #{userId})))")
    Long notificationUnreadCount(@Param("userId") Object userId);

    @Insert("INSERT INTO notification_read (notification_id, user_id, is_read, read_at) "
            + "VALUES (#{notificationId}, #{userId}, 1, NOW()) "
            + "ON DUPLICATE KEY UPDATE is_read = 1, read_at = NOW()")
    int notificationMarkRead(@Param("notificationId") Object notificationId, @Param("userId") Object userId);

    /**
     * 动态字段更新。原版按「请求体里出现过该键」拼 SET 片段（值为 null 也照写），
     * 这里把片段在 service 侧从固定白名单拼好传进来，值仍走 #{} 绑定。
     *
     * @param clauses 形如 {@code title = #{fields.title}} 的片段列表，非空
     */
    @Update("<script>UPDATE notification SET "
            + "<foreach item='clause' collection='clauses' separator=','>${clause}</foreach>"
            + " WHERE notification_id = #{notificationId}</script>")
    int notificationUpdate(@Param("clauses") List<String> clauses,
                           @Param("fields") Map<String, Object> fields,
                           @Param("notificationId") Object notificationId);

    @Delete("DELETE FROM notification WHERE notification_id = #{notificationId}")
    int notificationRemove(@Param("notificationId") Object notificationId);

    /** 广播通知插入参数（自增主键回填到这里）。 */
    class BroadcastRow {
        private Long id;
        private String title;
        private String content;
        private String type;
        private String importance;
        private Object senderId;
        private String targetType;
        private Object targetId;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getImportance() {
            return importance;
        }

        public void setImportance(String importance) {
            this.importance = importance;
        }

        public Object getSenderId() {
            return senderId;
        }

        public void setSenderId(Object senderId) {
            this.senderId = senderId;
        }

        public String getTargetType() {
            return targetType;
        }

        public void setTargetType(String targetType) {
            this.targetType = targetType;
        }

        public Object getTargetId() {
            return targetId;
        }

        public void setTargetId(Object targetId) {
            this.targetId = targetId;
        }
    }
}
