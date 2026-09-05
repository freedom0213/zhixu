package com.zhixu.user.service.impl;

import com.zhixu.message.domain.enums.SmsTemplate;
import com.zhixu.common.exceptions.BadRequestException;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.RandomUtils;
import com.zhixu.common.utils.StringUtils;
import com.zhixu.message.api.client.AsyncSmsClient;
import com.zhixu.message.domain.dto.SmsInfoDTO;
import com.zhixu.user.service.ICodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static com.zhixu.api.constants.SmsConstants.VERIFY_CODE_PARAM_NAME;
import static com.zhixu.common.constants.ErrorInfo.Msg.INVALID_VERIFY_CODE;
import static com.zhixu.user.constants.UserConstants.USER_VERIFY_CODE_KEY;
import static com.zhixu.user.constants.UserConstants.USER_VERIFY_CODE_TTL;

@Slf4j
@Service
public class CodeServiceImpl implements ICodeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private AsyncSmsClient asyncSmsClient;

    /** Local demo mode avoids external SMS credentials while retaining the production adapter. */
    @Value("${tj.sms.mock:true}")
    private boolean mockSms;

    @Override
    public String sendVerifyCode(String phone) {
        String key = USER_VERIFY_CODE_KEY + phone;
        // 1.查看code是否存在
        String code = stringRedisTemplate.opsForValue().get(key);
        if(StringUtils.isBlank(code)){
            // 2.生成随机验证码
            code = RandomUtils.randomNumbers(4);
            // 3.保存到redis
            stringRedisTemplate.opsForValue()
                    .set(USER_VERIFY_CODE_KEY + phone, code, USER_VERIFY_CODE_TTL);

        }
        // 4. Send through the configured provider in production. In local mode the
        // generated value is returned by the controller for browser-only testing.
        log.debug("短信验证码：{}", code);
        if (mockSms) {
            log.info("本地短信 Mock 已生成验证码，手机号={}，验证码={}", phone, code);
            return code;
        }
        SmsInfoDTO info = new SmsInfoDTO();
        info.setPhones(CollUtils.singletonList(phone));
        info.setTemplateCode(SmsTemplate.VERIFY_CODE.toString());
        Map<String, String> params = new HashMap<>(1);
        params.put(VERIFY_CODE_PARAM_NAME, code);
        info.setTemplateParams(params);
        asyncSmsClient.sendMessage(info);
        return null;
    }

    @Override
    public void verifyCode(String phone, String code) {
        String cacheCode = stringRedisTemplate.opsForValue().get(USER_VERIFY_CODE_KEY + phone);
        if (!StringUtils.equals(cacheCode, code)) {
            // 验证码错误
            throw new BadRequestException(INVALID_VERIFY_CODE);
        }
    }
}
