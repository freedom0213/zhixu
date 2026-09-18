package com.zhixu.media.controller;

import com.zhixu.media.storage.local.LocalFileStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地媒资流播放（P23，仅 LOCAL 模式装配）。
 * -----------------------------------------------------------------------------
 * 返回 FileSystemResource 时 Spring MVC 会自动处理 Range 请求 —— 视频拖进度条必需。
 * key 由 LocalMediaStorage 生成（uuid + 扩展名，无路径分隔符），并经 safeResolve 二次校验。
 */
@RestController
@ConditionalOnProperty(prefix = "tj.file", name = "platform", havingValue = "LOCAL")
public class LocalMediaStreamController {

    private final LocalFileStorage fileStorage;

    public LocalMediaStreamController(LocalFileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    @GetMapping("/media-stream/{key:.+}")
    public ResponseEntity<Resource> stream(@PathVariable("key") String key) {
        Resource resource = fileStorage.loadAsResource(key);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(resolveMediaType(key))
                .body(resource);
    }

    private MediaType resolveMediaType(String key) {
        String k = key.toLowerCase();
        if (k.endsWith(".webm")) {
            return MediaType.parseMediaType("video/webm");
        }
        if (k.endsWith(".mov") || k.endsWith(".m4v")) {
            return MediaType.parseMediaType("video/quicktime");
        }
        return MediaType.parseMediaType("video/mp4");
    }
}
