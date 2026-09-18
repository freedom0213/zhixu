package com.zhixu.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.IOException;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixu.api.client.course.CourseClient;
import com.zhixu.api.client.learning.LearningClient;
import com.zhixu.api.client.user.UserClient;
import com.zhixu.api.dto.course.MediaQuoteDTO;
import com.zhixu.api.dto.course.SectionInfoDTO;
import com.zhixu.api.dto.user.UserDTO;
import com.zhixu.common.domain.dto.PageDTO;
import org.springframework.web.multipart.MultipartFile;
import com.zhixu.common.exceptions.BadRequestException;
import com.zhixu.common.exceptions.ForbiddenException;
import com.zhixu.common.utils.*;
import com.zhixu.media.constants.FileErrorInfo;
import com.zhixu.media.domain.dto.MediaDTO;
import com.zhixu.media.domain.dto.MediaUploadResultDTO;
import com.zhixu.media.domain.po.Media;
import com.zhixu.media.domain.query.MediaQuery;
import com.zhixu.media.domain.vo.MediaVO;
import com.zhixu.media.storage.MediaUploadResult;
import com.zhixu.media.domain.vo.VideoPlayVO;
import com.zhixu.media.enums.FileStatus;
import com.zhixu.media.mapper.MediaMapper;
import com.zhixu.media.service.IMediaService;
import com.zhixu.media.storage.IMediaStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.zhixu.media.constants.FileErrorInfo.MEDIA_NOT_EXISTS;
import static com.zhixu.media.constants.FileErrorInfo.MEDIA_NOT_UPLOADED;

/**
 * <p>
 * 媒资表，主要是视频文件 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-30
 */
@Service
@RequiredArgsConstructor
public class MediaServiceImpl extends ServiceImpl<MediaMapper, Media> implements IMediaService {

    private final IMediaStorage mediaStorage;

    private final CourseClient courseClient;

    private final LearningClient learningClient;

    private final UserClient userClient;

    @Override
    public String getUploadSignature() {
        return mediaStorage.getUploadSignature();
    }

    @Override
    public VideoPlayVO getPlaySignatureBySectionId(Long sectionId) {
        // 1.根据sectionId查询媒课程信息
        SectionInfoDTO sectionInfo = courseClient.sectionInfo(sectionId);
        Long courseId = sectionInfo.getCourseId();
        // 2.查询用户课程表，是否是购买过的课程
        Long lessonId = learningClient.isLessonValid(courseId);

        if(lessonId != null){
            // 2.1.是，查询媒资信息，直接获取签名
            // 先区分「没传视频」（正常状态）和「媒资记录丢了」（数据异常），别混成一句话
            AssertUtils.isNotNull(sectionInfo.getMediaId(), MEDIA_NOT_UPLOADED);
            Media media = getById(sectionInfo.getMediaId());
            AssertUtils.isNotNull(media, MEDIA_NOT_EXISTS);
            // 0）本地直链模式（P23）：mediaUrl 非空 → 不取签名，直接给播放地址
            if (media.getMediaUrl() != null && !media.getMediaUrl().isBlank()) {
                VideoPlayVO vo = new VideoPlayVO();
                vo.setFileId(media.getFileId());
                vo.setMediaUrl(media.getMediaUrl());
                return vo;
            }
            // 1）获取签名
            String signature =  mediaStorage.getPlaySignature(media.getFileId(), UserContext.getUser(), null);
            // 2）返回
            VideoPlayVO vo = new VideoPlayVO();
            vo.setSignature(signature);
            vo.setFileId(media.getFileId());
            return vo;
        }
        // 2.2.否，判断能不能播
        // P25：「试看」是**付费课**的营销手段 —— 免费课程本就对所有登录用户开放，
        //      以前这里只看 trailer，导致免费课里 trailer=0 的小节报「课程不支持试看」。
        Boolean trailer = sectionInfo.getTrailer();
        if(BooleanUtils.isFalse(sectionInfo.getFree()) && BooleanUtils.isFalse(trailer)) {
            // 2.3.付费课且未开放试看，抛出异常
            throw new ForbiddenException(FileErrorInfo.MEDIA_NOT_FREE);
        }

        // 3.免费，获取课程信息
        AssertUtils.isNotNull(sectionInfo.getMediaId(), MEDIA_NOT_UPLOADED);
        Media media = getById(sectionInfo.getMediaId());
        AssertUtils.isNotNull(media, MEDIA_NOT_EXISTS);
        // 0）本地直链模式（P23）：试看暂不截断，全段可播（本地 demo 语义）
        if (media.getMediaUrl() != null && !media.getMediaUrl().isBlank()) {
            VideoPlayVO vo = new VideoPlayVO();
            vo.setFileId(media.getFileId());
            vo.setMediaUrl(media.getMediaUrl());
            return vo;
        }
        // 4.获取签名
        String signature =  mediaStorage.getPlaySignature(
                media.getFileId(), UserContext.getUser(), sectionInfo.getFreeDuration());
        // 5.返回
        VideoPlayVO vo = new VideoPlayVO();
        vo.setSignature(signature);
        vo.setFileId(media.getFileId());
        return vo;
    }


