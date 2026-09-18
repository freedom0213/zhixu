package com.zhixu.media.controller;

import com.zhixu.api.dto.media.MediaMetaDTO;
import com.zhixu.media.service.IMediaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 媒资元数据查询（服务间内部调用）· P30
 * <p>
 * ⚠️ 路径刻意**不放在 `/medias/**` 下**：那个前缀在鉴权白名单里是「需要登录」的，
 * 而这里由课程服务在登记视频时调用（携带的是业务请求的登录态，未必透传）。
 */
@RestController
@RequestMapping("/media-meta")
@Api(tags = "媒资元数据（内部调用）")
@RequiredArgsConstructor
public class MediaLookupController {

    private final IMediaService mediaService;

    @GetMapping("/by-id")
    @ApiOperation("按 id 查媒资元数据；不存在返回空")
    public MediaMetaDTO byId(@RequestParam("id") Long id) {
        return mediaService.findMetaById(id);
    }

    @GetMapping("/latest-by-filename")
    @ApiOperation("按文件名查最近一次上传的媒资（id + 时长）；查不到返回空")
    public MediaMetaDTO latestByFilename(@RequestParam("filename") String filename) {
        return mediaService.findLatestByFilename(filename);
    }
}
