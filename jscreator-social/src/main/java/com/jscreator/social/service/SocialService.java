package com.jscreator.social.service;

import com.jscreator.social.mapper.SocialMapper;
import com.jscreator.social.util.Js;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 关注 / 点赞 / 收藏 / 互动通知 / 后台点赞收藏管理，逐条对齐 Express 版
 * modules/social/social.service.ts（分支、文案、响应结构照抄）。
 */
@Service
public class SocialService {

    /** 原版 404 的触发条件：按 username 查不到 id。 */
    private static final String NO_USER = "用户不存在";
    private static final String NO_ARTICLE = "文章不存在";

    private final SocialMapper mapper;

    public SocialService(SocialMapper mapper) {
        this.mapper = mapper;
    }

    /** POST /social/follow/{username}（toggle） */
    public SResult toggleFollow(Object actorId, String username) {
        Object followeeId = mapper.userIdByUsername(username);
        if (followeeId == null) {
            return SResult.fail(404, NO_USER);
        }
        if (sameId(followeeId, actorId)) {
            return SResult.fail(400, "不能关注自己");
        }
        if (mapper.followRowId(actorId, followeeId) != null) {
            mapper.followRemove(actorId, followeeId);
            return SResult.ok("已取消关注", data("following", false));
        }
        mapper.followAdd(actorId, followeeId);
        notificationAdd(followeeId, actorId, "follow", null, "关注了你");
        return SResult.ok("关注成功", data("following", true));
    }

    /** 关注列表（公开） */
    public SResult followingByUsername(String username) {
        Object uid = mapper.userIdByUsername(username);
        if (uid == null) {
            return SResult.fail(404, NO_USER);
        }
        return SResult.ok("获取成功", data("list", mapper.followListByFollower(uid)));
    }

    /** 粉丝列表（公开） */
    public SResult followersByUsername(String username) {
        Object uid = mapper.userIdByUsername(username);
        if (uid == null) {
            return SResult.fail(404, NO_USER);
        }
        return SResult.ok("获取成功", data("list", mapper.followListByFollowee(uid)));
    }