    @Override
    public VideoPlayVO getPlaySignatureByMediaId(Long mediaId) {
        // 1.根据id查询媒资信息
        Media media = getById(mediaId);
        // 2.获取签名
        String signature =  mediaStorage.getPlaySignature(media.getFileId(), UserContext.getUser(), null);
        // 3.返回
        VideoPlayVO vo = new VideoPlayVO();
        vo.setSignature(signature);
        vo.setFileId(media.getFileId());
        return vo;
    }

    @Override
    public PageDTO<MediaVO> queryMediaPage(MediaQuery query) {
        // 1.分页条件
        Page<Media> mediaPage = new Page<>(query.getPageNo(), query.getPageSize());
        if(StringUtils.isNotBlank(query.getSortBy())){
            mediaPage.addOrder(new OrderItem(query.getSortBy(), query.getIsAsc()));
        }
        // 2.分页搜索
        lambdaQuery()
                .like(StringUtils.isNotBlank(query.getName()), Media::getFilename, query.getName())
                .page(mediaPage);
        // 3.解析数据
        List<Media> records = mediaPage.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(mediaPage);
        }
        List<Long> ids = new ArrayList<>(records.size());
        Set<Long> createIds = new HashSet<>();
        for (Media m : records) {
            ids.add(m.getId());
            createIds.add(m.getCreater());
        }
        createIds.remove(0L);
        // 4.查询引用次数
        List<MediaQuoteDTO> mediaQuoteDTOS = courseClient.mediaUserInfo(ids);
        AssertUtils.isNotEmpty(mediaQuoteDTOS, FileErrorInfo.MEDIA_QUOTE_NOT_EXISTS);
        Map<Long, Integer> quoteMap = mediaQuoteDTOS
                .stream()
                .collect(Collectors.toMap(MediaQuoteDTO::getMediaId, MediaQuoteDTO::getQuoteNum));

