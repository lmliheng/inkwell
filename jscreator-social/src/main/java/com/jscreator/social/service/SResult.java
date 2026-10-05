package com.jscreator.social.service;

/**
 * 业务结果，对齐 Express 版 modules/social/social.service 的 {@code SResult}：
 * 成功带 message（可选 data、可省略 message），失败带 HTTP 状态码与文案。
 *
 * <p>{@code hasData} 对应 TS 里的 {@code data !== undefined}：为 false 时响应体里不出现 data 键。
 * {@code omitMessage} 对应 {@code omitMessage: true}（/social/status 的响应没有 message 键）。
 */
public record SResult(int status, String message, Object data, boolean hasData, boolean omitMessage) {

    public static SResult ok(String message) {
        return new SResult(200, message, null, false, false);
    }

    public static SResult ok(String message, Object data) {
        return new SResult(200, message, data, true, false);
    }

    /** 成功但响应体不带 message（原版 dm 的 data-only 响应）。 */
    public static SResult dataOnly(Object data) {
        return new SResult(200, "", data, true, true);
    }

    public static SResult fail(int status, String message) {
        return new SResult(status, message, null, false, false);
    }

    public boolean isOk() {
        return status == 200;
    }
}