    /** 社交统计（公开；viewerId 为空时 isFollowing 恒 false） */
    public SResult statsByUsername(String username, Object viewerId) {
        Object uid = mapper.userIdByUsername(username);
        if (uid == null) {
            return SResult.fail(404, NO_USER);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("following", mapper.followCountByFollower(uid));
        body.put("followers", mapper.followCountByFollowee(uid));
        body.put("liked", mapper.likeCountReceived(uid));
        boolean isFollowing = false;
        if (viewerId != null) {
            isFollowing = mapper.followRowId(viewerId, uid) != null;
        }
        body.put("isFollowing", isFollowing);
        return SResult.ok("获取成功", body);
    }

    /** POST /social/like/{articleId}（toggle + 通知作者） */
    public SResult toggleLike(Object actorId, Object articleId) {
        Map<String, Object> article = mapper.articleOwner(articleId);
        if (article == null) {
            return SResult.fail(404, NO_ARTICLE);
        }
        boolean liked;
        Object rowId = mapper.likeRowId(articleId, actorId);
        if (rowId != null) {
            mapper.likeDeleteById(rowId);
            liked = false;
        } else {
            mapper.likeAdd(articleId, actorId);
            liked = true;
        }
        if (liked) {
            // 原版这里取了用户名却没用上（actorName 仅作 fallback 保留），保持同样的一次查询
            mapper.usernameById(actorId);
            notificationAdd(article.get("user"), actorId, "like", Js.number(articleId),
                    "点赞了你的文章 #" + articleId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("liked", liked);
        body.put("count", mapper.likeCountByArticle(articleId));
        return SResult.ok(liked ? "点赞成功" : "已取消点赞", body);
    }

    /** GET /social/status?ids=（批量查询；原版响应没有 message 键） */
    public SResult statusBatch(Object actorId, Object idsRaw) {
        List<Object> likes = new ArrayList<>();
        List<Object> favorites = new ArrayList<>();
        if (Js.truthy(idsRaw)) {
            for (Object id : parseIdList(idsRaw)) {
                if (mapper.likeRowId(id, actorId) != null) {
                    likes.add(id);
                }
                if (mapper.favoriteRowId(id, actorId) != null) {
                    favorites.add(id);
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("likes", likes);
        body.put("favorites", favorites);
        return SResult.dataOnly(body);
    }

    /** POST /social/favorite/{articleId}（toggle + 通知作者） */
    public SResult toggleFavorite(Object actorId, Object articleId) {
        Map<String, Object> article = mapper.articleOwner(articleId);
        if (article == null) {
            return SResult.fail(404, NO_ARTICLE);
        }
        boolean favorited;
        Object rowId = mapper.favoriteRowId(articleId, actorId);
        if (rowId != null) {
            mapper.favoriteDeleteById(rowId);
            favorited = false;
        } else {
            mapper.favoriteAdd(articleId, actorId);
            favorited = true;
        }
        if (favorited) {
            notificationAdd(article.get("user"), actorId, "favorite", Js.number(articleId),
                    "收藏了你的文章 #" + articleId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("favorited", favorited);
        body.put("count", mapper.favoriteCountByArticle(articleId));
        return SResult.ok(favorited ? "收藏成功" : "已取消收藏", body);
    }

    /** GET /social/my-favorites */
    public SResult myFavorites(Object actorId) {
        List<LinkedHashMap<String, Object>> rows = mapper.favoriteListByUser(actorId);
        for (Map<String, Object> row : rows) {
            row.put("status", Js.tinyInt(row.get("status")));
            row.put("category_ids", splitIds(row.get("category_ids")));
            row.put("category_names", splitNames(row.get("category_names")));
        }
        return SResult.ok("获取成功", data("list", rows));
    }

    /** GET /social/notifications */
    public SResult notificationPage(Object actorId, Object page, Object pageSize) {
        int ps = Js.parseIntOrDefault(pageSize, 20);
        if (ps < 1) {
            ps = 20;
        }
        int p = Js.parseIntOrDefault(page, 1);
        if (p < 1) {
            p = 1;
        }
        int offset = (p - 1) * ps;
        List<LinkedHashMap<String, Object>> rows = mapper.notificationList(actorId, ps, offset);
        for (Map<String, Object> row : rows) {
            row.put("is_read", Js.tinyInt(row.get("is_read")));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("list", rows);
        body.put("total", mapper.notificationTotal(actorId));
        body.put("page", p);
        body.put("pageSize", ps);
        return SResult.ok("获取成功", body);
    }

    /** GET /social/notifications/unread-count */
    public SResult notificationUnread(Object actorId) {
        return SResult.ok("获取成功", data("count", mapper.notificationUnreadCount(actorId)));
    }

    /** POST /social/notifications/read */
    public SResult notificationMarkRead(Object actorId, Map<String, Object> body) {
        if (Js.truthy(body.get("all"))) {
            mapper.notificationReadAll(actorId);
        } else if (Js.truthy(body.get("id"))) {
            Double id = Js.number(body.get("id"));
            if (id == null) {
                // 原版把 Number('abc') = NaN 原样塞进 SQL，MySQL 报错 → 500「操作失败」；这里照抄这条路径
                throw new IllegalArgumentException("notification id 不是数字：" + body.get("id"));
            }
            mapper.notificationRead(jsonNumber(id), actorId);
        } else {
            return SResult.fail(400, "参数缺失");
        }
        return SResult.ok("已标记已读");
    }

    // ===== 后台管理（admin） =====

    public SResult adminLikes(Object page, Object pageSize, Object keyword) {
        int p = Js.parseIntOrDefault(page, 1);
        if (p < 1) {
            p = 1;
        }
        int ps = Js.parseIntOrDefault(pageSize, 10);
        if (ps < 1) {
            ps = 10;
        }
        int offset = (p - 1) * ps;
        String kw = likeKeyword(keyword);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("list", mapper.likeManageList(kw, ps, offset));
        body.put("total", mapper.likeManageTotal(kw));
        body.put("page", p);
        body.put("pageSize", ps);
        return SResult.ok("获取成功", body);
    }

    public SResult adminLikeDelete(Object id) {
        mapper.likeManageDelete(id);
        return SResult.ok("删除成功");
    }

    public SResult adminFavorites(Object page, Object pageSize, Object keyword) {
        int p = Js.parseIntOrDefault(page, 1);
        if (p < 1) {
            p = 1;
        }
        int ps = Js.parseIntOrDefault(pageSize, 10);
        if (ps < 1) {
            ps = 10;
        }
        int offset = (p - 1) * ps;
        String kw = likeKeyword(keyword);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("list", mapper.favoriteManageList(kw, ps, offset));
        body.put("total", mapper.favoriteManageTotal(kw));
        body.put("page", p);
        body.put("pageSize", ps);
        return SResult.ok("获取成功", body);
    }

    public SResult adminFavoriteDelete(Object id) {
        mapper.favoriteManageDelete(id);
        return SResult.ok("删除成功");
    }

    // ===== 内部工具 =====

    /** user_notification 写入；原版不给自己发通知。 */
    private void notificationAdd(Object userId, Object actorId, String type, Object articleId, String content) {
        if (sameId(userId, actorId)) {
            return;
        }
        mapper.notificationAdd(userId, actorId, type, articleId, content);
    }

    /** 原版 {@code parseInt(String(idsRaw), 10)} 的替代：{@code String(v).split(',').map(Number).filter(Boolean)} */
    private static List<Object> parseIdList(Object idsRaw) {
        List<Object> ids = new ArrayList<>();
        for (String part : Js.str(idsRaw).split(",", -1)) {
            Double n = Js.number(part);
            if (n != null && n != 0) {
                ids.add(jsonNumber(n));
            }
        }
        return ids;
    }

    private static String likeKeyword(Object keyword) {
        return Js.truthy(keyword) ? "%" + Js.str(keyword) + "%" : null;
    }

    /** GROUP_CONCAT 出来的数字串 → 数字数组；null / 空串 → []。 */
    private static List<Object> splitIds(Object v) {
        List<Object> out = new ArrayList<>();
        if (v == null) {
            return out;
        }
        String s = String.valueOf(v);
        if (s.isEmpty()) {
            return out;
        }
        for (String part : s.split(",")) {
            Double n = Js.number(part);
            out.add(n == null ? null : jsonNumber(n));
        }
        return out;
    }

    /** GROUP_CONCAT 出来的名字串 → 字符串数组；null / 空串 → []。 */
    private static List<Object> splitNames(Object v) {
        List<Object> out = new ArrayList<>();
        if (v == null) {
            return out;
        }
        String s = String.valueOf(v);
        if (s.isEmpty()) {
            return out;
        }
        for (String part : s.split(",", -1)) {
            out.add(part);
        }
        return out;
    }

    /** JS 数字进 JSON 时整数不带小数点（8 而不是 8.0）。 */
    private static Object jsonNumber(Double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e21) {
            return d.longValue();
        }
        return d;
    }

    private static boolean sameId(Object a, Object b) {
        Double na = Js.number(a);
        Double nb = Js.number(b);
        // JS 的 NaN === NaN 为 false，两边一致
        return na != null && nb != null && na.doubleValue() == nb.doubleValue();
    }

    private static Map<String, Object> data(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }
}
