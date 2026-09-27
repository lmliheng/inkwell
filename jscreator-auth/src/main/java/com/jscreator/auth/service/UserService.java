package com.jscreator.auth.service;

import com.jscreator.auth.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户信息组装，对齐 Express 版 modules/user/user.service 的 profile：
 * user_detail 取联表第一行（剔除权限列）并附上文章/评论数；user_permission 是逐行的权限对。
 */
@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 查不到用户返回 null（controller 会翻成 code 401 文案）。 */
    public Map<String, Object> profile(Long userId) {
        List<LinkedHashMap<String, Object>> rows = userMapper.profileRows(userId);
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> permissions = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("permission_name", row.get("permission_name"));
            p.put("permission_id", row.get("permission_id"));
            permissions.add(p);
        }

        Map<String, Object> detail = new LinkedHashMap<>(rows.get(0));
        detail.remove("permission_name");
        detail.remove("permission_id");
        try {
            detail.put("article_count", userMapper.countPublishedArticles(userId));
            detail.put("comment_count", userMapper.countComments(userId));
        } catch (Exception e) {
            // 与原版一致：统计失败就置 0，不影响主页信息
            detail.put("article_count", 0);
            detail.put("comment_count", 0);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user_detail", detail);
        out.put("user_permission", permissions);
        return out;
    }
}
