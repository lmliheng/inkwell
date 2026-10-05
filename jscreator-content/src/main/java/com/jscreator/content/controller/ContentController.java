package com.jscreator.content.controller;

import com.jscreator.common.api.Resp;
import com.jscreator.common.exception.BizException;
import com.jscreator.common.web.RequireAdmin;
import com.jscreator.content.service.ContentService;
import com.jscreator.content.util.ContentJs;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * /ad/* 与 /announcement/* 的 HTTP 层，逐字段对齐 Express 版 modules/content/content.controller。
 *
 * <p>注意：原版注册的是 <b>POST /ad/click/:id</b>（content.routes.ts 里 {@code r.post('/ad/click/:id')}），
 * 不是 GET —— 以源码为准。
 */
@RestController
public class ContentController {

    private static final Logger log = LoggerFactory.getLogger(ContentController.class);

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    // ================= 广告：公开 =================

    @GetMapping("/ad/slots")
    public ResponseEntity<Map<String, Object>> adSlots(HttpServletRequest request) {
        try {
            Map<String, Object> data = contentService.adSlots(request.getParameter("position"));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取广告错误", e);
            return fail(500, "获取广告失败");
        }
    }

    @PostMapping("/ad/click/{id}")
    public ResponseEntity<Map<String, Object>> adClick(@PathVariable("id") String id) {
        try {
            contentService.adClick(id);
            return ResponseEntity.ok(Resp.ok("ok"));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("广告点击统计错误", e);
            return fail(500, "统计失败");
        }
    }

    // ================= 广告：管理 =================

    @RequireAdmin
    @GetMapping("/ad/admin/list")
    public ResponseEntity<Map<String, Object>> adManageList(HttpServletRequest request) {
        try {
            Map<String, Object> data = contentService.adManageList(
                    request.getParameter("page"), request.getParameter("pageSize"),
                    request.getParameter("keyword"), request.getParameter("position"));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("获取广告列表错误", e);
            return fail(500, "获取失败");
        }
    }

    @RequireAdmin
    @GetMapping("/ad/admin/detail/{id}")
    public ResponseEntity<Map<String, Object>> adManageDetail(@PathVariable("id") String id) {
        try {
            return ResponseEntity.ok(Resp.ok("获取成功", contentService.adDetail(id)));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取广告详情错误", e);
            return fail(500, "获取失败");
        }
    }

    @RequireAdmin
    @PostMapping("/ad/admin/add")
    public ResponseEntity<Map<String, Object>> adManageAdd(@RequestBody(required = false) Object rawBody) {
        try {
            Map<String, Object> data = contentService.adAdd(ContentJs.objectMap(rawBody));
            return ResponseEntity.ok(Resp.ok("新增成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("新增广告错误", e);
            return fail(500, "新增失败");
        }
    }

    @RequireAdmin
    @PutMapping("/ad/admin/update/{id}")
    public ResponseEntity<Map<String, Object>> adManageUpdate(@PathVariable("id") String id,
                                                              @RequestBody(required = false) Object rawBody) {
        try {
            contentService.adUpdate(id, ContentJs.objectMap(rawBody));
            return ResponseEntity.ok(Resp.ok("更新成功"));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("更新广告错误", e);
            return fail(500, "更新失败");
        }
    }

    @RequireAdmin
    @PutMapping("/ad/admin/status/{id}")
    public ResponseEntity<Map<String, Object>> adManageStatus(@PathVariable("id") String id,
                                                              @RequestBody(required = false) Object rawBody) {
        try {
            contentService.adSetStatus(id, ContentJs.objectMap(rawBody));
            return ResponseEntity.ok(Resp.ok("操作成功"));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("广告启停错误", e);
            return fail(500, "操作失败");
        }
    }

    @RequireAdmin
    @DeleteMapping("/ad/admin/delete/{id}")
    public ResponseEntity<Map<String, Object>> adManageDelete(@PathVariable("id") String id) {
        try {
            contentService.adDelete(id);
            return ResponseEntity.ok(Resp.ok("删除成功"));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("删除广告错误", e);
            return fail(500, "删除失败");
        }
    }

    // ================= 公告 =================

    @GetMapping("/announcement/latest")
    public ResponseEntity<Map<String, Object>> announceLatest() {
        try {
            return ResponseEntity.ok(Resp.ok("获取成功", contentService.announceLatest()));
        } catch (Exception e) {
            log.error("获取公告错误", e);
            return fail(500, "获取公告失败");
        }
    }

    @RequireAdmin
    @GetMapping("/announcement/admin/list")
    public ResponseEntity<Map<String, Object>> announceManageList(HttpServletRequest request) {
        try {
            Map<String, Object> data = contentService.announceManageList(
                    request.getParameter("page"), request.getParameter("pageSize"),
                    request.getParameter("keyword"), request.getParameter("status"));
            return ResponseEntity.ok(Resp.ok("获取成功", data));
        } catch (Exception e) {
            log.error("获取公告列表错误", e);
            return fail(500, "获取失败");
        }
    }

    @RequireAdmin
    @PostMapping("/announcement/admin/add")
    public ResponseEntity<Map<String, Object>> announceManageAdd(@RequestBody(required = false) Object rawBody) {
        try {
            Map<String, Object> data = contentService.announceAdd(ContentJs.objectMap(rawBody));
            return ResponseEntity.ok(Resp.ok("发布成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("新增公告错误", e);
            return fail(500, "发布失败");
        }
    }

    @RequireAdmin
    @PutMapping("/announcement/admin/update/{id}")
    public ResponseEntity<Map<String, Object>> announceManageUpdate(@PathVariable("id") String id,
                                                                    @RequestBody(required = false) Object rawBody) {
        try {
            contentService.announceUpdate(id, ContentJs.objectMap(rawBody));
            return ResponseEntity.ok(Resp.ok("更新成功"));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("更新公告错误", e);
            return fail(500, "更新失败");
        }
    }

    @RequireAdmin
    @PutMapping("/announcement/admin/status/{id}")
    public ResponseEntity<Map<String, Object>> announceManageStatus(@PathVariable("id") String id,
                                                                    @RequestBody(required = false) Object rawBody) {
        try {
            contentService.announceSetStatus(id, ContentJs.objectMap(rawBody));
            return ResponseEntity.ok(Resp.ok("操作成功"));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("公告启停错误", e);
            return fail(500, "操作失败");
        }
    }

    @RequireAdmin
    @DeleteMapping("/announcement/admin/delete/{id}")
    public ResponseEntity<Map<String, Object>> announceManageDelete(@PathVariable("id") String id) {
        try {
            contentService.announceDelete(id);
            return ResponseEntity.ok(Resp.ok("删除成功"));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("删除公告错误", e);
            return fail(500, "删除失败");
        }
    }

    static ResponseEntity<Map<String, Object>> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }
}
