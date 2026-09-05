package com.zhixu.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.common.exceptions.BizIllegalException;
import com.zhixu.common.utils.BeanUtils;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.learning.constants.RedisConstants;
import com.zhixu.learning.domain.dto.PointsExchangeFormDTO;
import com.zhixu.learning.domain.po.PointsExchangeRecord;
import com.zhixu.learning.domain.po.PointsMallItem;
import com.zhixu.learning.domain.query.PointsExchangePageQuery;
import com.zhixu.learning.domain.query.PointsMallPageQuery;
import com.zhixu.learning.domain.vo.PointsExchangeRecordVO;
import com.zhixu.learning.domain.vo.PointsMallItemVO;
import com.zhixu.learning.mapper.PointsExchangeRecordMapper;
import com.zhixu.learning.mapper.PointsMallItemMapper;
import com.zhixu.learning.service.IPointsMallService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointsMallServiceImpl extends ServiceImpl<PointsMallItemMapper, PointsMallItem> implements IPointsMallService {
    private final PointsExchangeRecordMapper recordMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public PageDTO<PointsMallItemVO> queryItems(PointsMallPageQuery query) {
        Page<PointsMallItem> page = lambdaQuery().eq(PointsMallItem::getStatus, 1)
                .orderByAsc(PointsMallItem::getPoints).page(query.toMpPageDefaultSortByCreateTimeDesc());
        return PageDTO.of(page, BeanUtils.copyList(page.getRecords(), PointsMallItemVO.class));
    }

    @Override
    public PointsMallItemVO queryItem(Long id) {
        PointsMallItem item = getById(id);
        return item == null ? null : BeanUtils.toBean(item, PointsMallItemVO.class);
    }

    @Override
    public PageDTO<PointsExchangeRecordVO> queryRecords(PointsExchangePageQuery query) {
        Page<PointsExchangeRecord> page = recordMapper.selectPage(query.toMpPageDefaultSortByCreateTimeDesc(),
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PointsExchangeRecord>()
                        .eq("user_id", UserContext.getUser()));
        if (CollUtils.isEmpty(page.getRecords())) return PageDTO.empty(page);
        Map<Long, String> names = itemNames(page.getRecords());
        List<PointsExchangeRecordVO> result = page.getRecords().stream().map(r -> {
            PointsExchangeRecordVO vo = BeanUtils.toBean(r, PointsExchangeRecordVO.class);
            vo.setItemName(names.get(r.getItemId()));
            return vo;
        }).collect(Collectors.toList());
        return PageDTO.of(page, result);
    }

    @Override
    public PointsExchangeRecordVO queryRecord(Long id) {
        PointsExchangeRecord record = recordMapper.selectById(id);
        if (record == null || !Objects.equals(record.getUserId(), UserContext.getUser())) throw new BizIllegalException("兑换记录不存在");
        PointsExchangeRecordVO vo = BeanUtils.toBean(record, PointsExchangeRecordVO.class);
        PointsMallItem item = getById(record.getItemId());
        vo.setItemName(item == null ? "" : item.getName());
        return vo;
    }

    @Override
    @Transactional
    public void exchange(PointsExchangeFormDTO form) {
        Long userId = UserContext.getUser();
        PointsMallItem item = getById(form.getItemId());
        if (item == null || item.getStatus() == null || item.getStatus() != 1 || item.getStock() <= 0) throw new BizIllegalException("商品已下架或库存不足");
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        Double current = redisTemplate.opsForZSet().score(key, userId.toString());
        if (current == null || current < item.getPoints()) throw new BizIllegalException("积分不足");
        redisTemplate.opsForZSet().incrementScore(key, userId.toString(), -item.getPoints());
        boolean updated = lambdaUpdate().setSql("stock = stock - 1").eq(PointsMallItem::getId, item.getId()).gt(PointsMallItem::getStock, 0).update();
        if (!updated) {
            redisTemplate.opsForZSet().incrementScore(key, userId.toString(), item.getPoints());
            throw new BizIllegalException("商品库存不足");
        }
        PointsExchangeRecord record = new PointsExchangeRecord();
        record.setUserId(userId);
        record.setItemId(item.getId());
        record.setPointsUsed(item.getPoints());
        record.setStatus(1);
        record.setAddress(form.getAddress());
        record.setPhone(form.getPhone());
        recordMapper.insert(record);
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        PointsExchangeRecord record = recordMapper.selectById(id);
        if (record == null || !Objects.equals(record.getUserId(), UserContext.getUser()) || !Objects.equals(record.getStatus(), 1)) throw new BizIllegalException("兑换记录不可取消");
        record.setStatus(3);
        recordMapper.updateById(record);
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        redisTemplate.opsForZSet().incrementScore(key, UserContext.getUser().toString(), record.getPointsUsed());
        lambdaUpdate().setSql("stock = stock + 1").eq(PointsMallItem::getId, record.getItemId()).update();
    }

    private Map<Long, String> itemNames(List<PointsExchangeRecord> records) {
        List<Long> ids = records.stream().map(PointsExchangeRecord::getItemId).distinct().collect(Collectors.toList());
        return listByIds(ids).stream().collect(Collectors.toMap(PointsMallItem::getId, PointsMallItem::getName));
    }
}
