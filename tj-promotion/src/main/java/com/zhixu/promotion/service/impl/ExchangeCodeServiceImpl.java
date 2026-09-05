package com.zhixu.promotion.service.impl;

import com.zhixu.common.utils.CollUtils;
import com.zhixu.promotion.domain.po.Coupon;
import com.zhixu.promotion.domain.po.ExchangeCode;
import com.zhixu.promotion.mapper.ExchangeCodeMapper;
import com.zhixu.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixu.promotion.utils.CodeUtil;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.zhixu.promotion.constants.PromotionConstants.*;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-20
 */
@Service
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {


    private final StringRedisTemplate redisTemplate;
    private final BoundValueOperations<String, String> serialOps;

    public ExchangeCodeServiceImpl(StringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
            this.serialOps = redisTemplate.boundValueOps(COUPON_CODE_SERIAL_KEY);  //绑定key
        }

        /**
         * 异步生成兑换码
         * @param coupon 优惠劵对象
          */
        @Override
        @Async("generateExchangeCodeExecutor")
        public void asyncGenerateCode(Coupon coupon) {
            // 发放数量
            Integer totalNum = coupon.getTotalNum();
            // 1.获取Redis自增序列号
            Long result = serialOps.increment(totalNum);
            if (result == null) {
                return;
            }
            int maxSerialNum = result.intValue();  //最大序列号
            List<ExchangeCode> list = new ArrayList<>(totalNum);
            for (int serialNum = maxSerialNum - totalNum + 1; serialNum <= maxSerialNum; serialNum++) {
                // 2.生成兑换码
                String code = CodeUtil.generateCode(serialNum, coupon.getId());
                ExchangeCode e = new ExchangeCode();
                e.setCode(code);
                e.setId(serialNum);
                e.setExchangeTargetId(coupon.getId());
                e.setExpiredTime(coupon.getIssueEndTime());
                list.add(e);
            }
            // 3.保存数据库
            saveBatch(list);

            // 4.写入Redis缓存，member：couponId，score：兑换码的最大序列号
            redisTemplate.opsForZSet().add(COUPON_RANGE_KEY, coupon.getId().toString(), maxSerialNum);
        }

    /**
     * 更新兑换记录
     * @param serialNum  兑换码id
     * @param b   更新后的状态
     * @return  返回值false代表没有被兑换过 true代表已经被兑换过了
     */
    @Override
    public boolean updateExchangeMark(long serialNum, boolean b) {
        Boolean boo = redisTemplate.opsForValue().setBit(COUPON_CODE_MAP_KEY, serialNum, b);
        return boo != null && boo;
    }

    /**
     * 根据兑换码序列号查询优惠劵id
     * @param serialNum 兑换码序列号
     * @return 优惠劵id
     */
    @Override
    public Long exchangeTargetId(long serialNum) {
        // 1.查询score值比当前序列号大的第一个优惠券
        Set<String> results = redisTemplate.opsForZSet().rangeByScore(
                COUPON_RANGE_KEY, serialNum, serialNum + 5000, 0L, 1L);
        if (CollUtils.isEmpty(results)) {
            return null;
        }
        // 2.数据转换
        String next = results.iterator().next();
        return Long.parseLong(next);
    }
}
