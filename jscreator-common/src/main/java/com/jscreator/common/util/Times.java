package com.jscreator.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间文案。原版用 {@code new Date().toLocaleString()}，输出随运行环境的 locale 变，
 * 这里固定成 {@code yyyy/M/d HH:mm:ss}（与中文环境一致，前端只把它当字符串展示）。
 */
public final class Times {

    private static final DateTimeFormatter LOGIN_TIME = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss");

    private Times() {
    }

    public static String loginTime() {
        return LocalDateTime.now().format(LOGIN_TIME);
    }
}
