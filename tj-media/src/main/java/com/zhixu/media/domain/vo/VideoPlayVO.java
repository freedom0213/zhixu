package com.zhixu.media.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "视频播放的签名信息")
public class VideoPlayVO {
    @ApiModelProperty(value = "视频唯一标示", example = "12412534535143242")
    private String fileId;
    @ApiModelProperty(value = "本地直链播放地址（LOCAL 平台模式返回；VOD 模式为空，走签名）")
    private String mediaUrl;
    @ApiModelProperty(value = "视频封面", example = "xxx.xxx.xxx")
    private String signature;
}
