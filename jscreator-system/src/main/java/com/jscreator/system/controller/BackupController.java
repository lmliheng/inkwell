package com.jscreator.system.controller;

import com.jscreator.system.service.BackupService;
import com.jscreator.common.web.RequireAdmin;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * GET /backup/download（管理员）：整库备份下载，对齐 Express 版 modules/backup/index.ts。
 *
 * <p>原版在拿到 SQL 之前就把 200 + zip 响应头发出去了，中途出错只能 destroy 连接；
 * 这里改成**先把 SQL 与 zip 生成好再写响应**，失败交给统一异常处理返回 500 信封 —— 前端拿到的是
 * 一个完整可用的 zip 或一个明确的错误，不会下到半截文件。这是刻意的改进，见 README。
 */
@RestController
public class BackupController {

    private static final Logger log = LoggerFactory.getLogger(BackupController.class);

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @RequireAdmin
    @GetMapping("/backup/download")
    public void download(HttpServletResponse response) throws IOException {
        BackupService.Archive archive;
        try {
            archive = backupService.build();
        } catch (Exception e) {
            log.error("数据库备份导出失败", e);
            throw new IllegalStateException("数据库备份导出失败", e);
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 文件名是纯 ASCII（<库名>-<yyyy-MM-dd_HHmmss>-dump.zip），与原版写法一致，不做编码
        response.setHeader("Content-Disposition", "attachment; filename=\"" + archive.zipName() + "\"");
        response.setContentLength(archive.bytes().length);
        response.getOutputStream().write(archive.bytes());
        response.flushBuffer();

        log.info("[备份] 导出 {}（zip {} KB）", archive.zipName(), archive.bytes().length / 1024);
    }
}
