package com.zhixu.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.zhixu.common.autoconfigure.mq.RabbitMqHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixu.common.constants.MqConstants;
import com.zhixu.common.exceptions.BadRequestException;
import com.zhixu.common.exceptions.BizIllegalException;
import com.zhixu.common.utils.BeanUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.promotion.constants.PromotionConstants;
import com.zhixu.promotion.domain.dto.UserCouponDTO;
import com.zhixu.promotion.domain.po.Coupon;
import com.zhixu.promotion.domain.po.ExchangeCode;
import com.zhixu.promotion.domain.po.UserCoupon;
import com.zhixu.promotion.domain.query.UserCouponQuery;
import com.zhixu.promotion.domain.vo.UserCouponVO;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.promotion.enums.ExchangeCodeStatus;
import com.zhixu.promotion.mapper.CouponMapper;
import com.zhixu.promotion.mapper.UserCouponMapper;
import com.zhixu.promotion.service.IExchangeCodeService;
import com.zhixu.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixu.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.LinkedHashMap;
import com.zhixu.promotion.enums.UserCouponStatus;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-21
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;

    @Override
    public List<Map<String, Object>> queryAvailableCoupons(Integer amount) {
        List<UserCoupon> records = lambdaQuery().eq(UserCoupon::getUserId, UserContext.getUser())
                .eq(UserCoupon::getStatus, UserCouponStatus.UNUSED).gt(UserCoupon::getTermEndTime, LocalDateTime.now()).list();
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserCoupon record : records) {
            Coupon c = couponMapper.selectById(record.getCouponId());
            if (c == null || (c.getThresholdAmount() != null && amount < c.getThresholdAmount())) continue;
            int discount = c.getDiscountType() == com.zhixu.promotion.enums.DiscountType.NO_THRESHOLD ? c.getDiscountValue() : c.getDiscountValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ids", Collections.singletonList(record.getId())); item.put("rules", Collections.singletonList(c.getName())); item.put("discountAmount", discount); item.put("couponId", record.getId());
            result.add(item);
        }
        return result;
    }

    private final IExchangeCodeService codeService;

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;

    /**
     * 用户领取优惠劵
     * 升级：查询优惠劵操作和检验每人限领数量均在redis中进行
     * @param couponId 优惠劵id
     */
    @Override
    public void receiveCoupon(Long couponId) {
        //1.查询优惠劵 在redis中
        Coupon coupon = queryCouponByCache(couponId);
        if(coupon == null){
            throw new BadRequestException("优惠券不存在");
        }
        //2.检验发放时间
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())){
            throw new BadRequestException("优惠券发放已经结束或尚未开始");
        }
        // Redis stores the remaining inventory. The previous implementation
        // read issueNum from a cache hash that never contained that field.
        String couponKey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        Long remaining = redisTemplate.opsForHash().increment(couponKey, "availableNum", -1);
        if (remaining == null || remaining < 0) {
            redisTemplate.opsForHash().increment(couponKey, "availableNum", 1);
            throw new BadRequestException("优惠券库存不足");
        }
        /*
        淘汰方案：使用synchronized悲观锁 只针对单体项目有效
        //4.检验并新增用户劵包括(检验用户限领数量 更新优惠劵已领取数量 新增用户劵)
        Long userId = UserContext.getUser();
        //先上锁再添加事务
        synchronized (userId.toString().intern()) {  //intern代表将userId.toString()放入常量池中
            //通过AopContext.currentProxy();获取当前Bean的Spring代理对象 再利用当前Bean的Spring代理对象调用事务方法
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            userCouponService.checkAndCreateUserCoupon(coupon,userId,null);
        }
         */
        /*新方案1：使用redis基础的分布式锁setIfAbsent
        会发现一些问题比如：锁误删问题 超时释放问题 所以在新方案2中我们采用redisson
        //4.检验并生成用户劵
        Long userId = UserContext.getUser();
        String key = "lock:coupon:uid:" + userId;
        //4.1 创建锁的对象 这里传递的key redisTemplate还没有使用 是为了让类方法使用
        RedisLock redisLock = new RedisLock(key, redisTemplate);
        //4.2 获取锁 如果已存在锁 那么返回false
        boolean isLock = redisLock.tryLock(5, TimeUnit.SECONDS);
        //4.3 判断是否成功
        if(!isLock){
            throw new BizIllegalException("请求太频繁了");
        }
        try{
            //4.4 获取锁成功 执行业务
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            userCouponService.checkAndCreateUserCoupon(coupon,userId,null);
        }finally {
            //4.5 不管过程如何 一定释放锁
            redisLock.unlock();
        }
         */
        //新方案2：采用redisson的分布式锁 其实跟采用redis基础的分布式锁挺像的
        //但对于锁误删问题 redisson底层用的是原子性的LUA脚本 这个脚本让判断和删除过程是原子执行的 所以彻底杜绝了锁误删的可能性
        //且对于超时删除问题 redisson底层用的是看门狗机制 当调用tryLock()无参的时候 意思就是交给看门狗处理 给一个默认过期时间 然后看门狗会有任务搁一定时间
        //检查该进程是否有锁 如果没有锁会到过期时间自动释放 如果有锁 看门狗会自动续期过期时间
        //！！！但会发现创建锁对象和获取锁 释放锁等是固定逻辑 只有执行业务等逻辑是不一样的
        //所以这里我们将redisson分布式锁的使用AOP化 也就是切面化 这里我们基于注解来标记

        Long userId = UserContext.getUser();
        //4.检验每人限领数量
        // 4.1.查询领取数量 在redis中查
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        // 4.2.校验限领数量
        if(count > coupon.getUserLimit()){
            redisTemplate.opsForHash().increment(key, userId.toString(), -1);
            redisTemplate.opsForHash().increment(couponKey, "availableNum", 1);
            throw new BadRequestException("超出领取数量");
        }

        // 6.发送MQ消息
        UserCouponDTO uc = new UserCouponDTO();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);


    }

    //根据优惠劵id在redis中查询优惠劵信息
    private Coupon queryCouponByCache(Long couponId) {
        // 1.准备KEY
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        // 2.查询
        Map<Object, Object> objMap = redisTemplate.opsForHash().entries(key);
        if (objMap.isEmpty()) {
            return null;
        }
        // 3.数据反序列化
        return BeanUtils.mapToBean(objMap, Coupon.class, false, CopyOptions.create());
    }

    /**
     * 兑换码兑换优惠劵
     * @param code 兑换码
     */
    @Override
    public void exchangeCoupon(String code) {
        // 1.校验并解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        // 2.校验是否已经兑换 SETBIT KEY 4 1
        boolean exchanged = codeService.updateExchangeMark(serialNum, true);
        if (exchanged) {
            throw new BizIllegalException("兑换码已经被兑换过了");
        }
        try {
            // 3.查询兑换码对应的优惠券id
            Long couponId = codeService.exchangeTargetId(serialNum);
            if (couponId == null) {
                throw new BizIllegalException("兑换码不存在！");
            }
            Coupon coupon = queryCouponByCache(couponId);
            if (coupon == null) {
                throw new BizIllegalException("优惠券不存在或尚未初始化");
            }
            // 4.是否过期
            LocalDateTime now = LocalDateTime.now();
            if (coupon != null && (now.isAfter(coupon.getIssueEndTime()) || now.isBefore(coupon.getIssueBeginTime()))) {
                throw new BizIllegalException("优惠券活动未开始或已经结束");
            }

            // 5.校验每人限领数量
            Long userId = UserContext.getUser();
            // 5.1.查询领取数量
            String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
            Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
            // 5.2.校验限领数量
            if(count > coupon.getUserLimit()){
                redisTemplate.opsForHash().increment(key, userId.toString(), -1);
                throw new BadRequestException("超出领取数量");
            }
            // 6.发送MQ消息通知 通知生成用户劵并修改优惠劵状态
            UserCouponDTO uc = new UserCouponDTO();
            uc.setUserId(userId);
            uc.setCouponId(couponId);
            uc.setSerialNum((int) serialNum);
            mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);
        } catch (Exception e) {
            // 重置兑换的标记 0
            codeService.updateExchangeMark(serialNum, false);
            throw e;
        }


    }

    // 移除了锁，这里不需要加锁了
    @Transactional
    @Override
    public void checkAndCreateUserCoupon(UserCouponDTO dto) {
        Coupon coupon = couponMapper.selectById(dto.getCouponId());
        if(coupon == null){
            //注意不能抛异常，因为该方法是MQ消息的消费者，如果是消费者抛异常那么监听器将会进行重试，
            //既然需要修改的优惠券都不存在了，那也没必要进行重试了，所以直接返回空即可
            return;
        }
        //保存用户优惠券领取记录
        saveUserCoupon(coupon, dto.getUserId());
        //更新优惠券领取数量+1
        //MyBatis写法
        int r = couponMapper.incrIssueNum(coupon.getId());
        if(r == 0){
            return;
        }
        //如果是兑换码，还需要将DB中的兑换码状态置为已兑换
        if(dto.getSerialNum() != null) {
            codeService.lambdaUpdate()
                    .eq(ExchangeCode::getId, dto.getSerialNum())
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .set(ExchangeCode::getUserId,dto.getUserId())
                    .update();
        }
    }

    private void saveUserCoupon(Coupon coupon, Long userId) {
        UserCoupon uc = new UserCoupon();
        uc.setCouponId(coupon.getId());
        uc.setUserId(userId);
        uc.setStatus(com.zhixu.promotion.enums.UserCouponStatus.UNUSED);

        LocalDateTime beginTime = coupon.getTermBeginTime();
        LocalDateTime endTime = coupon.getTermEndTime();
        if(beginTime == null){
            beginTime = LocalDateTime.now();
        }
        if(endTime == null){
            endTime = beginTime.plusDays(coupon.getTermDays() == null ? 30 : coupon.getTermDays());
        }
        uc.setTermBeginTime(beginTime);
        uc.setTermEndTime(endTime);

        save(uc);
    }

    @Override
    public PageDTO<UserCouponVO> queryMyCoupons(UserCouponQuery query) {
        Long userId = UserContext.getUser();
        Page<UserCoupon> page = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(query.getStatus() != null, UserCoupon::getStatus, query.getStatus())
                .orderByDesc(UserCoupon::getCreateTime)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        if (page.getRecords() == null || page.getRecords().isEmpty()) {
            return PageDTO.empty(page);
        }
        List<Long> couponIds = page.getRecords().stream().map(UserCoupon::getCouponId).distinct().collect(Collectors.toList());
        Map<Long, Coupon> coupons = new HashMap<>();
        couponMapper.selectBatchIds(couponIds).forEach(c -> coupons.put(c.getId(), c));
        List<UserCouponVO> result = page.getRecords().stream().map(uc -> {
            UserCouponVO vo = BeanUtils.toBean(uc, UserCouponVO.class);
            Coupon coupon = coupons.get(uc.getCouponId());
            if (coupon != null) {
                vo.setName(coupon.getName());
                vo.setSpecific(coupon.getSpecific());
                vo.setDiscountType(coupon.getDiscountType());
                vo.setThresholdAmount(coupon.getThresholdAmount());
                vo.setDiscountValue(coupon.getDiscountValue());
                vo.setMaxDiscountAmount(coupon.getMaxDiscountAmount());
                vo.setTermDays(coupon.getTermDays());
            }
            return vo;
        }).collect(Collectors.toList());
        return PageDTO.of(page, result);
    }
}
