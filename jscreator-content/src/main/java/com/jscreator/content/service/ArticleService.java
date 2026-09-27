package com.jscreator.content.service;

import com.jscreator.common.exception.BizException;
import com.jscreator.content.mapper.ArticleMapper;
import com.jscreator.content.util.ContentJs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 文章/分类业务，逐分支对齐 Express 版 modules/article/article.service.ts（可见性、归属权限、AI 总结触发）。
 */
@Service
public class ArticleService {

    /** 原版 isAdminOrEditor 的白名单（兼容两套角色编号）。 */
    private static final Set<String> ADMIN_EDITOR_ROLES = Set.of("admin", "editor", "超级管理员", "编辑");

    private final ArticleMapper articleMapper;
    private final AiSummaryService aiSummaryService;

    public ArticleService(ArticleMapper articleMapper, AiSummaryService aiSummaryService) {
        this.articleMapper = articleMapper;
        this.aiSummaryService = aiSummaryService;
    }

    // ================= 权限 =================

    public boolean isAdminOrEditor(Object userId) {
        LinkedHashMap<String, Object> role = articleMapper.userRoleById(userId);
        if (role == null) {
            return false;
        }
        return ADMIN_EDITOR_ROLES.contains(ContentJs.trim(ContentJs.strOrEmpty(role.get("role_name"))));
    }

    // ================= 列表 / 归档 =================

