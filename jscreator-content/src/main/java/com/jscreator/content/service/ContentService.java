package com.jscreator.content.service;

import com.jscreator.common.exception.BizException;
import com.jscreator.content.mapper.ContentMapper;
import com.jscreator.content.util.ContentJs;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.jscreator.content.util.ContentJs.parseIntOrDefault;
import static com.jscreator.content.util.ContentJs.str;
import static com.jscreator.content.util.ContentJs.truthy;

/** 广告 / 公告业务，逐分支对齐 Express 版 modules/content/content.controller.ts。 */
@Service
public class ContentService {

    /** 原版 AD_POSITIONS 白名单。 */
    private static final List<String> AD_POSITIONS = List.of("article_top", "article_bottom", "home_mid");

    private final ContentMapper contentMapper;

    public ContentService(ContentMapper contentMapper) {
        this.contentMapper = contentMapper;
    }

    // ================= 广告 =================

    public Map<String, Object> adSlots(String position) {
        if (!truthy(position) || !AD_POSITIONS.contains(position)) {
            throw BizException.badRequest("无效的广告位");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ad", contentMapper.adByPosition(position));
        return data;
    }

    public void adClick(Object idRaw) {
        Double id = ContentJs.number(idRaw);
        if (id == null || id.isNaN() || id == 0) {
            throw BizException.badRequest("无效的广告");
        }
        contentMapper.adIncrementClick(id);
    }

    public Map<String, Object> adManageList(Object pageRaw, Object pageSizeRaw, Object keyword, Object position) {
        int page = parseIntOrDefault(pageRaw, 1);
        int pageSize = parseIntOrDefault(pageSizeRaw, 10);
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        String keywordLike = truthy(keyword) ? "%" + str(keyword) + "%" : null;
        Object positionFilter = truthy(position) ? position : null;
        long total = contentMapper.adManageCount(keywordLike, positionFilter);
        List<LinkedHashMap<String, Object>> rows =
                contentMapper.adManageList(keywordLike, positionFilter, pageSize, (page - 1) * pageSize);
        for (LinkedHashMap<String, Object> row : rows) {
            row.put("status", ContentJs.tinyInt(row.get("status")));
            row.put("sort_order", ContentJs.num(row.get("sort_order")));
            row.put("click_count", ContentJs.num(row.get("click_count")));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    /** 详情：原版把路径里的原始字符串直接丢进 SQL。 */
    public LinkedHashMap<String, Object> adDetail(String idRaw) {
        LinkedHashMap<String, Object> ad = contentMapper.adById(idRaw);
        if (ad == null) {
            throw BizException.notFound("广告不存在");
        }
        ad.put("status", ContentJs.tinyInt(ad.get("status")));
        ad.put("sort_order", ContentJs.num(ad.get("sort_order")));
        ad.put("click_count", ContentJs.num(ad.get("click_count")));
        return ad;
    }

    public Map<String, Object> adAdd(Map<String, Object> body) {
        String title = str(nullish(body, "title", ""));
        if (!truthy(title)) {
            throw BizException.badRequest("广告标题不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", nullish(body, "title", null));
        params.put("type", nullish(body, "type", "image"));
        params.put("imageUrl", nullish(body, "image_url", ""));
        params.put("textTitle", nullish(body, "text_title", ""));
        params.put("textDesc", nullish(body, "text_desc", ""));
        params.put("linkUrl", nullish(body, "link_url", ""));
        params.put("position", nullish(body, "position", "article_top"));
        params.put("sortOrder", numberOrZero(nullish(body, "sort_order", 0)));
        params.put("status", ContentJs.truthyFlag(nullish(body, "status", 1)));
        contentMapper.adAdd(params);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", ContentJs.num(params.get("id")));
        return data;
    }

    public void adUpdate(Object idRaw, Map<String, Object> body) {
        Double id = ContentJs.number(idRaw);
        if (id == null || id.isNaN() || id == 0) {
            throw BizException.badRequest("无效的广告");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        // 原版 update 的 title 没有默认值：缺了就写 NULL，靠表的 NOT NULL 约束报错
        params.put("title", body.get("title"));
        params.put("type", nullish(body, "type", "image"));
        params.put("imageUrl", nullish(body, "image_url", ""));
        params.put("textTitle", nullish(body, "text_title", ""));
        params.put("textDesc", nullish(body, "text_desc", ""));
        params.put("linkUrl", nullish(body, "link_url", ""));
        params.put("position", nullish(body, "position", "article_top"));
        params.put("sortOrder", numberOrZero(nullish(body, "sort_order", 0)));
        params.put("status", ContentJs.truthyFlag(nullish(body, "status", 1)));
        contentMapper.adUpdate(params);
    }

    public void adSetStatus(Object idRaw, Map<String, Object> body) {
        Double id = ContentJs.number(idRaw);
        if (id == null || id.isNaN() || id == 0 || !body.containsKey("status")) {
            throw BizException.badRequest("参数缺失");
        }
        contentMapper.adSetStatus(id, ContentJs.truthyFlag(body.get("status")));
    }

    public void adDelete(Object idRaw) {
        Double id = ContentJs.number(idRaw);
        if (id == null || id.isNaN() || id == 0) {
            throw BizException.badRequest("无效的广告");
        }
        contentMapper.adDelete(id);
    }

    // ================= 公告 =================

    public Map<String, Object> announceLatest() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("announcement", contentMapper.announceLatest());
        return data;
    }

    public Map<String, Object> announceManageList(Object pageRaw, Object pageSizeRaw, Object keyword, Object statusRaw) {
        int page = parseIntOrDefault(pageRaw, 1);
        int pageSize = parseIntOrDefault(pageSizeRaw, 10);
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        String keywordLike = truthy(keyword) ? "%" + str(keyword) + "%" : null;
        // 原版只认字符串 '0' / '1'
        Object statusValue = null;
        if ("0".equals(statusRaw) || "1".equals(statusRaw)) {
            statusValue = Integer.valueOf(String.valueOf(statusRaw));
        }
        long total = contentMapper.announceManageCount(keywordLike, statusValue);
        List<LinkedHashMap<String, Object>> rows =
                contentMapper.announceManageList(keywordLike, statusValue, pageSize, (page - 1) * pageSize);
        for (LinkedHashMap<String, Object> row : rows) {
            row.put("status", ContentJs.tinyInt(row.get("status")));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    public Map<String, Object> announceAdd(Map<String, Object> body) {
        String title = truthy(body.get("title")) ? str(body.get("title")) : "";
        String content = body.get("content") != null ? str(body.get("content")) : "";
        if (!truthy(title) || title.trim().isEmpty()) {
            throw BizException.badRequest("公告标题不能为空");
        }
        if (!truthy(content) || content.trim().isEmpty()) {
            throw BizException.badRequest("公告内容不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", title.trim());
        params.put("content", content);
        contentMapper.announceAdd(params);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", ContentJs.num(params.get("id")));
        return data;
    }

    public void announceUpdate(Object idRaw, Map<String, Object> body) {
        Double id = ContentJs.number(idRaw);
        String title = truthy(body.get("title")) ? str(body.get("title")) : "";
        String content = body.get("content") != null ? str(body.get("content")) : "";
        if (id == null || id.isNaN() || id == 0 || !truthy(title) || !truthy(content)) {
            throw BizException.badRequest("参数缺失");
        }
        contentMapper.announceUpdate(id, title.trim(), content);
    }

    public void announceSetStatus(Object idRaw, Map<String, Object> body) {
        Double id = ContentJs.number(idRaw);
        if (id == null || id.isNaN() || id == 0 || !body.containsKey("status")) {
            throw BizException.badRequest("参数缺失");
        }
        contentMapper.announceSetStatus(id, ContentJs.truthyFlag(body.get("status")));
    }

    public void announceDelete(Object idRaw) {
        Double id = ContentJs.number(idRaw);
        if (id == null || id.isNaN() || id == 0) {
            throw BizException.badRequest("无效的公告");
        }
        contentMapper.announceDelete(id);
    }

    /** JS 的 {@code v ?? def}：键不存在或值为 null 时取默认值。 */
    private static Object nullish(Map<String, Object> body, String key, Object def) {
        Object v = body.get(key);
        return v == null ? def : v;
    }

    /** JS 的 {@code Number(v) || 0}（sort_order 落库用的就是它）。 */
    private static Object numberOrZero(Object v) {
        Double n = ContentJs.number(v);
        if (n == null || n.isNaN() || n == 0) {
            return 0.0;
        }
        return n;
    }
}
