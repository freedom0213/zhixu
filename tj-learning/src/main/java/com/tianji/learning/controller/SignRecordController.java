package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sign-record")
@Api(tags = "签到相关接口")
@RequiredArgsConstructor
public class SignRecordController {

    private final ISignRecordService signService;


    @GetMapping()
    @ApiOperation("新增签到记录功能")
    public SignResultVO addSignRecord(){
        return signService.addSignRecord();
    }


}
