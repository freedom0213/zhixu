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

    void updateMediaProcedureResult(Media media);

    void deleteMedia(String fileId);

    VideoPlayVO getPlaySignatureByMediaId(Long mediaId);

    PageDTO<MediaVO> queryMediaPage(MediaQuery query);
}
