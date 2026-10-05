package com.jscreator.system.controller;

import com.jscreator.common.api.Resp;
import com.jscreator.common.web.RequireAdmin;
import com.jscreator.system.service.SystemInfoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /system-monitor*（管理员）+ /（公开健康检查），逐字段对齐 Express 版 modules/systemmon/systemmon.controller。
 *
 * <p>鉴权语义：原版这两条系统监控是 {@code [verifyToken, adminOnly]}，未登录 401「未登录或登录过期」、
 * 非管理员 403「权限不足，仅管理员可查看系统监控」—— 注意这条 403 文案与原版其它管理端接口
 * （「权限不足，仅管理员可操作」）不同，这里按 systemmon 的原文保留。
 */
@RestController
public class SystemMonitorController {

    private final SystemInfoService systemInfoService;

    public SystemMonitorController(SystemInfoService systemInfoService) {
        this.systemInfoService = systemInfoService;
    }

    @RequireAdmin(message = "权限不足，仅管理员可查看系统监控")
    @GetMapping("/system-monitor")
    public ResponseEntity<Map<String, Object>> monitor() {
        return ResponseEntity.ok(Resp.ok("获取系统监控成功", systemInfoService.monitor()));
    }

    @RequireAdmin(message = "权限不足，仅管理员可查看系统监控")
    @GetMapping("/system-monitor/api-stats")
    public ResponseEntity<Map<String, Object>> apiStats() {
        List<Map<String, Object>> list = systemInfoService.apiStats();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return ResponseEntity.ok(Resp.ok("获取接口统计成功", data));
    }

    /**
     * GET /（公开）：原版是 {@code res.json({ code: 200, message: '你好，成功启动JScreate' })}，
     * **没有 success 字段**，这里照抄，不套 Resp 信封。
     */
    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("message", "你好，成功启动JScreate");
        return body;
    }
}
