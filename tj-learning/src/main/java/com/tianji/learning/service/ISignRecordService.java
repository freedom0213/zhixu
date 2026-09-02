package com.tianji.learning.service;

import com.tianji.learning.domain.vo.SignResultVO;

import java.util.List;

public interface ISignRecordService {
    SignResultVO addSignRecord();

    List<Integer> queryCurrentMonthRecords();

}
