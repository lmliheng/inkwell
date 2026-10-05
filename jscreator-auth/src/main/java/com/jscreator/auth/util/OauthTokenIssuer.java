package com.jscreator.auth.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * /oauth/token 签发的 access_token，对齐 Express 版 oauth.service 的 issueAccessToken：
 * <pre>
 *   jwt.sign({ ...payload, typ: 'oauth-access', jti }, JWT_SECRET || 'test', { expiresIn: 3600 })
 * </pre>
 * 也就是 HS256、1 小时、载荷里带 {@code typ=oauth-access} 与随机 {@code jti}；密钥来源与
 * {@code JwtUtil} 一致（{@code jscreator.jwt.secret} ← {@code JWT_SECRET}，缺省 {@code test}），
 * 这样 Node 侧校验的 token 在 Java 侧签发仍然合法。
 *
 * <p>没有改共享的 {@code JwtUtil}（它只负责登录 token 的 {id, role_id} 形状），这里自带一个签发器。
 */
@Component
public class OauthTokenIssuer {

    private static final long TTL_SECONDS = 3600;

    private final Algorithm algorithm;

    public OauthTokenIssuer(@Value("${jscreator.jwt.secret:${JWT_SECRET:}}") String secret) {
        this.algorithm = Algorithm.HMAC256(secret == null || secret.isEmpty() ? "test" : secret);
    }

    /**
     * @param userId   授权码模式下是 oauth_code.user_id；client_credentials 模式为 null
     * @param clientId 客户端
     * @param scope    授权范围
     * @param type     仅 client_credentials 模式会写 {@code type=client_credentials}
     */
    public String issue(Long userId, String clientId, String scope, String type) {
        long now = System.currentTimeMillis();
        JWTCreator.Builder builder = JWT.create()
                .withIssuedAt(new Date(now))
                .withExpiresAt(new Date(now + TTL_SECONDS * 1000));
        if (userId == null) {
            builder.withNullClaim("user_id");
        } else {
            builder.withClaim("user_id", userId);
        }
        builder.withClaim("client_id", clientId);
        builder.withClaim("scope", scope);
        if (type != null) {
            builder.withClaim("type", type);
        }
        builder.withClaim("typ", "oauth-access");
        // 原版：sha256(String(Math.random())).slice(0, 16)
        builder.withClaim("jti", OauthOpenapiJs.sha256Hex(String.valueOf(Math.random())).substring(0, 16));
        return builder.sign(algorithm);
    }
}
