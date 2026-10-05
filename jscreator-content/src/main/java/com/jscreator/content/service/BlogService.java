package com.jscreator.content.service;

import com.jscreator.common.exception.BizException;
import com.jscreator.content.mapper.BlogMapper;
import com.jscreator.content.util.ContentJs;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jscreator.content.util.ContentJs.parseIntOrDefault;
import static com.jscreator.content.util.ContentJs.str;
import static com.jscreator.content.util.ContentJs.truthy;

/**
 * 博客主页聚合业务，逐分支对齐 Express 版 modules/blog/blogProfile.service.ts
 * （featured 优先 / 最新兜底 + all_total）。
 */
@Service
public class BlogService {

    private final BlogMapper blogMapper;

    public BlogService(BlogMapper blogMapper) {
        this.blogMapper = blogMapper;
    }

    /** 有已发布文章的用户列表（默认 pageSize 24）。 */
    public Map<String, Object> users(Object pageRaw, Object pageSizeRaw) {
        int page = parseIntOrDefault(pageRaw, 1);
        int pageSize = parseIntOrDefault(pageSizeRaw, 24);
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 24;
        }
        long total = blogMapper.countUsersWithArticles();
        List<LinkedHashMap<String, Object>> rows = blogMapper.usersWithArticles(pageSize, (page - 1) * pageSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    /** 全站最新已发布文章（limit 缺省 6）——只是 data.list。 */
    public List<LinkedHashMap<String, Object>> latest(Object limitRaw) {
        int limit = parseIntOrDefault(limitRaw, 6);
        if (limit < 1) {
            limit = 6;
        }
        return shapeRows(blogMapper.latestArticles(limit));
    }

    /** 全站热议文章（评论数降序，limit 缺省 6）。 */
    public List<LinkedHashMap<String, Object>> hot(Object limitRaw) {
        int limit = parseIntOrDefault(limitRaw, 6);
        if (limit < 1) {
            limit = 6;
        }
        return shapeRows(blogMapper.hotArticles(limit));
    }

    /** 主页：用户公开信息 + 精选文章（无则最新）+ all_total。用户不存在 → 404。 */
    public Map<String, Object> profilePage(String username, Object pageRaw, Object pageSizeRaw) {
        LinkedHashMap<String, Object> user = userPublic(username);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        List<Object> featured = ContentJs.jsonArray(user.get("featured_articles"));
        Map<String, Object> articles;
        if (!featured.isEmpty()) {
            List<LinkedHashMap<String, Object>> list = featuredArticles(featured);
            Map<String, Object> shaped = new LinkedHashMap<>();
            shaped.put("list", list);
            shaped.put("total", (long) list.size());
            shaped.put("page", 1);
            shaped.put("pageSize", (long) list.size());
            articles = shaped;
        } else {
            articles = articlesByUsername(username, pageRaw, pageSizeRaw, null, null, null);
        }
        long allTotal;
        try {
            allTotal = countArticles(username, null, null);
        } catch (Exception e) {
            // 与「最新兜底」那支保持一致，total 缺失时回落 0
            allTotal = ContentJs.num(articles.get("total"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", user);
        data.put("articles", articles);
        data.put("all_total", allTotal);
        return data;
    }

    /** 用户文章分页（翻页/加载更多）。用户不存在 → 404。 */
    public Map<String, Object> userArticles(String username, Object pageRaw, Object pageSizeRaw,
                                            Object keyword, Object categoryId, Object sort) {
        if (userPublic(username) == null) {
            throw BizException.notFound("用户不存在");
        }
        return articlesByUsername(username, pageRaw, pageSizeRaw, keyword, categoryId, sort);
    }

    // ================= 内部 =================

    /** 公开字段行：socials / featured_articles 两个 JSON 列解析成数组。 */
    private LinkedHashMap<String, Object> userPublic(String username) {
        LinkedHashMap<String, Object> row = blogMapper.userPublicByUsername(username);
        if (row == null) {
            return null;
        }
        row.put("socials", ContentJs.jsonArray(row.get("socials")));
        row.put("featured_articles", ContentJs.jsonArray(row.get("featured_articles")));
        return row;
    }

    private Map<String, Object> articlesByUsername(String username, Object pageRaw, Object pageSizeRaw,
                                                   Object keyword, Object categoryId, Object sort) {
        int page = parseIntOrDefault(pageRaw, 1);
        int pageSize = parseIntOrDefault(pageSizeRaw, 10);
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        String keywordLike = truthy(keyword) ? "%" + str(keyword) + "%" : null;
        Object category = truthy(categoryId) ? categoryId : null;
        String orderBy = "asc".equals(str(sort)) ? "a.created_at ASC" : "a.created_at DESC";

        long total = countArticles(username, keywordLike, category);
        List<LinkedHashMap<String, Object>> rows = shapeRows(
                blogMapper.articlesByUsername(username, keywordLike, category, orderBy, pageSize, (page - 1) * pageSize));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    private long countArticles(String username, String keywordLike, Object categoryId) {
        return blogMapper.countArticlesByUsername(username, keywordLike, categoryId);
    }

    /** 原版 attachCategoryArrays（blog 侧只处理分类）。 */
    private static List<LinkedHashMap<String, Object>> shapeRows(List<LinkedHashMap<String, Object>> rows) {
        for (LinkedHashMap<String, Object> row : rows) {
            row.put("status", ContentJs.tinyInt(row.get("status")));
            if (row.containsKey("category_ids")) {
                row.put("category_ids", ContentJs.splitInts(row.get("category_ids")));
                row.put("category_names", ContentJs.splitStrings(row.get("category_names")));
            }
            if (row.containsKey("comment_count")) {
                row.put("comment_count", ContentJs.num(row.get("comment_count")));
            }
            if (row.containsKey("article_count")) {
                row.put("article_count", ContentJs.num(row.get("article_count")));
            }
        }
        return rows;
    }

    /**
     * 原版 getArticlesByIds：去重（保留首次出现的顺序）后按 id 查询，输出保持传入顺序，缺失的丢掉。
     */
    public List<LinkedHashMap<String, Object>> featuredArticles(List<Object> ids) {
        Set<Object> unique = new LinkedHashSet<>();
        for (Object id : ids) {
            Double n = ContentJs.number(id);
            if (n == null || n == 0 || n.isNaN() || n.isInfinite()) {
                continue;
            }
            unique.add(n == Math.floor(n) ? (Object) (long) n.doubleValue() : (Object) n);
        }
        if (unique.isEmpty()) {
            return new ArrayList<>();
        }
        List<LinkedHashMap<String, Object>> rows = shapeRows(blogMapper.articlesByIds(new ArrayList<>(unique)));
        Map<String, LinkedHashMap<String, Object>> byId = new LinkedHashMap<>();
        for (LinkedHashMap<String, Object> row : rows) {
            byId.put(str(row.get("article_id")), row);
        }
        List<LinkedHashMap<String, Object>> out = new ArrayList<>();
        for (Object id : unique) {
            LinkedHashMap<String, Object> row = byId.get(str(id));
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }
}
