package com.zhixu.media.storage.local;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 本地磁盘文件存储（P23）。
 * -----------------------------------------------------------------------------
 * 用户决策（2026-09-17）：本地环境不配任何云厂商凭证 —— 以前这里只有 ALI/TENCENT 两种实现，
 * 都依赖云上凭证才能装配 → 缺 bean → media-service 崩溃循环（「媒资服务未启用」的根因）。
 *
 * 配置 `tj.file.platform=LOCAL` 时启用；文件落在 `tj.file.local.base-path`（容器内挂卷 /data/media）。
 */
public class LocalFileStorage implements com.zhixu.media.storage.IFileStorage, org.springframework.beans.factory.InitializingBean {

    private final String basePath;

    public LocalFileStorage(String basePath) {
        this.basePath = basePath == null || basePath.isBlank() ? "/data/media" : basePath;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 启动时把存储目录建好（容器里目录挂的是卷，首次为空）
        Files.createDirectories(Paths.get(basePath));
    }

    public String getBasePath() {
        return basePath;
    }

    /**
     * 落盘保存。
     * @param key 已生成的唯一文件名（uuid.ext，**不含路径**，防路径穿越）
     */
    public String save(String key, InputStream inputStream) {
        Path target = safeResolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("保存文件到本地存储失败：" + key, e);
        }
        return key;
    }

    /** 读文件为 Resource：Spring MVC 对 Resource 自动支持 Range（视频拖进度条必需） */
    public Resource loadAsResource(String key) {
        Path target = safeResolve(key);
        if (!Files.exists(target)) {
            return null;
        }
        return new FileSystemResource(target);
    }

    @Override
    public String uploadFile(String key, InputStream inputStream, long contentLength) {
        return save(key, inputStream);
    }

    @Override
    public InputStream downloadFile(String key) {
        try {
            return new FileInputStream(safeResolve(key).toFile());
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("本地存储中不存在该文件：" + key, e);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            Files.deleteIfExists(safeResolve(key));
        } catch (IOException e) {
            // 删除失败不抛：与云存储行为保持一致（幂等）
        }
    }

    @Override
    public void deleteFiles(List<String> keys) {
        if (keys != null) {
            keys.forEach(this::deleteFile);
        }
    }

    /** 防路径穿越：key 只允许「文件名」本身，拼出来的路径必须仍在 base 目录内 */
    private Path safeResolve(String key) {
        if (key == null || key.isBlank() || key.contains("..") || key.contains("/") || key.contains("\\")) {
            throw new IllegalArgumentException("非法的文件标识：" + key);
        }
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path target = base.resolve(key).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法的文件标识：" + key);
        }
        return target;
    }
}
