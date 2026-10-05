package com.jscreator.auth.util;

import com.jscreator.auth.mapper.ApiKeyMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

/**
 * /api/v1/* 的 API Key 鉴权，对齐 Express 版 openapi.routes 的 requireApiKey / requireScope：
 *
 * <ul>
 *   <li>自己解析 {@code Authorization: Bearer sk_...}，<b>不走 JWT 拦截器</b>（这里的 token 是 API Key 不是 JWT）</li>
 *   <li>无效 → 401 {@code 无效的 API Key}</li>
 *   <li>scope 不含所需权限 → 403 {@code 该 API Key 无 read 权限} / {@code ... 无 write 权限}</li>
 * </ul>
 *
 * <p>校验过程同原版 apiKey.dao.verify：按 SHA-256(key) 查库、status 必须为 1，通过后写 last_used_at。
 */
@Component
public class OpenApiKeyGuard {

    private final ApiKeyMapper apiKeyMapper;

    public OpenApiKeyGuard(ApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    /** 原版 middleware 里挂在 req 上的 {@code apiKeyUser}。 */
    public record Verified(long userId, String scopes) {
    }

    /** 校验通过返回用户与权限；无效（缺头 / 不是 sk_ 开头 / 查不到 / 已禁用）返回 null。 */
    public Verified verify(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        String plain = "";
        if (auth != null && auth.startsWith("Bearer ")) {
            plain = auth.substring(7).trim();
        }
        if (plain.isEmpty() || !plain.startsWith("sk_")) {
            return null;
        }
        LinkedHashMap<String, Object> row = apiKeyMapper.findByHash(OauthOpenapiJs.sha256Hex(plain));
        Integer status = row == null ? null : OauthOpenapiJs.tinyInt(row.get("status"));
        if (row == null || status == null || status != 1) {
            return null;
        }
        try {
            // 原版是「不阻塞响应」的异步 UPDATE，这里同步写（对外可观察结果一致）
            apiKeyMapper.touchLastUsed(row.get("id"));
        } catch (Exception ignored) {
            // 原版 .catch(() => {})：时间戳写失败不影响鉴权结果
        }
        long userId = row.get("user_id") instanceof Number n ? n.longValue() : 0L;
        String scopes = OauthOpenapiJs.truthy(row.get("scopes")) ? OauthOpenapiJs.str(row.get("scopes")) : "read";
        return new Verified(userId, scopes);
    }

    /** 原版 requireScope：scopes 逗号分隔、逐项 trim 后做精确匹配。 */
    public static boolean hasScope(Verified user, String scope) {
        if (user == null) {
            return false;
        }
        for (String s : user.scopes().split(",")) {
            if (s.trim().equals(scope)) {
                return true;
            }
        }
        return false;
    }
}
