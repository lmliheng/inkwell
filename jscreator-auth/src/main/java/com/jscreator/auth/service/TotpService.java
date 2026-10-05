package com.jscreator.auth.service;

import com.jscreator.auth.mapper.TotpMapper;
import com.jscreator.auth.util.TotpUtil;
import com.jscreator.common.security.JwtUtil;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

/**
 * TOTP 绑定/解绑/状态/登录业务，逐条对齐 Express 版 modules/auth/totp/totp.service：
 *
 * <ul>
 *   <li>setup 只生成密钥并回给前端，<b>不写库</b>；confirm 校验通过才落库</li>
 *   <li>disable：未绑定 → not-bound；动态码错 → code-wrong；成功置 NULL</li>
 *   <li>status：totp_secret 非 NULL 即 bound</li>
 *   <li>login：账号（用户名或邮箱）→ 未绑定 → 动态码</li>
 * </ul>
 */
@Service
public class TotpService {

    private static final String ISSUER = "JScreator";

    private final TotpMapper mapper;
    private final JwtUtil jwtUtil;

    public TotpService(TotpMapper mapper, JwtUtil jwtUtil) {
        this.mapper = mapper;
        this.jwtUtil = jwtUtil;
    }

    public record SetupInfo(String secret, String uri) {
    }

    public record LoginOk(String token, LinkedHashMap<String, Object> user) {
    }

    /** setup：用户不存在返回 null（controller 翻成 404）。 */
    public SetupInfo setup(Long userId) {
        LinkedHashMap<String, Object> user = mapper.selectBasicById(userId);
        if (user == null) {
            return null;
        }
        String secret = TotpUtil.generateSecret();
        String account = firstNonEmpty(str(user.get("username")), str(user.get("email")), str(user.get("id")));
        return new SetupInfo(secret, TotpUtil.keyUri(account, ISSUER, secret));
    }

    /** confirm：校验通过才写库。 */
    public boolean confirm(Long userId, String secret, String code) {
        if (!TotpUtil.check(code, secret)) {
            return false;
        }
        mapper.updateTotpSecret(userId, secret);
        return true;
    }

    /** disable：返回 ok / not-bound / code-wrong。 */
    public String disable(Long userId, String code) {
        String secret = mapper.selectTotpSecret(userId);
        if (secret == null) {
            return "not-bound";
        }
        if (!TotpUtil.check(code, secret)) {
            return "code-wrong";
        }
        mapper.updateTotpSecret(userId, null);
        return "ok";
    }

    /** status：data.bound。 */
    public LinkedHashMap<String, Object> status(Long userId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("bound", mapper.selectTotpSecret(userId) != null);
        return out;
    }

    /** login：返回 ok / no-account / not-bound / code-wrong。 */
    public Object login(String account, String code) {
        LinkedHashMap<String, Object> user = mapper.findByAccountForTotp(account);
        if (user == null) {
            return "no-account";
        }
        Object secret = user.get("totp_secret");
        if (secret == null || str(secret).isEmpty()) {
            return "not-bound";
        }
        if (!TotpUtil.check(str(code).trim(), str(secret))) {
            return "code-wrong";
        }
        Long id = longValue(user.get("id"));
        Long roleId = longValue(user.get("role_id"));
        return new LoginOk(jwtUtil.sign(id, roleId), user);
    }

    // ------------------------------------------------------------------ 小工具

    /** 对齐 JS 的 String(v)：数字/布尔等原样转字符串。 */
    private static String str(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof byte[] bytes) {
            return new String(bytes);
        }
        return String.valueOf(v);
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
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
}
