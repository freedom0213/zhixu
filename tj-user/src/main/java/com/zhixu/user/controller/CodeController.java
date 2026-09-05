package com.zhixu.user.controller;

import com.zhixu.user.service.ICodeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** Verification-code endpoint shared by registration, phone login and password reset. */
@RestController
@RequestMapping("/code")
@Api(tags = "验证码")
public class CodeController {

    private final ICodeService codeService;

    @Value("${tj.sms.mock:true}")
    private boolean mockSms;

    public CodeController(ICodeService codeService) {
        this.codeService = codeService;
    }

    @ApiOperation("发送短信验证码")
    @PostMapping("/verifycode")
    public Map<String, Object> sendVerifyCode(@RequestParam("cellPhone") String cellPhone) {
        String code = codeService.sendVerifyCode(cellPhone);
        Map<String, Object> result = new HashMap<>(2);
        result.put("mock", mockSms);
        if (mockSms) {
            result.put("code", code);
        }
        return result;
    }
}