    public Map<String, Object> list(Object pageRaw, Object pageSizeRaw, Object categoryId, Object keyword,
                                    Object statusRaw, Object authorRaw) {
        int page = parseIntOrDefault(pageRaw, 1);
        int pageSize = parseIntOrDefault(pageSizeRaw, 10);
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        int offset = (page - 1) * pageSize;

        boolean allStatus;
        Object statusValue;
        if (statusRaw == null || String.valueOf(statusRaw).isEmpty()) {
            allStatus = false;
            statusValue = 1;
        } else if (!"all".equals(String.valueOf(statusRaw))) {
            allStatus = false;
            // 原版是 a.status = Number(status)：非法值会拼出 NaN 让 SQL 报错（用例覆盖，见 content.py）
            Double n = ContentJs.number(statusRaw);
            if (n == null || n.isNaN() || n.isInfinite()) {
                throw new IllegalArgumentException("status 不是数字: " + statusRaw);
            }
            statusValue = n;
        } else {
            allStatus = true;
            statusValue = null;
        }

        String keywordLike = truthy(keyword) ? "%" + str(keyword) + "%" : null;
        String authorLike = truthy(authorRaw) ? "%" + str(authorRaw) + "%" : null;
        Object category = truthy(categoryId) ? categoryId : null;

        List<LinkedHashMap<String, Object>> rows =
                articleMapper.listArticles(allStatus, statusValue, keywordLike, authorLike, category, pageSize, offset);
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (LinkedHashMap<String, Object> row : rows) {
            list.add(shapeRow(row, true));
        }
        long total = articleMapper.countList(allStatus, statusValue, keywordLike, authorLike, category);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    /** 归档：公开文章平铺（username 可为 null / 空，等价原版不传）。 */
    public List<LinkedHashMap<String, Object>> archive(String username) {
        return articleMapper.archive(truthy(username) ? username : null);
    }

    // ================= 详情 =================

    /** 详情（含可见性判断）：不存在或（未发布且非作者/admin）→ 404。 */
    public LinkedHashMap<String, Object> detailForViewer(double articleId, Long viewerId) {
        LinkedHashMap<String, Object> article = detail(articleId);
        if (article == null) {
            throw BizException.notFound("文章不存在");
        }
        Object status = article.get("status");
        if (!ContentJs.sameId(status, 1)) {
            boolean allowed = viewerId != null
                    && (ContentJs.sameId(article.get("user_id"), viewerId) || isAdminOrEditor(viewerId));
            if (!allowed) {
                throw BizException.notFound("文章不存在");
            }
        }
        return article;
    }

    /** 原版 dao.detail：详情行 + 分类数组 + ai_summary 解析。 */
    public LinkedHashMap<String, Object> detail(Object articleId) {
        LinkedHashMap<String, Object> article = articleMapper.detailById(articleId);
        if (article == null) {
            return null;
        }
        Object aiSummary = article.get("ai_summary");
        if (truthy(aiSummary)) {
            // mysql2 会把 JSON 列解析成对象；这里把字符串 parse 回来（失败按原版置 null）
            article.put("ai_summary", ContentJs.parseJson(aiSummary));
        }
        List<LinkedHashMap<String, Object>> cats = articleMapper.categoriesOfArticle(articleId);
        List<Object> categoryIds = new ArrayList<>(cats.size());
        List<Object> categoryNames = new ArrayList<>(cats.size());
        for (LinkedHashMap<String, Object> c : cats) {
            categoryIds.add(c.get("category_id"));
            categoryNames.add(c.get("category_name"));
        }
        article.put("category_ids", categoryIds);
        article.put("category_names", categoryNames);
        article.put("status", ContentJs.tinyInt(article.get("status")));
        return article;
    }

    // ================= 增删改 =================

    /** 新增文章；返回 {@code {article_id}}。status 缺省为 1（发布），发布时后台触发生成 AI 总结。 */
    @Transactional
    public Map<String, Object> create(Object userId, String title, String content, Object categoryIds,
                                      boolean hasStatus, Object statusRaw) {
        Double statusNumber = hasStatus ? ContentJs.number(statusRaw) : 1.0;
        if (statusNumber == null || statusNumber.isNaN() || statusNumber.isInfinite()) {
            // 原版拼出 status = NaN 让 SQL 报错
            throw new IllegalArgumentException("status 不是数字: " + statusRaw);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", title);
        params.put("content", content);
        params.put("userId", userId);
        params.put("status", statusNumber);
        articleMapper.insertArticle(params);
        Object articleId = params.get("articleId");
        for (Object categoryId : distinctCategoryIds(categoryIds)) {
            articleMapper.insertArticleCategory(articleId, categoryId);
        }
        if (statusNumber.doubleValue() == 1.0) {
            aiSummaryService.summarizeAndSaveAsync(articleId, title, content);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("article_id", ContentJs.num(articleId));
        return data;
    }

    /** 更新文章（作者本人或 admin/editor）。 */
    @Transactional
    public Map<String, Object> updateAs(Object actorId, double articleId, Map<String, Object> body) {
        LinkedHashMap<String, Object> article = articleMapper.articleRawById(articleId);
        if (article == null) {
            throw BizException.notFound("文章不存在");
        }
        if (!ContentJs.sameId(article.get("user"), actorId) && !isAdminOrEditor(actorId)) {
            throw BizException.forbidden("无权限操作该文章");
        }

        boolean hasTitle = body.containsKey("title");
        boolean hasContent = body.containsKey("content");
        boolean hasStatus = body.containsKey("status");
        String title = hasTitle ? str(body.get("title")) : null;
        String content = hasContent ? str(body.get("content")) : null;
        Double statusNumber = null;
        if (hasStatus) {
            statusNumber = ContentJs.number(body.get("status"));
            if (statusNumber == null || statusNumber.isNaN() || statusNumber.isInfinite()) {
                throw new IllegalArgumentException("status 不是数字: " + body.get("status"));
            }
        }

        if (hasTitle || hasContent || hasStatus) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("articleId", articleId);
            params.put("hasTitle", hasTitle);
            params.put("hasContent", hasContent);
            params.put("hasStatus", hasStatus);
            params.put("title", title);
            params.put("content", content);
            params.put("status", statusNumber);
            articleMapper.updateArticle(params);
        }
        if (body.containsKey("category_ids")) {
            articleMapper.deleteArticleCategories(articleId);
            for (Object categoryId : distinctCategoryIds(body.get("category_ids"))) {
                articleMapper.insertArticleCategory(articleId, categoryId);
            }
        }

        Object nextStatus = hasStatus ? statusNumber : article.get("status");
        if (ContentJs.sameId(nextStatus, 1)) {
            String nextTitle = hasTitle ? title : str(article.get("title"));
            String nextContent = hasContent ? content : str(article.get("content"));
            aiSummaryService.summarizeAndSaveAsync(articleId, nextTitle, nextContent);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("article_id", articleId);
        return data;
    }

    /** 删除文章（作者本人或 admin/editor）：先清评论再删文章。 */
    @Transactional
    public Map<String, Object> removeAs(Object actorId, double articleId) {
        LinkedHashMap<String, Object> article = articleMapper.articleRawById(articleId);
        if (article == null) {
            throw BizException.notFound("文章不存在");
        }
        if (!ContentJs.sameId(article.get("user"), actorId) && !isAdminOrEditor(actorId)) {
            throw BizException.forbidden("无权限操作该文章");
        }
        articleMapper.deleteCommentsOfArticle(articleId);
        articleMapper.deleteArticle(articleId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("article_id", articleId);
        return data;
    }

    public Map<String, Object> mine(Object userId, Object pageRaw, Object pageSizeRaw) {
        int page = parseIntOrDefault(pageRaw, 1);
        int pageSize = parseIntOrDefault(pageSizeRaw, 10);
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        long total = articleMapper.countMine(userId);
        List<LinkedHashMap<String, Object>> rows = articleMapper.mine(userId, pageSize, (page - 1) * pageSize);
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (LinkedHashMap<String, Object> row : rows) {
            list.add(shapeRow(row, false));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    /** 手动重新生成 AI 总结（作者本人或 admin/editor）。返回新的 ai_summary。 */
    public Object regenerateAiSummary(Object actorId, double articleId) {
        LinkedHashMap<String, Object> article = detail(articleId);
        if (article == null) {
            throw BizException.notFound("文章不存在");
        }
        if (!ContentJs.sameId(article.get("user_id"), actorId) && !isAdminOrEditor(actorId)) {
            throw BizException.forbidden("无权限操作该文章");
        }
        Map<String, Object> result = aiSummaryService.summarizeAndSave(articleId,
                str(article.get("title")), str(article.get("content")));
        if (result == null) {
            throw new BizException(500, "AI 总结生成失败，请重试或检查 LLM 配置");
        }
        return result;
    }

    // ================= 分类 =================

    public List<LinkedHashMap<String, Object>> categoryList() {
        return articleMapper.categoryAll();
    }

    /** 新增分类：仅 admin/editor。 */
    public Map<String, Object> categoryAdd(Object actorId, String categoryName) {
        if (!isAdminOrEditor(actorId)) {
            throw BizException.forbidden("权限不足，仅管理员或编辑可创建分类");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("categoryName", categoryName);
        params.put("userId", actorId);
        articleMapper.insertCategory(params);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category_name", categoryName);
        return data;
    }

    /** 更新分类：作者本人或 admin/editor。 */
    public Map<String, Object> categoryUpdate(Object actorId, String categoryId, String categoryName) {
        LinkedHashMap<String, Object> category = articleMapper.categoryById(categoryId);
        if (category == null) {
            throw BizException.notFound("分类不存在");
        }
        boolean isOwner = ContentJs.sameId(category.get("user"), actorId);
        if (!isOwner && !isAdminOrEditor(actorId)) {
            throw BizException.forbidden("无权限操作该分类");
        }
        if (isOwner) {
            articleMapper.updateCategoryOwn(categoryId, categoryName, actorId);
        } else {
            articleMapper.updateCategoryAny(categoryId, categoryName);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category_id", categoryId);
        return data;
    }

    /** 删除分类：作者本人或 admin/editor。 */
    public Map<String, Object> categoryDelete(Object actorId, String categoryId) {
        LinkedHashMap<String, Object> category = articleMapper.categoryById(categoryId);
        if (category == null) {
            throw BizException.notFound("分类不存在");
        }
        boolean isOwner = ContentJs.sameId(category.get("user"), actorId);
        if (!isOwner && !isAdminOrEditor(actorId)) {
            throw BizException.forbidden("无权限操作该分类");
        }
        if (isOwner) {
            articleMapper.deleteCategoryOwn(categoryId, actorId);
        } else {
            articleMapper.deleteCategoryAny(categoryId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category_id", categoryId);
        return data;
    }

    // ================= 行整形 =================

    /** 原版 attachCategoryArrays + 计数归一；withCounts 对应 list（有 like/favorite 计数）。 */
    private static Map<String, Object> shapeRow(LinkedHashMap<String, Object> row, boolean withCounts) {
        row.put("status", ContentJs.tinyInt(row.get("status")));
        row.put("category_ids", ContentJs.splitInts(row.get("category_ids")));
        row.put("category_names", ContentJs.splitStrings(row.get("category_names")));
        if (withCounts) {
            row.put("like_count", ContentJs.num(row.get("like_count")));
            row.put("favorite_count", ContentJs.num(row.get("favorite_count")));
        }
        return row;
    }

    /** 原版 setCategories：去重 + Number() + 丢掉 0/NaN（保持首次出现顺序）。 */
    private static Set<Object> distinctCategoryIds(Object raw) {
        Set<Object> out = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object v : list) {
                Double n = ContentJs.number(v);
                if (n == null || n == 0 || n.isNaN() || n.isInfinite()) {
                    continue;
                }
                out.add(n == Math.floor(n) ? (Object) (long) n.doubleValue() : (Object) n);
            }
        }
        return out;
    }
}
