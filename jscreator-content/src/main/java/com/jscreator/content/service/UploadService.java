package com.jscreator.content.service;

import com.jscreator.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片上传（POST /upload/image），对齐 Express 版 modules/content/upload.controller.ts：
 * 类型白名单、5MB 限制、失败文案。
 *
 * <p>本机没有 OSS AccessKey（原版 getClient 会抛「OSS 未配置」），因此上传成功分支走不到：
 * 与原版一致返回 500「上传失败，请检查 OSS 配置」。config 里两个 key 一旦配上，
 * 这里需要补 ali-oss 的 PUT 实现（V4 签名）才能真正上传。
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final String ossAccessKeyId;
    private final String ossAccessKeySecret;

    public UploadService(@Value("${OSS_ACCESS_KEY_ID:}") String ossAccessKeyId,
                         @Value("${OSS_ACCESS_KEY_SECRET:}") String ossAccessKeySecret) {
        this.ossAccessKeyId = ossAccessKeyId;
        this.ossAccessKeySecret = ossAccessKeySecret;
    }

    /** 返回 {@code {url}}；校验失败抛 400，上传失败抛 500。 */
    public Map<String, Object> uploadImage(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isEmpty()) {
            throw BizException.badRequest("请选择图片文件");
        }
        String mime = file.getContentType();
        String ext = (mime == null || mime.isEmpty()) ? null : ALLOWED.get(mime);
        if (ext == null) {
            throw BizException.badRequest("仅支持 jpg/png/webp/gif 图片");
        }
        try {
            String url = uploadToOss(file.getBytes(), ext, mime);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", url);
            return data;
        } catch (Exception e) {
            log.error("上传图片错误", e);
            throw new BizException(500, "上传失败，请检查 OSS 配置");
        }
    }

    private String uploadToOss(byte[] buffer, String ext, String mime) throws Exception {
        if (ossAccessKeyId == null || ossAccessKeyId.isEmpty() || ossAccessKeySecret == null || ossAccessKeySecret.isEmpty()) {
            throw new IllegalStateException("OSS 未配置：请在 .env 设置 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET");
        }
        // 原版：uploads/<yyyyMMdd>/<timestamp>_<6 位随机>.<ext>，bucket fast-node-server / 杭州 region
        throw new IllegalStateException("OSS 上传实现待补（config 里 OSS key 配好后启用）");
    }
}
