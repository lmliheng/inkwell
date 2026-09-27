package com.jscreator.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jscreator.auth.mapper.OpenApiMapper;
import com.jscreator.auth.util.OauthOpenapiJs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jscreator.auth.util.OauthOpenapiJs.parseIntOrDefault;
import static com.jscreator.auth.util.OauthOpenapiJs.str;
import static com.jscreator.auth.util.OauthOpenapiJs.truthy;

/**
 * 开放 API v1 业务（/api/v1/*），对齐 Express 版 modules/openapi/openApi.service +
 * article.dao.list/detail、blogProfile.dao.getUserPublicByUsername 的取数与行整形。
 *
 * <p>跨域只读：文章 / 用户数据同库，直接 SQL 查，不改其它服务的代码。
 *
 * <p>与 Node 版的一处已知差异：原版发布文章后「不阻塞响应」地调 DeepSeek 生成 AI 摘要
 * （失败也吞掉），本服务没有 LLM 依赖（AI 摘要在 M5 才移植），因此不写 article.ai_summary；
 * 接口响应与原文案一致，不受影响。
 */
@Service
public class OpenApiService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final OpenApiMapper openApiMapper;

    public OpenApiService(OpenApiMapper openApiMapper) {
        this.openApiMapper = openApiMapper;
    }

    /** 公开文章列表，返回与原版一致的 {list, total, page, pageSize}。 */
    public Map<String, Object> list(Object pageRaw, Object pageSizeRaw, Object keywordRaw, Object categoryRaw) {
        int page = parseIntOrDefault(pageRaw, 1);
        int pageSize = parseIntOrDefault(pageSizeRaw, 10);
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        int offset = (page - 1) * pageSize;
        // 原版：if (filter.keyword) { LIKE '%kw%' } —— 空串不参与过滤
        String keywordLike = truthy(keywordRaw) ? "%" + str(keywordRaw) + "%" : null;
        // 原版把 query 里的字符串原样塞进 SQL
        Object categoryId = truthy(categoryRaw) ? str(categoryRaw) : null;

        List<LinkedHashMap<String, Object>> rows = openApiMapper.listArticles(pageSize, offset, keywordLike, categoryId);
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (LinkedHashMap<String, Object> row : rows) {
            list.add(shapeListRow(row));
        }
        long total = openApiMapper.countArticles(keywordLike, categoryId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    /** 列表行整形，对齐 article.dao.attachCategoryArrays + 计数归一。 */
    private static LinkedHashMap<String, Object> shapeListRow(LinkedHashMap<String, Object> row) {
        row.put("status", OauthOpenapiJs.tinyInt(row.get("status")));
        row.put("category_ids", splitInts(row.get("category_ids")));
        row.put("category_names", splitStrings(row.get("category_names")));
        row.put("like_count", num(row.get("like_count")));
        row.put("favorite_count", num(row.get("favorite_count")));
        return row;
    }

    /** 详情：不存在或未发布返回 null（对应原版 'notfound' → 404）。 */
    public LinkedHashMap<String, Object> detail(long articleId) {
        LinkedHashMap<String, Object> article = openApiMapper.articleDetail(articleId);
        if (article == null) {
            return null;
        }
        Integer status = OauthOpenapiJs.tinyInt(article.get("status"));
        if (status == null || status != 1) {
            return null;
        }
        article.put("status", status);
        Object aiSummary = article.get("ai_summary");
        if (aiSummary != null) {
            // mysql2 会把 JSON 列解析成对象；这里把字符串 parse 回来（失败按原版置 null）
            article.put("ai_summary", parseJson(aiSummary));
        }
        List<LinkedHashMap<String, Object>> categories = openApiMapper.articleCategories(articleId);
        List<Object> categoryIds = new ArrayList<>(categories.size());
        List<Object> categoryNames = new ArrayList<>(categories.size());
        for (LinkedHashMap<String, Object> c : categories) {
            categoryIds.add(c.get("category_id"));
            categoryNames.add(c.get("category_name"));
        }
        article.put("category_ids", categoryIds);
        article.put("category_names", categoryNames);
        return article;
    }

    /** 用户公开信息：不存在返回 null（对应原版 'notfound' → 404）。 */
    public LinkedHashMap<String, Object> user(String username) {
        LinkedHashMap<String, Object> row = openApiMapper.userPublicByUsername(username);
        if (row == null) {
            return null;
        }
        // 原版 parseJson：已经是数组就原样返回，null / 非数组 → []
        row.put("socials", parseJsonArray(row.get("socials")));
        row.put("featured_articles", parseJsonArray(row.get("featured_articles")));
        return row;
    }

    /**
     * 发布文章（write scope）：插 article + 分类中间表（一个事务，同 article.dao.add），
     * 返回 {@code {article_id}}。
     *
     * @param hasStatus body 里是否显式带了 status（原版用 undefined 判定，null 会被 Number(null)＝0）
     */
    @Transactional
    public Map<String, Object> publish(long userId, String title, String content, Object categoryIds, Object statusRaw,
                                       boolean hasStatus) {
        int status = hasStatus ? numOrZero(statusRaw) : 1;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", title);
        params.put("content", content);
        params.put("userId", userId);
        params.put("status", status);
        openApiMapper.insertArticle(params);
        Object articleId = params.get("articleId");
        for (Object categoryId : distinctCategoryIds(categoryIds)) {
            openApiMapper.insertArticleCategory(articleId, categoryId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("article_id", num(articleId));
        return data;
    }

    /** 原版 setCategories：去重 + Number() + 丢掉 0/NaN。 */
    private static Set<Object> distinctCategoryIds(Object raw) {
        Set<Object> out = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object v : list) {
                Double n = OauthOpenapiJs.number(v);
                if (n == null || n == 0 || n.isNaN()) {
                    continue;
                }
                out.add(n == Math.floor(n) ? (Object) n.longValue() : (Object) n);
            }
        }
        return out;
    }

    private static long num(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return v == null ? 0L : Long.parseLong(String.valueOf(v));
    }

    private static int numOrZero(Object v) {
        Double n = OauthOpenapiJs.number(v);
        return n == null || n.isNaN() ? 0 : n.intValue();
    }

    private static List<Integer> splitInts(Object v) {
        List<Integer> out = new ArrayList<>();
        if (truthy(v)) {
            for (String part : str(v).split(",")) {
                Double n = OauthOpenapiJs.number(part);
                if (n != null && !n.isNaN()) {
                    out.add(n.intValue());
                }
            }
        }
        return out;
    }

    private static List<String> splitStrings(Object v) {
        List<String> out = new ArrayList<>();
        if (truthy(v)) {
            out.addAll(List.of(str(v).split(",")));
        }
        return out;
    }

    /** JSON 列 → 对象（解析失败按原版处理成 null）。 */
    private static Object parseJson(Object raw) {
        String text = raw instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : str(raw);
        try {
            return JSON.readValue(text, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** JSON 列 → 数组（原版 parseJson：非数组、空值、解析失败都是 []）。 */
    private static List<Object> parseJsonArray(Object raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        Object parsed = parseJson(raw);
        if (parsed instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }
}
