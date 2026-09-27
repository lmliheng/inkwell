package com.jscreator.auth.service;

import com.jscreator.auth.mapper.ApiKeyMapper;
import com.jscreator.auth.mapper.UserMapper;
import com.jscreator.auth.util.OauthOpenapiJs;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

import static com.jscreator.auth.util.OauthOpenapiJs.str;
import static com.jscreator.auth.util.OauthOpenapiJs.truthy;
import static com.jscreator.auth.util.OauthOpenapiJs.truthyFlag;

/**
 * API Key 管理业务（/api-keys*），对齐 Express 版 modules/openapi/apiKey.service + apiKey.dao：
 * write 权限只有 admin(1) / editor(3) 能建，库里只存 SHA-256 哈希与前缀，明文只返回一次。
 */
@Service
public class ApiKeyService {

    private final ApiKeyMapper apiKeyMapper;
    private final UserMapper userMapper;

    public ApiKeyService(ApiKeyMapper apiKeyMapper, UserMapper userMapper) {
        this.apiKeyMapper = apiKeyMapper;
        this.userMapper = userMapper;
    }

    /** 原版 create 的两种结局：{ok:true, plain, keyPrefix, scopes} / {ok:false, status, message}。 */
    public record CreateResult(boolean ok, String plain, String prefix, String scopes, int status, String message) {

        static CreateResult created(String plain, String prefix, String scopes) {
            return new CreateResult(true, plain, prefix, scopes, 200, null);
        }

        static CreateResult denied(int status, String message) {
            return new CreateResult(false, null, null, null, status, message);
        }
    }

    public CreateResult create(long userId, String name, Object scopesRaw) {
        String scope = "read";
        if (truthy(scopesRaw) && str(scopesRaw).contains("write")) {
            Long roleId = userMapper.selectRoleId(userId);
            if (roleId != null && (roleId == 1L || roleId == 3L)) {
                scope = "read,write";
            } else {
                return CreateResult.denied(403, "仅管理员/编辑可创建写权限 Key");
            }
        }
        String plain = "sk_" + OauthOpenapiJs.randomHex(24);
        String keyPrefix = plain.length() > 10 ? plain.substring(0, 10) : plain;
        apiKeyMapper.insertKey(userId, truthy(name) ? name : "未命名",
                OauthOpenapiJs.sha256Hex(plain), keyPrefix, scope);
        return CreateResult.created(plain, keyPrefix, scope);
    }

    public List<LinkedHashMap<String, Object>> list(long userId) {
        List<LinkedHashMap<String, Object>> rows = apiKeyMapper.listByUser(userId);
        for (LinkedHashMap<String, Object> row : rows) {
            // tinyint(1) 归一成数字，与原版 JSON 里的 1/0 一致
            row.put("status", OauthOpenapiJs.tinyInt(row.get("status")));
        }
        return rows;
    }

    public void setStatus(Object id, long userId, Object status) {
        apiKeyMapper.setStatus(id, userId, truthyFlag(status));
    }

    public void remove(Object id, long userId) {
        apiKeyMapper.delete(id, userId);
    }
}
