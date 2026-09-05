package com.zhixu.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.learning.domain.dto.PointsExchangeFormDTO;
import com.zhixu.learning.domain.po.PointsExchangeRecord;
import com.zhixu.learning.domain.po.PointsMallItem;
import com.zhixu.learning.domain.query.PointsExchangePageQuery;
import com.zhixu.learning.domain.query.PointsMallPageQuery;
import com.zhixu.learning.domain.vo.PointsExchangeRecordVO;
import com.zhixu.learning.domain.vo.PointsMallItemVO;

public interface IPointsMallService extends IService<PointsMallItem> {
    PageDTO<PointsMallItemVO> queryItems(PointsMallPageQuery query);
    PointsMallItemVO queryItem(Long id);
    PageDTO<PointsExchangeRecordVO> queryRecords(PointsExchangePageQuery query);
    PointsExchangeRecordVO queryRecord(Long id);
    void exchange(PointsExchangeFormDTO form);
    void cancel(Long id);
}
