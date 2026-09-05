package com.zhixu.message.service;

import com.zhixu.message.domain.dto.MessageTemplateDTO;
import com.zhixu.message.domain.dto.MessageTemplateFormDTO;
import com.zhixu.message.domain.query.MessageTemplatePageQuery;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.message.domain.po.MessageTemplate;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 第三方短信平台签名和模板信息 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
public interface IMessageTemplateService extends IService<MessageTemplate> {

    List<MessageTemplate> queryByNoticeTemplateId(Long id);

    Long saveMessageTemplate(MessageTemplateFormDTO messageTemplateDTO);

    void updateMessageTemplate(MessageTemplateFormDTO messageTemplateDTO);

    PageDTO<MessageTemplateDTO> queryMessageTemplates(MessageTemplatePageQuery pageQuery);

    MessageTemplateDTO queryMessageTemplate(Long id);
}
