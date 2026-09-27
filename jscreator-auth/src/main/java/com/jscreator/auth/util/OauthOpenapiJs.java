package com.jscreator.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * oauth / openapi 两个模块共用的「JS 取值语义」小工具（同一移植人负责，故放一个文件）。
 *
 * <p>原版是 TypeScript，大量地方直接用了 JS 的隐式转换（truthy / {@code String(v)} / {@code Number(v)} /
 * {@code parseInt}），移植时逐条照抄这些语义，避免出现「空字符串 vs null vs 缺键」这类行为漂移：
 *
 * <ul>
 *   <li>{@link #truthy} —— {@code if (v)} 的判定：null/undefined/''/0/NaN/false 为假，空数组与空对象<b>为真</b></li>
 *   <li>{@link #str} —— {@code String(v)}：null → {@code "null"}（JS 就是这么写的），数字不带小数点</li>
 *   <li>{@link #strOrEmpty} —— {@code v ? String(v) : ''}</li>
 *   <li>{@link #number} —— {@code Number(v)}，解析不了返回 NaN</li>
 *   <li>{@link #parseIntOrDefault} —— {@code parseInt(v) || def}（原版分页参数用的就是它）</li>
 *   <li>{@link #truthyFlag} —— {@code Number(v) ? 1 : 0}（status 落库用的就是它）</li>
 * </ul>
 */
public final class OauthOpenapiJs {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OauthOpenapiJs() {
    }

    /** 原版 {@code randomBytes(n).toString('hex')}：oauth 的 client_id / 授权码、api_key 明文都靠它。 */
    public static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** 原版 {@code createHash('sha256').update(s).digest('hex')}（api_key 只存这个哈希）。 */
    public static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 请求体归一：原版用 express.json()，body 是 JSON 对象时按对象取属性；是数组 / 字符串 / null 时
     * 取属性一律 undefined（等价于空对象）。Java 侧直接把 {@code Object} 收下来再走这里，行为对齐。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObjectMap(Object body) {
        if (body instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    /** JS 的 truthy 判定。 */
    public static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        if (v instanceof CharSequence s) {
            return s.length() > 0;
        }
        // JS 里 [] / {} 都是真值
        return true;
    }

    /** JS 的 {@code String(v)}（null → "null"）。 */
    public static String str(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Boolean b) {
            return String.valueOf(b);
        }
        if (v instanceof Double d) {
            return jsNumberToString(d);
        }
        if (v instanceof Float f) {
            return jsNumberToString(f.doubleValue());
        }
        if (v instanceof Number n) {
            return String.valueOf(n);
        }
        if (v instanceof Collection<?> c) {
            // JS 的 String([1,2]) === "1,2"
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Object o : c) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(str(o));
            }
            return sb.toString();
        }
        if (v instanceof Map<?, ?>) {
            return "[object Object]";
        }
        return String.valueOf(v);
    }

    /** JS 的 {@code v ? String(v) : ''}。 */
    public static String strOrEmpty(Object v) {
        return truthy(v) ? str(v) : "";
    }

    private static String jsNumberToString(double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e21) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    /**
     * JS 的 {@code Number(v)}：空串/空白串 → 0，十六进制字面量按 16 进制解析，其余非法一律 NaN。
     * 返回 null 表示 NaN（方便调用方判空）。
     */
    public static Double number(Object v) {
        if (v == null) {
            return 0.0; // Number(null) === 0
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1.0 : 0.0;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return 0.0;
        }
        if (s.equals("Infinity") || s.equals("+Infinity")) {
            return Double.POSITIVE_INFINITY;
        }
        if (s.equals("-Infinity")) {
            return Double.NEGATIVE_INFINITY;
        }
        try {
            if (s.length() > 2 && s.charAt(0) == '0' && (s.charAt(1) == 'x' || s.charAt(1) == 'X')) {
                return Double.valueOf((double) Long.parseLong(s.substring(2), 16));
            }
            if (!s.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")) {
                return null;
            }
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** JS 的 {@code parseInt(String(v), 10) || def}（原版分页参数的写法）。 */
    public static int parseIntOrDefault(Object v, int def) {
        if (v == null) {
            return def;
        }
        String s = v instanceof Double d ? jsNumberToString(d) : String.valueOf(v);
        s = s.trim();
        int i = 0;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            i++;
        }
        int start = i;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i == start) {
            return def;
        }
        try {
            long parsed = Long.parseLong(s.substring(0, i));
            return parsed == 0 ? def : (int) parsed;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** JS 的 {@code Number(v) ? 1 : 0}（oauth_client / api_key 的 status 落库）。 */
    public static Integer truthyFlag(Object v) {
        Double n = number(v);
        return (n != null && n != 0 && !n.isNaN() && !n.isInfinite()) ? 1 : 0;
    }

    /**
     * tinyint(1) 归一：MySQL Connector/J 的 {@code tinyInt1isBit} 默认为 true，会把 TINYINT(1)
     * 列取成 Boolean，而原版 mysql2 出来的是数字 1/0 —— 直接序列化会变成 true/false，
     * 所以所有 tinyint(1) 列（oauth_client.status、api_key.status、article.status、oauth_code.used）
     * 出库后都过一遍这里。null 原样保留。
     */
    public static Integer tinyInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.valueOf(String.valueOf(v));
    }

    /** 原版 DAO 里的 {@code Array.isArray(v) ? v.join(',') : (v ? String(v) : '')}。 */
    public static String joinOrEmpty(Object v) {
        if (v instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(str(list.get(i)));
            }
            return sb.toString();
        }
        return strOrEmpty(v);
    }
}
