package com.jscreator.common.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * JWT 签发/校验，与 Express 版 legacy-utils/token-creator 完全互通：
 * HS256、载荷 { id, role_id }、有效期 7d、取值 Authorization: Bearer &lt;token&gt;。
 *
 * <p>因此 Node 版签发的 token 在 Java 侧继续有效（前端用户不掉线），反之亦然。
 * 密钥缺省值与 Node 版一致，都是 {@code test}（会打 warn，提醒补 JWT_SECRET）。
 */
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final long TTL_MILLIS = 7L * 24 * 60 * 60 * 1000; // 7d

    private final Algorithm algorithm;

    public JwtUtil(String secret) {
        String s = (secret == null || secret.isEmpty()) ? "test" : secret;
        if (secret == null || secret.isEmpty()) {
            log.warn("JWT_SECRET 未配置，使用默认值 test（与 Node 版一致）");
        }
        this.algorithm = Algorithm.HMAC256(s);
    }

    /** 登录用：载荷写 {@code {id, role_id}}，role_id 为 null 时也写成 null（对齐 Node 的 JSON.stringify 行为）。 */
    public String sign(Long id, Long roleId) {
        var builder = base(id);
        if (roleId == null) {
            builder.withNullClaim("role_id");
        } else {
            builder.withClaim("role_id", roleId);
        }
        return builder.sign(algorithm);
    }

    /** 注册用：原版传的对象里没有 role_id 字段，载荷里也就不该出现该键。 */
    public String signWithoutRole(Long id) {
        return base(id).sign(algorithm);
    }

    private com.auth0.jwt.JWTCreator.Builder base(Long id) {
        var builder = JWT.create()
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + TTL_MILLIS));
        if (id != null) {
            builder.withClaim("id", id);
        }
        return builder;
    }

    /** 校验失败一律返回 null（对齐 tokenValidator 的语义，不抛异常）。 */
    public TokenPayload verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String raw = token.startsWith("Bearer ") ? token.substring(7) : token;
        try {
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT decoded = verifier.verify(raw);
            Long id = decoded.getClaim("id").isMissing() ? null : decoded.getClaim("id").asLong();
            Long roleId = decoded.getClaim("role_id").isMissing() ? null : decoded.getClaim("role_id").asLong();
            if (id == null) {
                return null;
            }
            return new TokenPayload(id, roleId);
        } catch (Exception e) {
            return null;
        }
    }
}
