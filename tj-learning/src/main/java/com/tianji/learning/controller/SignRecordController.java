package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/sign-record", "/sign-records"})
@Api(tags = "签到相关接口")
@RequiredArgsConstructor
public class SignRecordController {

    private final ISignRecordService signService;


    @PostMapping()
    @ApiOperation("新增签到记录功能")
    public SignResultVO addSignRecord(){
        return signService.addSignRecord();
    }

    @GetMapping()
    @ApiOperation("查询本月签到记录")
    public List<Integer> queryCurrentMonthRecords() {
        return signService.queryCurrentMonthRecords();
    }


}
