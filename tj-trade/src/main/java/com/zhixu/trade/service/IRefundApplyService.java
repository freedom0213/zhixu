package com.zhixu.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.pay.sdk.dto.RefundResultDTO;
import com.zhixu.trade.domain.dto.ApproveFormDTO;
import com.zhixu.trade.domain.dto.RefundCancelDTO;
import com.zhixu.trade.domain.dto.RefundFormDTO;
import com.zhixu.trade.domain.po.RefundApply;
import com.zhixu.trade.domain.query.RefundApplyPageQuery;
import com.zhixu.trade.domain.vo.RefundApplyPageVO;
import com.zhixu.trade.domain.vo.RefundApplyVO;

import java.util.List;

/**
 * <p>
 * 退款申请 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */
public interface IRefundApplyService extends IService<RefundApply> {

    List<RefundApply> queryByDetailId(Long id);

    void applyRefund(RefundFormDTO refundFormDTO);

    PageDTO<RefundApplyPageVO> queryRefundApplyByPage(RefundApplyPageQuery pageQuery);

    RefundApplyVO queryRefundDetailById(Long id);

    RefundApplyVO nextRefundApplyToApprove();

    void approveRefundApply(ApproveFormDTO approveDTO);

    void cancelRefundApply(RefundCancelDTO cancelDTO);

    RefundApplyVO queryRefundDetailByDetailId(Long id);

    void handleRefundResult(RefundResultDTO refundResult);

    List<RefundApply> queryApplyToSend(int page, int size);

    void sendRefundRequest(RefundApply refundApply);

    boolean checkRefundStatus(RefundApply refundApply);
}
