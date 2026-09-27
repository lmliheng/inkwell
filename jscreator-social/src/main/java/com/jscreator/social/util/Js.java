package com.jscreator.social.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JS 取值语义的小工具，供 social/dm/notification 三层复用。
 *
 * <p>原版 Node 侧的判断（{@code if (body.id)}、{@code parseInt(...) || 10}、{@code Number(x)}、
 * {@code String(v)}）都要逐字节对齐，Java 这边就靠这几个函数把差异收进一个地方。
 */
public final class Js {

    private Js() {
    }

    /** JS 的 truthy 判定：null / false / 0 / NaN / '' 为假，其余（含空数组、空对象）为真。 */
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

    /** JS 的 {@code String(v)}（null → "null"；数组 → "1,2"）。 */
    public static String str(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Boolean b) {
            return String.valueOf(b);
        }
        if (v instanceof Double d) {
            return numberToString(d);
        }
        if (v instanceof Float f) {
            return numberToString(f.doubleValue());
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

    private static String numberToString(double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e21) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    /**
     * JS 的 {@code Number(v)}：空串 → 0，其余非法一律 NaN。
     *
     * @return null 表示 NaN（方便调用方判空；原版这里会带着 NaN 去查库，直接 500）
     */
    public static Double number(Object v) {
        if (v == null) {
            return 0.0;
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

    /** JS 的 {@code parseInt(String(v), 10) || def}（原版分页参数的统一写法）。 */
    public static int parseIntOrDefault(Object v, int def) {
        if (v == null) {
            return def;
        }
        String s = v instanceof Double d ? numberToString(d) : str(v);
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

    /**
     * tinyint(1) 归一：Connector/J 的 {@code tinyInt1isBit} 默认为 true，会把 TINYINT(1) 列取成
     * Boolean，而原版 mysql2 出来的是数字 1/0（user_notification.is_read、article.status 等），
     * 直接序列化会变成 true/false。null 原样保留。
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

    /**
     * 请求体归一：原版用 express.json()，body 是对象时按对象取属性；是数组/字符串/null 时取值一律
     * undefined（等价于空对象）。Java 侧把 {@code Object} 收下来后走这里，行为对齐。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObjectMap(Object body) {
        if (body instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }
}
