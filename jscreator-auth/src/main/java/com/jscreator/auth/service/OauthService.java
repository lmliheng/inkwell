package com.jscreator.auth.service;

import com.jscreator.auth.dto.OauthOutcome;
import com.jscreator.auth.mapper.OauthMapper;
import com.jscreator.auth.util.OauthOpenapiJs;
import com.jscreator.auth.util.OauthTokenIssuer;
import com.jscreator.common.api.Resp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.jscreator.auth.util.OauthOpenapiJs.joinOrEmpty;
import static com.jscreator.auth.util.OauthOpenapiJs.number;
import static com.jscreator.auth.util.OauthOpenapiJs.str;
import static com.jscreator.auth.util.OauthOpenapiJs.strOrEmpty;
import static com.jscreator.auth.util.OauthOpenapiJs.truthy;
import static com.jscreator.auth.util.OauthOpenapiJs.truthyFlag;

/**
 * OAuth 2.0 授权服务器业务（授权码 + PKCE / Client Credentials / 管理端），
 * 逐行对齐 Express 版 modules/oauth/oauth.service + oauth.dao（含响应怪癖与 400 文本错误）。
 *
 * <p>数据库异常不在这里吞：controller 按各端点 legacy 文案兜底 500。
 */
@Service
public class OauthService {

    private final OauthMapper oauthMapper;
    private final OauthTokenIssuer tokenIssuer;

    public OauthService(OauthMapper oauthMapper, OauthTokenIssuer tokenIssuer) {
        this.oauthMapper = oauthMapper;
        this.tokenIssuer = tokenIssuer;
    }

    // ================= 管理端 =================

