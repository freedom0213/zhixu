package com.tianji.user.service;

public interface ICodeService {
    /**
     * Sends (or locally mocks) a verification code.
     *
     * @return the code only when local mock mode is enabled; null in production
     */
    String sendVerifyCode(String phone);
    void verifyCode(String phone, String code);
}
