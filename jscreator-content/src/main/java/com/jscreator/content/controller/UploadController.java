package com.jscreator.content.controller;

import com.jscreator.common.api.Resp;
import com.jscreator.common.exception.BizException;
import com.jscreator.common.web.RequireLogin;
import com.jscreator.content.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.List;
import java.util.Map;

/**
 * POST /upload/image，对齐 Express 版 modules/content/upload.controller.ts：
 * 未登录 401、没有文件 400「请选择图片文件」、类型不在白名单 400、超过 5MB 400「图片不能超过 5MB」、
 * OSS 没配好 500「上传失败，请检查 OSS 配置」。
 *
 * <p>原版走 multer 的内存存储，非 multipart 请求会直接跳过解析（req.file 为 undefined）→ 400；
 * 这里同样先看请求是不是 multipart，不是就按「没选文件」处理，避免 Spring 直接回 415。
 */
@RestController
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @RequireLogin
    @PostMapping("/upload/image")
    public ResponseEntity<Map<String, Object>> upload(HttpServletRequest request) {
        MultipartFile file = null;
        try {
            if (request instanceof MultipartHttpServletRequest multipart) {
                // 原版 upload.single('image')：只要出现字段名不是 image 的文件部件，multer 就先抛
                // LIMIT_UNEXPECTED_FILE（400「上传出错：Unexpected field」），连 image 一起传也一样。
                if (hasUnexpectedFileField(multipart)) {
                    return fail(400, "上传出错：Unexpected field");
                }
                file = multipart.getFile("image");
            }
        } catch (MaxUploadSizeExceededException e) {
            return fail(400, "图片不能超过 5MB");
        }
        try {
            Map<String, Object> data = uploadService.uploadImage(file);
            return ResponseEntity.ok(Resp.ok("上传成功", data));
        } catch (BizException e) {
            return fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("上传图片错误", e);
            return fail(500, "上传失败，请检查 OSS 配置");
        }
    }

    /** 是否存在字段名不是 image 的文件部件（只有文本字段不算）。 */
    private static boolean hasUnexpectedFileField(MultipartHttpServletRequest multipart) {
        for (Map.Entry<String, List<MultipartFile>> entry : multipart.getMultiFileMap().entrySet()) {
            if (!"image".equals(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** multipart 解析失败（文件超限等）走这里，文案与原版 multer 错误中间件一致。 */
    @org.springframework.web.bind.annotation.ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return fail(400, "图片不能超过 5MB");
    }

    static ResponseEntity<Map<String, Object>> fail(int code, String message) {
        return ResponseEntity.status(code).body(Resp.fail(code, message));
    }
}
