package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.PointsExchangeFormDTO;
import com.tianji.learning.domain.po.PointsExchangeRecord;
import com.tianji.learning.domain.po.PointsMallItem;
import com.tianji.learning.domain.query.PointsExchangePageQuery;
import com.tianji.learning.domain.query.PointsMallPageQuery;
import com.tianji.learning.domain.vo.PointsExchangeRecordVO;
import com.tianji.learning.domain.vo.PointsMallItemVO;

public interface IPointsMallService extends IService<PointsMallItem> {
    PageDTO<PointsMallItemVO> queryItems(PointsMallPageQuery query);
    PointsMallItemVO queryItem(Long id);
    PageDTO<PointsExchangeRecordVO> queryRecords(PointsExchangePageQuery query);
    PointsExchangeRecordVO queryRecord(Long id);
    void exchange(PointsExchangeFormDTO form);
    void cancel(Long id);
}
