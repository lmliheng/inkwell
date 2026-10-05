package com.jscreator.content.service;

import com.jscreator.common.exception.BizException;
import com.jscreator.content.mapper.ArticleMapper;
import com.jscreator.content.mapper.CommentMapper;
import com.jscreator.content.util.ContentJs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.jscreator.content.util.ContentJs.parseIntOrDefault;
import static com.jscreator.content.util.ContentJs.str;

/**
 * 评论业务，逐分支对齐 Express 版 modules/comment/comment.service.ts
 * （创建校验序列、管理列表/更新/级联删除）。
 */
@Service
public class CommentService {

    private static final int MAX_CHILDREN = 50;

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;

    public CommentService(CommentMapper commentMapper, ArticleMapper articleMapper) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
    }

    /** 文章评论树（公开）：顶层分页 + 楼中楼嵌套。 */
    public Map<String, Object> listByArticle(Object articleId, Object pageRaw, Object pageSizeRaw) {
        Long articleAuthorId = commentMapper.articleAuthor(articleId);
        List<LinkedHashMap<String, Object>> rows = commentMapper.commentsOfArticle(articleId);

        Map<Long, LinkedHashMap<String, Object>> map = new LinkedHashMap<>();
        List<LinkedHashMap<String, Object>> roots = new ArrayList<>();
        for (LinkedHashMap<String, Object> row : rows) {
            LinkedHashMap<String, Object> node = new LinkedHashMap<>();
            node.put("comment_id", row.get("comment_id"));
            node.put("article_id", row.get("article_id"));
            node.put("user_id", row.get("user_id"));
            // 显示名：登录用户用 name 优先，无则 username；匿名评论用存的昵称
            Object userId = row.get("user_id");
            Object displayName = row.get("display_name");
            Object nickname = row.get("nickname");
            if (userId != null && displayName != null) {
                nickname = displayName;
            }
            node.put("nickname", nickname);
            node.put("content", row.get("content"));
            node.put("parent_id", row.get("parent_id"));
            node.put("created_at", row.get("created_at"));
            node.put("children", new ArrayList<LinkedHashMap<String, Object>>());
            node.put("is_author", userId != null && ContentJs.sameId(userId, articleAuthorId));
            map.put(ContentJs.num(row.get("comment_id")), node);
        }
        for (LinkedHashMap<String, Object> row : rows) {
            Object parentId = row.get("parent_id");
            LinkedHashMap<String, Object> node = map.get(ContentJs.num(row.get("comment_id")));
            LinkedHashMap<String, Object> parent = parentId == null ? null : map.get(ContentJs.num(parentId));
            if (parent != null) {
                @SuppressWarnings("unchecked")
                List<LinkedHashMap<String, Object>> children = (List<LinkedHashMap<String, Object>>) parent.get("children");
                children.add(node);
            } else {
                roots.add(node);
            }
        }
        for (LinkedHashMap<String, Object> root : roots) {
            @SuppressWarnings("unchecked")
            List<LinkedHashMap<String, Object>> children = (List<LinkedHashMap<String, Object>>) root.get("children");
            if (children.size() > MAX_CHILDREN) {
                root.put("children", new ArrayList<>(children.subList(children.size() - MAX_CHILDREN, children.size())));
            }
        }
        long total = roots.size();
        int page = Math.max(1, parseIntOrDefault(pageRaw, 1));
        int size = Math.max(1, parseIntOrDefault(pageSizeRaw, 20));
        int from = Math.min((page - 1) * size, roots.size());
        int to = Math.min(page * size, roots.size());
        List<LinkedHashMap<String, Object>> list = new ArrayList<>(roots.subList(from, to));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", size);
        return data;
    }

    /** 发表评论（actorId 可空=匿名）。校验顺序与文案照抄原版。 */
    @Transactional
    public Map<String, Object> create(Object articleIdRaw, Object contentRaw, Object parentIdRaw,
                                      Object nicknameRaw, Long actorId) {
        if (!ContentJs.truthy(articleIdRaw) || !ContentJs.truthy(contentRaw)) {
            throw BizException.badRequest("文章id和评论内容不能为空");
        }
        double articleId = sqlNumber(articleIdRaw, "article_id");
        if (articleMapper.articleRawById(articleId) == null) {
            throw BizException.notFound("文章不存在");
        }
        Double parentId = null;
        if (parentIdRaw != null) {
            parentId = sqlNumber(parentIdRaw, "parent_id");
            LinkedHashMap<String, Object> parent = commentMapper.commentById(parentId);
            if (parent == null || !ContentJs.sameId(parent.get("article_id"), articleId)) {
                throw BizException.badRequest("父评论不存在或不属于该文章");
            }
        }
        Long userId = null;
        String finalNickname = nicknameRaw != null ? str(nicknameRaw) : null;
        if (actorId != null) {
            userId = actorId;
            String username = commentMapper.usernameById(actorId);
            if (username != null) {
                finalNickname = username;
            }
        }
        if (userId == null && !ContentJs.truthy(finalNickname)) {
            throw BizException.badRequest("匿名评论需要填写昵称");
        }
        String text = ContentJs.trim(ContentJs.strOrEmpty(contentRaw));
        if (text.isEmpty()) {
            throw BizException.badRequest("评论内容不能为空");
        }
        if (text.length() > 500) {
            throw BizException.badRequest("评论内容最多 500 字");
        }
        String condensed = ContentJs.removeWhitespace(text);
        if (ContentJs.allDigits(condensed) && condensed.length() >= 6) {
            throw BizException.badRequest("评论内容过于简单，请认真填写");
        }
        if (userId == null) {
            String nick = ContentJs.trim(finalNickname);
            if (nick.length() < 2 || nick.length() > 20) {
                throw BizException.badRequest("昵称需 2-20 个字符");
            }
            if (ContentJs.allDigits(nick)) {
                throw BizException.badRequest("昵称不能是纯数字");
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("articleId", articleId);
        params.put("userId", userId);
        params.put("nickname", finalNickname);
        params.put("content", text);
        params.put("parentId", parentId);
        commentMapper.insertComment(params);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("comment_id", ContentJs.num(params.get("commentId")));
        return data;
    }

    /** 原版直接拿 {@code Number(v)} 的结果当 SQL 参数；NaN 会拼出非法 SQL（调用方兜成 500）。 */
    private static double sqlNumber(Object raw, String label) {
        Double n = ContentJs.number(raw);
        if (n == null || n.isNaN() || n.isInfinite()) {
            throw new IllegalArgumentException(label + " 不是数字: " + raw);
        }
        return n;
    }

    /** 管理端列表（admin）。 */
    public Map<String, Object> manageList(Object pageRaw, Object pageSizeRaw, Object articleId, Object keyword) {
        int page = Math.max(1, parseIntOrDefault(pageRaw, 1));
        int size = Math.max(1, parseIntOrDefault(pageSizeRaw, 10));
        Object articleFilter = ContentJs.truthy(articleId) ? articleId : null;
        String keywordLike = ContentJs.truthy(keyword) ? "%" + str(keyword) + "%" : null;
        long total = commentMapper.manageCount(articleFilter, keywordLike);
        List<LinkedHashMap<String, Object>> list =
                commentMapper.manageList(articleFilter, keywordLike, size, (page - 1) * size);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", size);
        return data;
    }

    /** 管理端更新（admin）：无字段可更新时 400。 */
    public void manageUpdate(Object commentId, Map<String, Object> body) {
        if (!ContentJs.truthy(commentId)) {
            throw BizException.badRequest("comment_id 不能为空");
        }
        boolean hasContent = body.containsKey("content");
        boolean hasNickname = body.containsKey("nickname");
        if (!hasContent && !hasNickname) {
            throw BizException.badRequest("没有需要更新的字段");
        }
        Double id = ContentJs.number(commentId);
        if (id == null || id.isNaN() || id.isInfinite()) {
            // 原版拼出 comment_id = NaN 让 SQL 报错
            throw new IllegalArgumentException("comment_id 不是数字: " + commentId);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("commentId", id.longValue());
        params.put("hasContent", hasContent);
        params.put("hasNickname", hasNickname);
        params.put("content", hasContent ? str(body.get("content")) : null);
        params.put("nickname", hasNickname ? str(body.get("nickname")) : null);
        commentMapper.updateComment(params);
    }

    /** 级联删除多条（admin）：与 legacy 一致，逐条统计删除条数（含不存在 id 也计 1）。 */
    @Transactional
    public long manageDeleteCascade(Object commentIds) {
        if (!(commentIds instanceof List<?> list) || list.isEmpty()) {
            throw BizException.badRequest("comment_ids 不能为空");
        }
        long deleted = 0;
        for (Object id : list) {
            Double n = ContentJs.number(id);
            if (n == null || n.isNaN() || n.isInfinite()) {
                throw new IllegalArgumentException("comment_id 不是数字: " + id);
            }
            deleted += deleteCascade(n.longValue());
        }
        return deleted;
    }

    /** 级联删除（含全部楼中楼子评论），返回删除条数。 */
    private long deleteCascade(long commentId) {
        List<Object> toDelete = new ArrayList<>();
        toDelete.add(commentId);
        List<Object> frontier = new ArrayList<>();
        frontier.add(commentId);
        while (!frontier.isEmpty()) {
            List<Long> children = commentMapper.childIds(frontier);
            if (children == null || children.isEmpty()) {
                break;
            }
            List<Object> next = new ArrayList<>(children.size());
            for (Long child : children) {
                toDelete.add(child);
                next.add(child);
            }
            frontier = next;
        }
        commentMapper.deleteByIds(toDelete);
        return toDelete.size();
    }
}
