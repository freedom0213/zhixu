package com.zhixu.message.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 会话列表的一行（P22）。
 * 字段名与前端页面（学生端 messages.vue / 教师端 messages.vue）对齐：
 * otherUserId / otherUsername / otherAvatar / lastMessage / lastMessageTime / unReadCount。
 */
@Data
@Accessors(chain = true)
@ApiModel("师生对话·会话")
public class ChatConversationVO {

    private Long id;

    @ApiModelProperty("对方用户id（打开会话用它）")
    private Long otherUserId;

    @ApiModelProperty("对方显示名")
    private String otherUsername;

    @ApiModelProperty("对方头像（拿不到为 null，前端回落默认图）")
    private String otherAvatar;

    @ApiModelProperty("最后一条消息摘要")
    private String lastMessage;

    @ApiModelProperty("最后一条消息时间")
    private LocalDateTime lastMessageTime;

    @ApiModelProperty("我的未读数")
    private Integer unReadCount;
}
