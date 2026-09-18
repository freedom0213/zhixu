package com.zhixu.media.storage.local;

import com.zhixu.media.domain.po.Media;
import com.zhixu.media.storage.IMediaStorage;
import com.zhixu.media.storage.MediaUploadResult;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 本地媒资存储（P23）：配合 LOCAL 平台模式使用。
 * -----------------------------------------------------------------------------
 * VOD 模式是「客户端拿签名直传腾讯云」；本地模式改走**服务端 multipart 上传**：
 *   POST /ms/medias/upload → LocalMediaStorage.uploadFile() 落盘 → media 表登记
 * 播放也不再要签名：media.mediaUrl 直接是 /ms/media-stream/{key} 直链
 * （getPlaySignatureBySectionId 里对 mediaUrl 非空的媒资短路返回）。
 */
public class LocalMediaStorage implements IMediaStorage {

    private final LocalFileStorage fileStorage;

    public LocalMediaStorage(LocalFileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    @Override
    public String getUploadSignature() {
        // 本地模式没有「签名」概念：前端直接调 /ms/medias/upload
        return "";
    }

    @Override
    public String getPlaySignature(String fileId, Long userId, Integer freeExpire) {
        // 本地模式播放走 mediaUrl 直链，不走签名（见 MediaServiceImpl 的短路分支）
        return "";
    }

    @Override
    public MediaUploadResult uploadFile(String filename, InputStream inputStream, long contentLength) {
        String key = newKey(filename);
        fileStorage.save(key, inputStream);
        MediaUploadResult result = new MediaUploadResult();
        result.setFileId(key);
        result.setMediaUrl("/ms/media-stream/" + key);
        result.setFilename(filename);
        return result;
    }

    @Override
    public void deleteFile(String fileId) {
        fileStorage.deleteFile(fileId);
    }

    @Override
    public void deleteFiles(List<String> fileIds) {
        fileStorage.deleteFiles(fileIds);
    }

    @Override
    public List<Media> queryMediaInfos(String... fileIds) {
        // 本地模式无远端元数据可查；调用方按空处理即可
        return Collections.emptyList();
    }

    /** 生成安全文件名：uuid + 原扩展名（扩展名只用来定 Content-Type，不参与路径） */
    private String newKey(String filename) {
        String ext = ".mp4";
        if (filename != null && filename.lastIndexOf('.') >= 0) {
            ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        }
        return UUID.randomUUID().toString().replace("-", "") + ext;
    }
}
