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
 * 师生对话·消息（P22）。
 * 只存文本（≤500 字）；图片/文件要等上传通路，先不做。
 * 消息不物理删——撤回/删除是后续话题，表结构里先不排。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话id
     */
    private Long conversationId;

    /**
     * 发送者用户id
     */
    private Long senderId;

    /**
     * 文本内容
     */
    private String content;

    /**
     * 发送时间
     */
    private LocalDateTime pushTime;
}
