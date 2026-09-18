package com.zhixu.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixu.message.domain.po.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}
