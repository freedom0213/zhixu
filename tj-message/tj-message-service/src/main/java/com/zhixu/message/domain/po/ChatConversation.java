package com.zhixu.message.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 师生对话·会话（P22）。
 * 两个人只有一条会话：user_low/user_high 按 id 大小编排对（配合 uk_pair 唯一键），
 * 否则「A 先发」和「B 先发」会建出两条会话。
 * 未读数记在会话行上（各记各的），会话列表 + 角标一次查询就够。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_conversation")
public class ChatConversation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 参与者中较小的用户id
     */
    private Long userLow;

    /**
     * 参与者中较大的用户id
     */
    private Long userHigh;

    /**
     * 最后一条消息摘要（会话列表预览）
     */
    private String lastMessage;

    /**
     * 最后一条消息时间（列表按它倒序）
     */
    private LocalDateTime lastTime;

    /**
     * user_low 这一方的未读数
     */
    private Integer unreadLow;

    /**
     * user_high 这一方的未读数
     */
    private Integer unreadHigh;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
