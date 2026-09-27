package com.jscreator.common.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一响应信封，逐字段对齐 Express 版 common/response.ts：
 *
 * <pre>
 *   成功：{ code: 200, success: true, message, data? }        —— data 为 undefined 时不出现该键
 *   失败：{ code, success: false, message }（HTTP 状态码由调用方给定）
 * </pre>
 *
 * 用 LinkedHashMap 保证 JSON 键顺序与 Node 版一致（code → success → message → data）。
 */
public final class Resp {

    private Resp() {
    }

    /** { code: 200, success: true, message: "success" } */
    public static Map<String, Object> ok() {
        return ok("success", null, false);
    }

    /** { code: 200, success: true, message: "success", data } */
    public static Map<String, Object> okData(Object data) {
        return ok("success", data, true);
    }

    public static Map<String, Object> ok(String message) {
        return ok(message, null, false);
    }

    public static Map<String, Object> ok(String message, Object data) {
        return ok(message, data, true);
    }

    private static Map<String, Object> ok(String message, Object data, boolean withData) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("success", true);
        m.put("message", message);
        if (withData) {
            m.put("data", data);
        }
        return m;
    }

    /** { code, success: false, message } */
    public static Map<String, Object> fail(int code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("success", false);
        m.put("message", message);
        return m;
    }

    /**
     * 有序 Map 构造器：{@code map("code", 200, "success", true, "token", t, "user_info", u)}
     * 用于登录这类「信封信封之外还带顶层字段」的响应。
     */
    public static Map<String, Object> map(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("键值必须成对出现");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
