package com.zhixu.trade.service;

import com.zhixu.trade.domain.dto.OrderDelayQueryDTO;
import com.zhixu.trade.domain.dto.PayApplyFormDTO;
import com.zhixu.trade.domain.vo.PayChannelVO;

import java.util.List;

public interface IPayService {
    List<PayChannelVO> queryPayChannels();

    String applyPayOrder(PayApplyFormDTO payApply);

    void queryPayResult(OrderDelayQueryDTO message);
}
