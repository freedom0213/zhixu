package com.zhixu.api.dto.media;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 媒资元数据（服务间调用）· P30
 * <p>
 * 只带「能被别的服务用到」的最小字段：id 用来挂到小节上，duration 用来校正时长
 * （前端预读视频元数据会失败，那时的时长是 0 —— 以媒资记录为准才靠谱）。
 */
@Data
@ApiModel("媒资元数据")
public class MediaMetaDTO {

    @ApiModelProperty("媒资id")
    private Long id;

    @ApiModelProperty("时长（秒）")
    private Float duration;
}
