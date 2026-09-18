package com.zhixu.message.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 一条聊天消息（P22）。字段与前端页面对齐：senderId / senderIcon / content / pushTime。
 */
@Data
@Accessors(chain = true)
@ApiModel("师生对话·消息")
public class ChatMessageVO {

    private Long id;

    @ApiModelProperty("发送者用户id（前端拿它区分左右气泡）")
    private Long senderId;

    @ApiModelProperty("发送者头像（拿不到为 null，前端回落默认图）")
    private String senderIcon;

    private String content;

    @ApiModelProperty("发送时间")
    private LocalDateTime pushTime;
}