    public OauthOutcome listClients() {
        List<LinkedHashMap<String, Object>> list = oauthMapper.listClients();
        // 原版：map(r => ({ ...r, client_secret_hidden: true }))
        for (LinkedHashMap<String, Object> row : list) {
            row.put("status", OauthOpenapiJs.tinyInt(row.get("status")));
            row.put("client_secret_hidden", true);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return new OauthOutcome.Json(200, Resp.ok("获取成功", data));
    }

    public OauthOutcome createClient(Map<String, Object> input) {
        String name = truthy(input.get("name")) ? str(input.get("name")) : "";
        if (name.isEmpty()) {
            return json(400, Resp.fail(400, "应用名称不能为空"));
        }
        String grantTypes = input.get("grant_types") == null
                ? "authorization_code,client_credentials"
                : str(input.get("grant_types"));
        Object redirectUris = input.get("redirect_uris");
        if (grantTypes.contains("authorization_code") && !truthy(redirectUris)) {
            return json(400, Resp.fail(400, "授权码模式需要填写回调 URI"));
        }
        String description = input.containsKey("description") ? str(input.get("description")) : "";
        String scopes = input.containsKey("scopes") ? str(input.get("scopes")) : "read";
        String logo = input.containsKey("logo") ? str(input.get("logo")) : "";

        // ---- 下列默认值来自 oauth.dao.createClient 的 `||` 兜底 ----
        String clientId = "oa_" + OauthOpenapiJs.randomHex(16);
        String clientSecret = grantTypes.contains("client_credentials") ? OauthOpenapiJs.randomHex(24) : null;
        oauthMapper.insertClient(
                clientId,
                clientSecret,
                name,
                truthy(description) ? description : "",
                joinOrEmpty(redirectUris),
                truthy(scopes) ? scopes : "read",
                truthy(grantTypes) ? grantTypes : "authorization_code,client_credentials",
                truthy(logo) ? logo : "");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", oauthMapper.selectClientIdByClientId(clientId));
        data.put("client_id", clientId);
        data.put("client_secret", clientSecret);
        if (clientSecret != null) {
            data.put("note", "client_secret 只显示这一次，请妥善保存");
        }
        return json(200, Resp.ok("创建成功", data));
    }

    public OauthOutcome updateClient(Object idRaw, Map<String, Object> input) {
        Double id = number(idRaw);
        String name = truthy(input.get("name")) ? str(input.get("name")) : "";
        if (id == null || id == 0 || name.isEmpty()) {
            return json(400, Resp.fail(400, "参数缺失"));
        }
        String description = input.containsKey("description") ? str(input.get("description")) : "";
        String scopes = input.containsKey("scopes") ? str(input.get("scopes")) : "read";
        String logo = input.containsKey("logo") ? str(input.get("logo")) : "";
        String grantTypes = input.get("grant_types") == null
                ? "authorization_code,client_credentials"
                : str(input.get("grant_types"));

        oauthMapper.updateClient(
                jsNumberArg(id),
                name,
                truthy(description) ? description : "",
                joinOrEmpty(input.get("redirect_uris")),
                truthy(scopes) ? scopes : "read",
                truthy(grantTypes) ? grantTypes : "authorization_code,client_credentials",
                truthy(logo) ? logo : "");
        return json(200, Resp.ok("更新成功"));
    }

    public OauthOutcome setClientStatus(Object idRaw, Object status) {
        oauthMapper.setClientStatus(idRaw, truthyFlag(status));
        return json(200, Resp.ok("操作成功"));
    }

    public OauthOutcome deleteClient(Object idRaw) {
        oauthMapper.deleteClient(idRaw);
        return json(200, Resp.ok("删除成功"));
    }

    // ================= 授权端点（GET /oauth/authorize） =================

    /**
     * @param query       query 参数（原版 {@code req.query}）
     * @param userId      Authorization 解析出的用户 id；未登录为 null
     * @param queryString 原版用 {@code new URLSearchParams(req.query).toString()} 重新编码后的串
     */
    public OauthOutcome authorize(Map<String, Object> query, Long userId, String queryString) {
        String clientId = truthy(query.get("client_id")) ? str(query.get("client_id")) : "";
        String redirectUri = truthy(query.get("redirect_uri")) ? str(query.get("redirect_uri")) : "";
        if (clientId.isEmpty() || redirectUri.isEmpty()) {
            return new OauthOutcome.Text(400, "参数缺失：需要 client_id 与 redirect_uri");
        }
        LinkedHashMap<String, Object> client = oauthMapper.getClientByClientId(clientId);
        if (client == null || !isEnabled(client)) {
            return new OauthOutcome.Text(400, "无效的客户端");
        }
        if (!isAllowedRedirect(client, redirectUri)) {
            return new OauthOutcome.Text(400, "redirect_uri 不在白名单");
        }
        if (!"code".equals(normalize(query.get("response_type")))) {
            return new OauthOutcome.Text(400, "仅支持 response_type=code");
        }
        if (userId == null) {
            String loginUrl = "/login?redirect=" + encodeUriComponent("/oauth/authorize?" + queryString);
            return new OauthOutcome.Redirect(loginUrl);
        }
        String code = createCode(
                String.valueOf(client.get("client_id")),
                userId,
                truthy(query.get("scope")) ? str(query.get("scope")) : "read",
                truthy(query.get("code_challenge")) ? str(query.get("code_challenge")) : null,
                redirectUri);
        // 原版：state !== undefined && state !== null 时才带上（空串也会带上）
        boolean hasState = query.containsKey("state") && query.get("state") != null;
        String location = appendQueryParams(redirectUri, "code", code, hasState ? str(query.get("state")) : null);
        return new OauthOutcome.Redirect(location);
    }

    /**
     * 生成授权码。这里<b>照抄原版的一个 bug</b>：原版 oauth.dao.createCode 的 INSERT 有 7 个占位符，
     * 参数数组却只给了 6 个（{@code expires_at} 漏传），mysql2 会把没绑定的 {@code ?} 原样发给 MySQL，
     * 于是必然 ER_PARSE_ERROR(1064)，被 oauth.controller 兜底成 HTTP 500 + 纯文本「服务器内部错误」。
     *
     * <p>也就是说原版「已登录用户通过全部校验后走 authorize」这条路就是 500（前端按这个形态对接，
     * 授权的可用路径实际只有未登录跳登录页 / client_credentials），移植版保持一致、不擅自修好它。
     * 对照用例见 scripts/ref_cases/oauth.py 的「authorize 已登录 → 500」。
     *
     * <p>（mapper 里的 {@link OauthMapper#insertCode} 保留原版 SQL，供将来原版修好后接回。）
     */
    public String createCode(String clientId, Long userId, String scope, String challenge, String redirectUri) {
        throw new IllegalStateException(
                "ER_PARSE_ERROR: You have an error in your SQL syntax; check the manual that corresponds to your "
                        + "MySQL server version for the right syntax to use near '?)' at line 2");
    }

    private static boolean isAllowedRedirect(LinkedHashMap<String, Object> client, String uri) {
        String allowed = String.valueOf(client.get("redirect_uris") == null ? "" : client.get("redirect_uris"));
        for (String part : allowed.split(",")) {
            if (part.trim().equals(uri)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEnabled(LinkedHashMap<String, Object> client) {
        // 原版：Number(client.status) !== 1
        Integer status = OauthOpenapiJs.tinyInt(client.get("status"));
        return status != null && status == 1;
    }

    private static String normalize(Object v) {
        return v == null ? "" : str(v);
    }

    // ================= Token 端点（POST /oauth/token） =================

    /**
     * 授权码消费（查码 + 标记已用）在一个事务里，对齐 legacy db_oauth.consumeCode 的 withTransaction。
     */
    @Transactional
    public OauthOutcome token(Map<String, Object> body) {
        String grantType = truthy(body.get("grant_type")) ? str(body.get("grant_type")) : "";
        if ("authorization_code".equals(grantType)) {
            return tokenByAuthorizationCode(body);
        }
        if ("client_credentials".equals(grantType)) {
            return tokenByClientCredentials(body);
        }
        return err(400, "不支持的 grant_type", "unsupported_grant_type");
    }

    private OauthOutcome tokenByAuthorizationCode(Map<String, Object> body) {
        String code = truthy(body.get("code")) ? str(body.get("code")) : "";
        if (code.isEmpty()) {
            return err(400, "缺少授权码", "invalid_request");
        }
        LinkedHashMap<String, Object> rec = oauthMapper.getCode(code);
        if (rec == null) {
            return err(400, "授权码无效", "invalid_grant");
        }
        Integer used = OauthOpenapiJs.tinyInt(rec.get("used"));
        if (used != null && used == 1) {
            return err(400, "授权码已使用", "invalid_grant");
        }
        if (isExpired(rec.get("expires_at"))) {
            return err(400, "授权码已过期", "invalid_grant");
        }
        oauthMapper.markCodeUsed(rec.get("id"));

        LinkedHashMap<String, Object> client = oauthMapper.getClientByClientId(str(rec.get("client_id")));
        if (client == null || !isEnabled(client)) {
            return err(400, "无效的客户端", "invalid_client");
        }
        if (truthy(rec.get("code_challenge"))) {
            String verifier = body.get("code_verifier") == null ? "" : str(body.get("code_verifier"));
            if (!pkceVerify(verifier, str(rec.get("code_challenge")))) {
                return err(400, "PKCE 校验失败", "invalid_grant");
            }
        }
        String scope = truthy(rec.get("scope")) ? str(rec.get("scope")) : "read";
        String accessToken = tokenIssuer.issue(toLong(rec.get("user_id")), str(client.get("client_id")), scope, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("access_token", accessToken);
        out.put("token_type", "Bearer");
        out.put("expires_in", 3600);
        out.put("scope", scope);
        return json(200, out);
    }

    private OauthOutcome tokenByClientCredentials(Map<String, Object> body) {
        String clientId = truthy(body.get("client_id")) ? str(body.get("client_id")) : "";
        String clientSecret = truthy(body.get("client_secret")) ? str(body.get("client_secret")) : "";
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            return err(400, "缺少客户端凭证", "invalid_client");
        }
        LinkedHashMap<String, Object> client = oauthMapper.getClientByClientId(clientId);
        if (client == null || !isEnabled(client) || !truthy(client.get("client_secret"))) {
            return err(401, "无效的客户端", "invalid_client");
        }
        // 常量时间比较 secret：与 legacy 一致，长度不一致时 crypto.timingSafeEqual 会抛错 → 500
        byte[] given = clientSecret.getBytes(StandardCharsets.UTF_8);
        byte[] expected = str(client.get("client_secret")).getBytes(StandardCharsets.UTF_8);
        if (given.length != expected.length) {
            throw new IllegalStateException("timingSafeEqual: Input buffers must have the same byte length");
        }
        if (!MessageDigest.isEqual(given, expected)) {
            return err(401, "客户端密钥错误", "invalid_client");
        }
        String scope = truthy(client.get("scopes")) ? str(client.get("scopes")) : "read";
        String accessToken = tokenIssuer.issue(null, str(client.get("client_id")), scope, "client_credentials");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("access_token", accessToken);
        out.put("token_type", "Bearer");
        out.put("expires_in", 3600);
        out.put("scope", scope);
        return json(200, out);
    }

    /** 对齐 legacy pkceVerify：只有 S256（无 method 参数时默认 S256）。 */
    private static boolean pkceVerify(String codeVerifier, String codeChallenge) {
        if (codeVerifier.isEmpty() || codeChallenge.isEmpty()) {
            return false;
        }
        String hash = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256(codeVerifier.getBytes(StandardCharsets.UTF_8)));
        return hash.equals(codeChallenge);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static boolean isExpired(Object expiresAt) {
        LocalDateTime expires = toLocalDateTime(expiresAt);
        return expires != null && expires.isBefore(LocalDateTime.now());
    }

    private static LocalDateTime toLocalDateTime(Object v) {
        if (v instanceof LocalDateTime t) {
            return t;
        }
        if (v instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime();
        }
        if (v instanceof java.util.Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneId.systemDefault());
        }
        return null;
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    // ================= 小工具 =================

    private static OauthOutcome.Json json(int status, Map<String, Object> body) {
        return new OauthOutcome.Json(status, body);
    }

    private static OauthOutcome.Json err(int status, String message, String error) {
        Map<String, Object> body = Resp.fail(status, message);
        if (error != null) {
            body.put("error", error);
        }
        return new OauthOutcome.Json(status, body);
    }

    /** 原版把 {@code Number(id)} 的结果直接塞进 SQL：整数给 Long，小数给 Double。 */
    private static Object jsNumberArg(Double id) {
        if (id == Math.floor(id) && !Double.isInfinite(id)) {
            return id.longValue();
        }
        return id;
    }

    /**
     * 对齐 {@code new URL(redirect_uri)} + {@code searchParams.set(...)} + {@code toString()}：
     * set 会覆盖同名参数，所以这里先按 key 去掉已有项再追加。
     */
    private static String appendQueryParams(String url, String key, String value, String stateValue) {
        String base = url;
        String query = "";
        int hash = url.indexOf('#');
        if (hash >= 0) {
            base = url.substring(0, hash);
        }
        int q = base.indexOf('?');
        if (q >= 0) {
            query = base.substring(q + 1);
            base = base.substring(0, q);
        }
        StringBuilder sb = new StringBuilder(base).append('?');
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            String k = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            if (decode(k).equals(key) || (stateValue != null && decode(k).equals("state"))) {
                continue;
            }
            sb.append(pair).append('&');
        }
        sb.append(key).append('=').append(encodeQueryComponent(value));
        if (stateValue != null) {
            sb.append("&state=").append(encodeQueryComponent(stateValue));
        }
        String out = sb.toString();
        if (hash >= 0) {
            out += url.substring(hash);
        }
        return out;
    }

    private static String decode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    /** URLSearchParams.set 的编码：空格是 +，其余按 encodeURIComponent 语义。 */
    private static String encodeQueryComponent(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** encodeURIComponent 语义（比 URLEncoder 少编码 !'()*~ ）。 */
    private static String encodeUriComponent(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%2A", "*")
                .replace("%7E", "~");
    }
}
