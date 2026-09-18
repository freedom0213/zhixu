package com.zhixu.user.constants;

import java.time.Duration;

public interface UserConstants {
    String DEFAULT_PASSWORD = "123456";

    Long STUDENT_ROLE_ID = 2L;
    String STUDENT_ROLE_NAME = "学生";

    Long TEACHER_ROLE_ID = 3L;
    // P24：用户可见称呼一律「讲师」（产品定位：人人可授课的平台，不是学校内部系统）
    String TEACHER_ROLE_NAME = "讲师";

    // 验证码的Redis key前缀
    String USER_VERIFY_CODE_KEY = "sms:user:code:phone:";
    // 验证码有效期，5分钟
    Duration USER_VERIFY_CODE_TTL = Duration.ofMinutes(5);
}