        // 5.查询创建者信息
        Map<Long, String> userMap = null;
        if(CollUtils.isNotEmpty(createIds)) {
            List<UserDTO> users = userClient.queryUserByIds(createIds);
            AssertUtils.isNotEmpty(users, FileErrorInfo.USER_NOT_EXISTS);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        }
        // 6.数据转换
        List<MediaVO> list = new ArrayList<>(records.size());
        for (Media m : records) {
            MediaVO v = BeanUtils.toBean(m, MediaVO.class);
            v.setUseTimes(quoteMap.get(m.getId()));
            if(userMap != null) {
                v.setCreater(userMap.get(m.getCreater()));
            }
            list.add(v);
        }
        return new PageDTO<>(mediaPage.getTotal(), mediaPage.getPages(), list);
    }

    @Override
    public MediaDTO uploadLocalVideo(MultipartFile file, Float durationSec) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择要上传的视频文件");
        }
        String name = file.getOriginalFilename();
        String ext = (name != null && name.lastIndexOf('.') >= 0)
                ? name.substring(name.lastIndexOf('.')).toLowerCase() : ".mp4";
        if (!".mp4".equals(ext) && !".webm".equals(ext) && !".mov".equals(ext) && !".m4v".equals(ext)) {
            throw new BadRequestException("只支持 mp4 / webm / mov 格式的视频");
        }
        MediaUploadResult result;
        try {
            result = mediaStorage.uploadFile(name, file.getInputStream(), file.getSize());
        } catch (IOException e) {
            throw new BadRequestException("读取上传文件失败，请重试");
        }
        // 登记媒资：fileId = 本地 key，mediaUrl = 播放直链（P23 播放靠它短路）
        Media media = new Media();
        media.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        media.setFileId(result.getFileId());
        media.setFilename(name);
        media.setMediaUrl(result.getMediaUrl());
        media.setDuration(durationSec == null ? 0f : durationSec);
        media.setSize(file.getSize());
        media.setStatus(FileStatus.UPLOADED);
        media.setCreateTime(LocalDateTime.now());
        media.setUpdateTime(LocalDateTime.now());
        media.setCreater(UserContext.getUser());
        media.setUpdater(UserContext.getUser());
        media.setDeleted(0);
        save(media);
        return BeanUtils.toBean(media, MediaDTO.class);
    }

    @Override
    public com.zhixu.api.dto.media.MediaMetaDTO findMetaById(Long id) {
        Media media = id == null ? null : getById(id);
        if (media == null) {
            return null;
        }
        com.zhixu.api.dto.media.MediaMetaDTO meta = new com.zhixu.api.dto.media.MediaMetaDTO();
        meta.setId(media.getId());
        meta.setDuration(media.getDuration());
        return meta;
    }

    @Override
    public com.zhixu.api.dto.media.MediaMetaDTO findLatestByFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        Media media = lambdaQuery()
                .eq(Media::getFilename, filename)
                .orderByDesc(Media::getId)
                .last("LIMIT 1")
                .one();
        if (media == null) {
            return null;
        }
        com.zhixu.api.dto.media.MediaMetaDTO meta = new com.zhixu.api.dto.media.MediaMetaDTO();
        meta.setId(media.getId());
        meta.setDuration(media.getDuration());
        return meta;
    }

    @Override
    public MediaDTO save(MediaUploadResultDTO result) {
        // 1.查询视频信息
        List<Media> list = mediaStorage.queryMediaInfos(result.getFileId());
        AssertUtils.isNotEmpty(list, MEDIA_NOT_EXISTS);
        // 2.判断是否存在，幂等处理
        Media media = lambdaQuery().eq(Media::getFileId, result.getFileId()).one();
        if (media != null) {
            // 已经存在并且处理过
            return BeanUtils.toBean(media, MediaDTO.class);
        }
        // 3.查询视频信息
        media = list.get(0);
        // 4.直接保存数据库
        save(list.get(0));
        return BeanUtils.toBean(media, MediaDTO.class);
    }

    @Override
    public void updateMediaProcedureResult(Media media) {
        // 1.查询fileId是否已经存在
        Media old = lambdaQuery().eq(Media::getFileId, media.getFileId()).one();
        if (old == null) {
            // 2.如果不存在，新增
            save(media);
        }else {
            // 3.存在，则更新
            lambdaUpdate()
                    .set(Media::getStatus, FileStatus.PROCESSED.getValue())
                    .set(Media::getCoverUrl, media.getCoverUrl())
                    .eq(Media::getId, old.getId())
                    .update();
        }
    }

    @Override
    @Transactional
    public void deleteMedia(String fileId) {
        // 1.删除云端文件
        mediaStorage.deleteFile(fileId);
        // 2.删除本地信息
        remove(new LambdaQueryWrapper<Media>().eq(Media::getFileId, fileId));
    }
}
