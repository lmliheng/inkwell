package com.jscreator.content.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * content 域（article / blog / comment / ad / announcement / upload）用到的「JS 取值语义」小工具。
 *
 * <p>原版是 TypeScript，接口分支大量依赖隐式转换（truthy / {@code String(v)} / {@code Number(v)} /
 * {@code parseInt} / {@code ??}），移植时逐条照抄这些语义，避免「空串 vs null vs 缺键」的行为漂移。
 * 与 auth 服务的 {@code OauthOpenapiJs} 是同一套写法的两份拷贝（两个服务之间不共享模块）。
 */
public final class ContentJs {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ContentJs() {
    }

    /** JS 的 truthy 判定：null/undefined/''/0/NaN/false 为假，空数组与空对象为真。 */
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
        return true;
    }

    /** JS 的 {@code String(v)}（null → "null"，数字不带小数点）。 */
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
     * JS 的 {@code Number(v)}：空串/空白串 → 0，非法一律 NaN（这里用 null 表示 NaN，方便判空）。
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

    /** JS 的 {@code Number(v) ? 1 : 0}。 */
    public static Integer truthyFlag(Object v) {
        Double n = number(v);
        return (n != null && n != 0 && !n.isNaN() && !n.isInfinite()) ? 1 : 0;
    }

    /**
     * tinyint(1) 归一：MySQL Connector/J 的 {@code tinyInt1isBit} 默认为 true，会把 TINYINT(1)
     * 取成 Boolean，而原版 mysql2 出来的是数字 1/0 —— 直接序列化会变成 true/false。
     * article.status / ad.status / announcement.status 出库后都过一遍这里。null 原样保留。
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

    /** JS 的 {@code String.prototype.trim()}（ASCII 白空白足够；用于标题/昵称/评论正文校验）。 */
    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** JS 的 {@code /^\d+$/.test(s)}：\d 只认 [0-9]。 */
    public static boolean allDigits(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** JS 的 {@code s.replace(/\s+/g, '')}。 */
    public static String removeWhitespace(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isJsWhitespace(c)) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isJsWhitespace(char c) {
        return c == ' ' || (c >= '\t' && c <= '\r') || c == '\u00a0' || c == '\u1680'
                || (c >= '\u2000' && c <= '\u200a') || c == '\u2028' || c == '\u2029'
                || c == '\u202f' || c == '\u205f' || c == '\u3000' || c == '\ufeff';
    }

    /** JSON 列 → 对象（解析失败按原版处理成 null）。 */
    public static Object parseJson(Object raw) {
        String text = raw instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : str(raw);
        try {
            return JSON.readValue(text, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JSON 列 → 数组（原版 parseJson：**已经是数组就原样返回**，空值/非数组/解析失败都是 []）。
     * 反过来当入参是字符串时按 JSON 文本解析——MyBatis 从 JSON 列取出来的是字符串，
     * 而 mysql2 早就解析成对象了，两种形态都要能接。
     */
    public static List<Object> jsonArray(Object raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        if (raw instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        Object parsed = parseJson(raw);
        if (parsed instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    /** 原版 {@code GROUP_CONCAT(...) → String(v).split(',').map(Number)}。 */
    public static List<Object> splitInts(Object v) {
        List<Object> out = new ArrayList<>();
        if (truthy(v)) {
            for (String part : str(v).split(",")) {
                Double n = number(part);
                if (n != null && !n.isNaN()) {
                    out.add(n == Math.floor(n) && !n.isInfinite() ? (Object) (long) n.doubleValue() : (Object) n);
                }
            }
        }
        return out;
    }

    /** 原版 {@code GROUP_CONCAT(...) → String(v).split(',')}。 */
    public static List<String> splitStrings(Object v) {
        List<String> out = new ArrayList<>();
        if (truthy(v)) {
            out.addAll(List.of(str(v).split(",")));
        }
        return out;
    }

    /** 聚合列（COUNT）归一成数字：原版是 {@code Number(v) || 0}。 */
    public static long num(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        Double d = number(v);
        return d == null || d.isNaN() ? 0L : d.longValue();
    }

    /** 请求体归一：原版用 express.json()，body 非对象时取属性一律 undefined（等价空对象）。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> objectMap(Object body) {
        if (body instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    /** {@code Number(a) === Number(b)}（原版的归属判断，null → 0）。 */
    public static boolean sameId(Object a, Object b) {
        Double x = number(a);
        Double y = number(b);
        if (x == null || y == null) {
            return false;
        }
        return x.doubleValue() == y.doubleValue();
    }
}
