package com.jscreator.social.util;

import com.jscreator.common.api.Resp;
import com.jscreator.social.service.SResult;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 {@link SResult} 渲染成与原版一致的响应信封：
 *
 * <pre>
 *   成功：{ code:200, success:true, message?, data? }   —— message/data 缺失时键不出现
 *   失败：HTTP 真实状态码 + { code, success:false, message }
 * </pre>
 *
 * 键序用 LinkedHashMap 固定成 code → success → message → data，与 Express 版 render() 一致。
 */
public final class NodeResponse {

    private NodeResponse() {
    }

    public static ResponseEntity<Map<String, Object>> render(SResult out) {
        if (!out.isOk()) {
            return fail(out.status(), out.message());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("success", true);
        if (!out.omitMessage()) {
            body.put("message", out.message());
        }
        if (out.hasData()) {
            body.put("data", out.data());
        }
        return ResponseEntity.ok(body);
    }

    public static ResponseEntity<Map<String, Object>> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }
}
