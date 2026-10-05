package com.jscreator.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jscreator.auth.mapper.GithubAuthMapper;
import com.jscreator.common.security.JwtUtil;
import com.jscreator.common.security.TokenPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GitHub OAuth 登录/绑定，逐条对齐 Express 版 modules/auth/github/githubAuth.service：
 *
 * <ul>
 *   <li>跳转授权页：{@code https://github.com/login/oauth/authorize?client_id=&redirect_uri=&scope=&state=}</li>
 *   <li>回调：code 换 token → 取用户 → 绑定模式或登录模式 → 302 回前端</li>
 *   <li>没配 client_id/secret 时与原版一样：client_id 出现在 URL 里是空串（Node 是
 *       {@code String(process.env.X)}，未设置时是字符串 "undefined"，这里用 {@link #rawEnv} 保持同样的语义）</li>
 *   <li>网络请求用 JDK HttpClient；<b>对齐 axios 的语义：非 2xx 抛错</b>，所以会落到
 *       {@code error=github_login_failed}，而不是 {@code github_token_failed}</li>
 * </ul>
 */
@Service
public class GithubAuthService {

    private static final Logger log = LoggerFactory.getLogger(GithubAuthService.class);

    private static final String GITHUB_AUTHORIZE = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_API_USER = "https://api.github.com/user";
    private static final String DEFAULT_CALLBACK = "http://127.0.0.1:7000/auth/github/callback";
    private static final String DEFAULT_FRONTEND = "http://127.0.0.1:5173";

    private final GithubAuthMapper mapper;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 与 axios 一致：不设超时（原版也没设），重定向按默认策略跟随。 */
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private final String clientId = rawEnv("GITHUB_CLIENT_ID");
    private final String clientSecret = rawEnv("GITHUB_CLIENT_SECRET");
    private final String callbackUrl = envOr("GITHUB_CALLBACK_URL", DEFAULT_CALLBACK);
    private final String frontendUrl = envOr("FRONTEND_URL", DEFAULT_FRONTEND);

    public GithubAuthService(GithubAuthMapper mapper, JwtUtil jwtUtil) {
        this.mapper = mapper;
        this.jwtUtil = jwtUtil;
    }

    /** GET /auth/github：登录模式授权跳转。 */
    public String loginAuthorizeUrl(String redirect) {
        String state = (redirect != null && !redirect.isEmpty()) ? redirect : frontendUrl + "/login";
        return buildAuthorizeUrl(state);
    }

    /** GET /auth/github/bind：绑定模式授权跳转（state 携带 {mode:'bind', token, redirect}）。 */
    public String bindAuthorizeUrl(String redirect, String token) {
        String r = (redirect != null && !redirect.isEmpty()) ? redirect : frontendUrl + "/login";
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("mode", "bind");
        state.put("token", token == null ? "" : token);
        state.put("redirect", r);
        return buildAuthorizeUrl(toJson(state));
    }

    /** GET /auth/github/callback：内部异常一律 {@code error=github_login_failed}（与 legacy 一致）。 */
    public String handleCallback(String code, String state) {
        JsonNode bindMode = null;
        String frontend = (state != null && !state.isEmpty()) ? state : frontendUrl + "/login";
        if (state != null) {
            try {
                JsonNode parsed = objectMapper.readTree(state);
                if (parsed != null && parsed.isObject() && "bind".equals(asText(parsed.get("mode")))) {
                    bindMode = parsed;
                    String redirect = asText(parsed.get("redirect"));
                    frontend = (redirect == null || redirect.isEmpty()) ? frontendUrl + "/login" : redirect;
                }
            } catch (Exception ignore) {
                // 非 JSON：登录模式
            }
        }
        String sep = frontend.contains("?") ? "&" : "?";
        if (code == null || code.isEmpty()) {
            return frontend + sep + "error=github_no_code";
        }
        try {
            // 用 code 换 access_token
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("client_id", clientId);
            payload.put("client_secret", clientSecret);
            payload.put("code", code);
            payload.put("redirect_uri", callbackUrl);
            HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(GITHUB_TOKEN_URL))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            requireSuccess(tokenResponse.statusCode());
            String accessToken = asText(jsonOrNull(tokenResponse.body()).path("access_token"));
            if (accessToken == null || accessToken.isEmpty()) {
                return frontend + sep + "error=github_token_failed";
            }

            // 拿 GitHub 用户信息
            HttpRequest userRequest = HttpRequest.newBuilder(URI.create(GITHUB_API_USER))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("User-Agent", "jscreator")
                    .GET()
                    .build();
            HttpResponse<String> userResponse = httpClient.send(userRequest, HttpResponse.BodyHandlers.ofString());
            requireSuccess(userResponse.statusCode());
            JsonNode gh = jsonOrNull(userResponse.body());
            Object ghId = githubId(gh);

            // ===== 绑定模式：把 github_id 写入当前登录账号 =====
            if (bindMode != null) {
                TokenPayload decoded = jwtUtil.verify(asText(bindMode.get("token")));
                if (decoded == null || decoded.id() == null) {
                    return frontend + sep + "error=github_bind_login_expired";
                }
                LinkedHashMap<String, Object> existing = mapper.getByGithubId(ghId);
                if (existing != null && !String.valueOf(existing.get("id")).equals(String.valueOf(decoded.id()))) {
                    return frontend + sep + "error=github_bind_conflict";
                }
                mapper.setGithubId(decoded.id(), ghId);
                return frontend + sep + "success=github_bind_ok";
            }

            // 查已绑定用户，否则自动注册
            LinkedHashMap<String, Object> user = mapper.getByGithubId(ghId);
            if (user == null) {
                String login = asText(gh.get("login"));
                String username = truncate((login == null || login.isEmpty()) ? "gh" + ghId : login, 50);
                try {
                    registerGithubUser(ghId, username, login, gh);
                } catch (Exception e) {
                    // username 冲突时改用 gh{id}
                    registerGithubUser(ghId, truncate("gh" + ghId, 50), login, gh);
                }
                user = mapper.getByGithubId(ghId);
                if (user == null) {
                    return frontend + sep + "error=github_register_failed";
                }
            }

            // 签发本站 JWT，重定向回前端
            Long id = longValue(user.get("id"));
            Long roleId = longValue(user.get("role_id"));
            String token = jwtUtil.sign(id, roleId);
            return frontend + sep + "token=" + encodeUriComponent(token)
                    + "&username=" + encodeUriComponent(asText(user.get("username")));
        } catch (Exception error) {
            log.error("GitHub 登录错误: {}", error.toString());
            return frontend + sep + "error=github_login_failed";
        }
    }

    // ------------------------------------------------------------------ 内部

    private void registerGithubUser(Object ghId, String username, String login, JsonNode gh) {
        mapper.registerGithubUser(
                ghId,
                username,
                login,                                       // 原版 name: gh.login ?? null
                asText(gh.get("email")),
                randomPassword(),
                asText(gh.get("avatar_url")));
    }

    private static Object githubId(JsonNode gh) {
        JsonNode id = gh.get("id");
        if (id == null || id.isNull()) {
            return null;
        }
        return id.isNumber() ? id.numberValue() : id.asText();
    }

    /** axios 默认只把 2xx 当成功，其余抛错——照抄这个判定。 */
    private static void requireSuccess(int status) {
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("GitHub 返回状态码 " + status);
        }
    }

    private JsonNode jsonOrNull(String body) {
        try {
            JsonNode node = objectMapper.readTree(body == null ? "" : body);
            return node == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : node;
        } catch (Exception e) {
            return com.fasterxml.jackson.databind.node.NullNode.getInstance();
        }
    }

    private String buildAuthorizeUrl(String state) {
        return GITHUB_AUTHORIZE
                + "?client_id=" + encodeUriComponent(clientId)
                + "&redirect_uri=" + encodeUriComponent(callbackUrl)
                + "&scope=" + encodeUriComponent("read:user user:email")
                + "&state=" + encodeUriComponent(state);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** process.env.X（未设置时 Node 得到 undefined，字符串化就是 "undefined"）。 */
    private static String rawEnv(String name) {
        String v = System.getenv(name);
        return v == null ? "undefined" : v;
    }

    /** process.env.X || fallback。 */
    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private static String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.textValue() : node.asText();
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static Long longValue(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(String.valueOf(v));
    }

    /** 原版随机口令（明文入库）：Math.random().toString(36).slice(2) + Date.now().toString(36) */
    private static String randomPassword() {
        return Long.toString((long) (Math.random() * 1_000_000_000_000_000L), 36)
                + Long.toString(System.currentTimeMillis(), 36);
    }

    /** JS encodeURIComponent 的等价实现（JWT 只会用到不被转义的字符）。 */
    private static String encodeUriComponent(String value) {
        if (value == null) {
            return "undefined";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '!' || c == '~' || c == '*' || c == '\'' || c == '(' || c == ')';
            if (unreserved) {
                sb.append((char) c);
            } else {
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0x0f, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(c & 0x0f, 16)));
            }
        }
        return sb.toString();
    }
}
