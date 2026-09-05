package com.zhixu.message.thirdparty;

import com.zhixu.api.dto.sms.SmsInfoDTO;
import com.zhixu.message.domain.po.MessageTemplate;

/**
 * 第三方接口对接平台
 */
public interface ISmsHandler {

    /**
     * 发送短信
     */
    void send(SmsInfoDTO platformSmsInfoDTO, MessageTemplate template);


}
