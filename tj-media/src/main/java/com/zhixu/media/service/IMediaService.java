package com.zhixu.media.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.media.domain.dto.MediaDTO;
import com.zhixu.media.domain.dto.MediaUploadResultDTO;
import com.zhixu.media.domain.po.Media;
import com.zhixu.media.domain.query.MediaQuery;
import com.zhixu.media.domain.vo.MediaVO;
import com.zhixu.media.domain.vo.VideoPlayVO;

/**
 * <p>
 * 媒资表，主要是视频文件 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-30
 */
public interface IMediaService extends IService<Media> {

    String getUploadSignature();

    VideoPlayVO getPlaySignatureBySectionId(Long fileId);

    MediaDTO save(MediaUploadResultDTO mediaResult);

    /**
     * 本地上传视频（P23，LOCAL 平台模式）：multipart 落盘 + 登记 media 表。
     * @param durationSec 时长（秒），前端读视频元数据后传入；null 则落 0
     */
    MediaDTO uploadLocalVideo(org.springframework.web.multipart.MultipartFile file, Float durationSec);

    /**
     * 按文件名查最近一次上传的媒资（P30）。
     * 用于上传登记时 mediaId 缺失的兜底 + 给出媒资记录里的**真实时长**，
     * 避免写出「有文件名、没有媒资」的半截数据。
     */
    com.zhixu.api.dto.media.MediaMetaDTO findLatestByFilename(String filename);

    /** 按 id 查媒资元数据（P30）：登记时用来**校验**前端带来的 mediaId 是真的存在 */
    com.zhixu.api.dto.media.MediaMetaDTO findMetaById(Long id);

    void updateMediaProcedureResult(Media media);

    void deleteMedia(String fileId);

    VideoPlayVO getPlaySignatureByMediaId(Long mediaId);

    PageDTO<MediaVO> queryMediaPage(MediaQuery query);
}
