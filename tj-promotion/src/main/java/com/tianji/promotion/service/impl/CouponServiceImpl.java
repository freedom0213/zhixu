package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tianji.promotion.enums.CouponStatus.*;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-20
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final CouponScopeServiceImpl csService;

    private final IExchangeCodeService iExchangeCodeService;

    private final UserCouponServiceImpl userCouponService;

    private final StringRedisTemplate redisTemplate;

    /**
     * 新增优惠劵
     * @param cfDTO 优惠劵相关信息
     */
    @Override
    public void saveCoupon(CouponFormDTO cfDTO) {
        //因为dto和po数据属性比较相近 所以可以直接转po 但是 如果有使用范围还需要保存到额外的db中
        //因为优惠卷与使用范围是n对n关系 所以需要额外db保存这种关系
        //1.保存优惠劵
        Coupon coupon = BeanUtils.copyBean(cfDTO, Coupon.class);
        this.save(coupon);
        //2.先判断是否有限定范围
        if( !cfDTO.getSpecific() ){
            //没有限定
            return;
        }
        //进行限定了
        //3.保存优惠卷范围db
        Long couponId = coupon.getId();
        List<Long> scopes = cfDTO.getScopes();
        if(CollUtils.isEmpty(scopes)){

        }
        //转po
        List<CouponScope> list = scopes
                .stream()
                .map(bizId -> new CouponScope().setCouponId(couponId).setBizId(bizId))
                .collect(Collectors.toList());
        //批量保存
        csService.saveBatch(list);
    }

    /**
     * 分页查询优惠卷
     * @param query 分页参数以及过滤条件
     * @return 返回值是一个CouponPageVO集合
     */

    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        //1.先获取查询条件
        //1.1   优惠卷折扣类型
        Integer type = query.getType();
        //1.2   优惠卷状态
        Integer status = query.getStatus();
        //1.3   优惠卷名字
        String name = query.getName();
        //1.4   查询
        Page<Coupon> page = lambdaQuery()
                .ge(type != null, Coupon::getType, type)
                .eq(status != null, Coupon::getStatus, status)
                .like(name != null, Coupon::getName, name)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        //2.获取数据 健壮性处理
        List<Coupon> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //3.转换vo
        List<CouponPageVO> couponPageVOS = BeanUtils.copyList(records, CouponPageVO.class);
        return PageDTO.of(page,couponPageVOS);


    }
    /**
     * 发放优惠劵
     * 实际上就是修改优惠劵中的状态
     * 使用redis缓存技术 也就是发放优惠劵的时候 将优惠劵基础信息保存到redis中
     * @param dto 发放参数
     */
    @Override
    @Transactional
    public void beginIssue(CouponIssueFormDTO dto) {
        // 1.查询优惠券
        Coupon coupon = getById(dto.getId());
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }
        // 2.判断优惠券状态，是否是暂停或待发放
        if(coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != PAUSE){
            throw new BizIllegalException("优惠券状态错误！");
        }
        // 3.判断是否是立刻发放
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        boolean isBegin = issueBeginTime == null || !issueBeginTime.isAfter(now);
        // 4.更新优惠券
        // 4.1.拷贝属性到PO
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        // 4.2.更新状态
        if (isBegin) {
            c.setStatus(ISSUING);
            c.setIssueBeginTime(now);
        }else{
            c.setStatus(UN_ISSUE);
        }
        // 4.3.写入数据库
        updateById(c);
        //  5.添加缓存 前提是立刻发放
        if(isBegin){
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            coupon.setIssueEndTime(c.getIssueEndTime());
            cacheCouponInfo(coupon);
        }

        // 6 兑换码生成 判断是否需要生成兑换码：优惠劵类型必须是兑换码 优惠劵状态必须是待发放
        if(coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == DRAFT){
            coupon.setIssueEndTime(c.getIssueBeginTime());
            iExchangeCodeService.asyncGenerateCode(coupon); //异步生成兑换码
        }
    }
    private void cacheCouponInfo(Coupon coupon) {
        //缓存中保存的数据只有四份：发放开始时间 发放结束时间 优惠劵总共数量 优惠劵用户限领数量
        // 1.组织数据
        Map<String, String> map = new HashMap<>(4);
        map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
        map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
        map.put("totalNum", String.valueOf(coupon.getTotalNum()));
        map.put("availableNum", String.valueOf(Math.max(0, coupon.getTotalNum() - coupon.getIssueNum())));
        map.put("userLimit", String.valueOf(coupon.getUserLimit()));
        // 2.写缓存
        redisTemplate.opsForHash().putAll(PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId(), map);
    }

    @PostConstruct
    public void initLocalCouponCache() {
        // A persistent Docker volume skips /docker-entrypoint-initdb.d after
        // its first boot, so repopulate the Redis coupon cache on every start.
        lambdaQuery().eq(Coupon::getStatus, ISSUING).list().forEach(this::cacheCouponInfo);
    }

    /**
     * 优惠劵暂停发放 优惠劵过期的时候 清理缓存逻辑
     * 这里是手动删除 当优惠劵过期的时候需要定义一个定时任务 扫描优惠劵时候过期并删除缓存
     * @param id 优惠劵id
     */
    @Override
    public void pauseIssue(Long id) {
        // 1.查询旧优惠券
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }

        // 2.当前券状态必须是未开始或进行中
        CouponStatus status = coupon.getStatus();
        if (status != UN_ISSUE && status != ISSUING) {
            // 状态错误，直接结束
            return;
        }

        // 3.更新状态
        boolean success = lambdaUpdate()
                .set(Coupon::getStatus, PAUSE)
                .eq(Coupon::getId, id)
                .in(Coupon::getStatus, UN_ISSUE, ISSUING)
                .update();
        if (!success) {
            // 可能是重复更新，结束
            log.error("重复暂停优惠券");
        }

        // 4.删除缓存
        redisTemplate.delete(PromotionConstants.COUPON_CACHE_KEY_PREFIX + id);
    }

    /**
     * 查询发放中的优惠劵
     * 同时展示是否可以领取优惠劵 领取完后展示去使用效果
     * @return 优惠劵VO集合
     */
    @Override
    public List<CouponVO> queryIssuingCoupons() {
        //1.查询发放中的优惠劵
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if(CollUtils.isEmpty(coupons)){
            return CollUtils.emptyList();
        }

        //2.用户已领取优惠劵集合
        //2.1先获取所有用户领取优惠劵id集合
        List<Long> couponIds  = coupons.stream()
                .map(Coupon::getId)
                .collect(Collectors.toList());
        //2.2查询此用户领取优惠劵数据集合
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();
        //2.3统计当前用户已经领取的优惠劵的领取数量(是否可以领取) Collectors.counting()作用是统计分组后的数量
        Map<Long, Long> issuedMap = userCoupons.stream()
                    .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        //2.4统计当前用户对优惠劵的已经领取且未使用的数量(是否可以使用)
        Map<Long, Long> unusedMap = userCoupons.stream()
                .filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        //3.转成vo
        List<CouponVO> voList = new ArrayList<>(coupons.size());
        for(Coupon c : coupons){
            //赋值基本属性
            CouponVO vo = BeanUtils.copyBean(c , CouponVO.class);
            voList.add(vo);
            //赋值available与received
            //3.1 是否可以领取： available 判断优惠劵的 totalNum > issueNum && 用户的已领取数量 < 优惠劵限领数量
            vo.setAvailable(
                    c.getIssueNum() < c.getTotalNum()
                    &&
                    issuedMap.getOrDefault(c.getId(), 0L) < c.getUserLimit()
            );
            //3.2 是否可以使用 用户已经领取并且未使用的优惠劵数量 > 0
            vo.setReceived(
                    unusedMap.getOrDefault(c.getId(), 0L) > 0
            );
        }
        return voList;
    }





}
