package com.jscreator.auth.service;

import com.jscreator.auth.mapper.EmailAuthMapper;
import com.jscreator.auth.util.EmailAuthSender;
import com.jscreator.common.security.JwtUtil;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证码登录业务，逐条对齐 Express 版 modules/auth/email/emailAuth.service：
 *
 * <ul>
 *   <li>验证码存在<b>进程内存</b>里（email → {code, expireAt}），5 分钟有效，校验成功即消费</li>
 *   <li>发送动作失败被发送器吞掉，不影响「已发送」的返回</li>
 *   <li>登录时邮箱不存在 → 自动注册（username = 邮箱 @ 前段，冲突则换 u+时间戳），role_id=2</li>
 * </ul>
 */
@Service
public class EmailAuthService {

    private static final long CODE_TTL_MS = 5L * 60 * 1000;

    private record CodeRecord(String code, long expireAt) {
    }

    private final Map<String, CodeRecord> codeStore = new ConcurrentHashMap<>();

    private final EmailAuthMapper mapper;
    private final EmailAuthSender sender;
    private final JwtUtil jwtUtil;

    public EmailAuthService(EmailAuthMapper mapper, EmailAuthSender sender, JwtUtil jwtUtil) {
        this.mapper = mapper;
        this.sender = sender;
        this.jwtUtil = jwtUtil;
    }

    public record LoginOk(String token, LinkedHashMap<String, Object> user) {
    }

    /** 生成并「发送」验证码（发送失败在 sender 内部吞掉，与原版一致）。 */
    public void sendCode(String email) {
        String code = generateCode();
        codeStore.put(email, new CodeRecord(code, System.currentTimeMillis() + CODE_TTL_MS));
        sender.sendVerificationCode(email, code);
    }

    /** 校验并消费验证码：不存在 / 不相等 / 已过期都返回 false。 */
    public boolean validateCode(String email, String code) {
        CodeRecord rec = codeStore.get(email);
        if (rec == null || !rec.code().equals(code) || System.currentTimeMillis() > rec.expireAt()) {
            return false;
        }
        codeStore.remove(email);
        return true;
    }

    /** 验证码通过后：取用户，不存在则自动注册；返回 ok / register-failed。 */
    public Object login(String email) {
        LinkedHashMap<String, Object> user = mapper.findByEmail(email);
        if (user == null) {
            String username = email.split("@", 2)[0];
            if (username.isEmpty()) {
                username = "user";
            }
            username = username.substring(0, Math.min(50, username.length()));
            try {
                mapper.registerEmailUser(username, email, randomPassword());
            } catch (Exception e) {
                // username 冲突时用时间戳兜底（与 legacy 一致）
                String fallback = ("u" + System.currentTimeMillis());
                fallback = fallback.substring(0, Math.min(50, fallback.length()));
                mapper.registerEmailUser(fallback, email, randomPassword());
            }
            user = mapper.findByEmail(email);
            if (user == null) {
                return "register-failed";
            }
        }
        Long id = longValue(user.get("id"));
        Long roleId = longValue(user.get("role_id"));
        return new LoginOk(jwtUtil.sign(id, roleId), user);
    }

    // ------------------------------------------------------------------ 小工具

    /** 100000~999999，与原版 Math.floor(100000 + Math.random()*900000) 同分布。 */
    private static String generateCode() {
        return String.valueOf(100000 + (int) Math.floor(Math.random() * 900000));
    }

    /** 原版随机口令（明文入库，用户永远用不到）：Math.random().toString(36).slice(2) + Date.now().toString(36) */
    private static String randomPassword() {
        double r = Math.random();
        String head = Long.toString((long) (r * 1_000_000_000_000_000L), 36);
        return head + Long.toString(System.currentTimeMillis(), 36);
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
