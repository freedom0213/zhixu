package com.zhixu.learning.service;

import com.zhixu.learning.domain.vo.SignResultVO;

import java.util.List;

public interface ISignRecordService {
    SignResultVO addSignRecord();

    List<Integer> queryCurrentMonthRecords();

}
