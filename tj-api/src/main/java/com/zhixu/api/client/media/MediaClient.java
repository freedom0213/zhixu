package com.zhixu.api.client.media;

import com.zhixu.api.dto.media.MediaMetaDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 媒资服务客户端（P30）：按文件名反查最近一次上传的媒资元数据 */
@FeignClient(contextId = "media", value = "media-service")
public interface MediaClient {

    /**
     * 按文件名查最近一次上传的媒资（查不到返回 null）。
     * 既用于「登记时 mediaId 缺失」的兜底，也用于取媒资记录里的**真实时长**。
     */
    @GetMapping("/media-meta/latest-by-filename")
    MediaMetaDTO latestByFilename(@RequestParam("filename") String filename);

    /**
     * 按 id 查媒资元数据（不存在返回 null）。
     * ⚠️ 登记时**必须**用它校验前端带来的 mediaId —— 否则前端传个不存在的 id
     *    照样会落库，学生端播放时就报「媒资不存在」，等于换了一种半截数据（P30）。
     */
    @GetMapping("/media-meta/by-id")
    MediaMetaDTO findMetaById(@RequestParam("id") Long id);
}
