package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.validation.constraints.Min;
import java.text.Format;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-14
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final UserClient userClient;

    private final StringRedisTemplate redisTemplate;

    /**
     * 根据赛季id查询积分排行榜以及查询自己的排名和积分
     * @param query 分页参数以及赛季id 为null或者0则代表查询当前赛季
     * @return 返回值VO对象
     */
    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        //1.判断是否是当前赛季
        Long season = query.getSeason();
        boolean isCurrent = query.getSeason() == null || query.getSeason() == 0;
        //2.1拼接Key
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        //2.2查询我的积分和排名
        PointsBoard myBoard = isCurrent ?
                queryMyCurrentBoard(key) :   //查询当前我的榜单
                queryMyHistoryBoard(season);    //查询历史我的榜单

        //3.查新榜单列表
        List<PointsBoard> list = isCurrent ?
                queryCurrentBoardList(key, query.getPageNo(), query.getPageSize()) :
                queryHistoryBoardList(query);

        // 4.封装VO
        PointsBoardVO vo = new PointsBoardVO();
        // 4.1.处理我的信息
        if (myBoard != null) {
            vo.setPoints(myBoard.getPoints());
            vo.setRank(myBoard.getRank());
        }
        if (CollUtils.isEmpty(list)) {
            return vo;
        }
        // 4.2.查询用户信息
        Set<Long> uIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> users = userClient.queryUserByIds(uIds);
        Map<Long, String> userMap = new HashMap<>(uIds.size());
        if(CollUtils.isNotEmpty(users)) {
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        }
        // 4.3.转换VO
        List<PointsBoardItemVO> items = new ArrayList<>(list.size());
        for (PointsBoard p : list) {
            PointsBoardItemVO v = new PointsBoardItemVO();
            v.setPoints(p.getPoints());
            v.setRank(p.getRank());
            v.setName(userMap.get(p.getUserId()));
            items.add(v);
        }
        vo.setBoardList(items);
        return vo;
    }

    /**
     * 根据传过来的赛季id 构建表
     * @param season 赛季id
     */
    @Override
    public void createPointsBoardTableBySeason(Integer season) {
        getBaseMapper().createPointsBoardTable(POINTS_BOARD_TABLE_PREFIX + season);
    }
    //TODO
    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery query) {

        return null;
    }

    public List<PointsBoard> queryCurrentBoardList(String key, @Min(value = 1, message = "页码不能小于1") Integer pageNo, @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize) {
        //1.计算分页
        int from = (pageNo - 1) * pageSize;
        int end = from + pageSize - 1;
        //2.查询 根据时间和ZSet的key 只要是同一个赛季的Key一定一样
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, from, end);
        if(CollUtils.isEmpty(typedTuples)) {
            return CollUtils.emptyList();
        }
        //3.封装
        int rank = from + 1;
        List<PointsBoard> list = new ArrayList<>(typedTuples.size());
        for(ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            String userId = tuple.getValue();
            Double score = tuple.getScore();
            if(userId == null || score == null) {
                continue;
            }
            PointsBoard p = new PointsBoard();
            p.setUserId(Long.valueOf(userId));
            p.setPoints(score.intValue());
            p.setRank(rank++);
        }
        return list;
    }


    private PointsBoard queryMyCurrentBoard(String key) {
        //查询我的实时积分与排名 数据都在redis中 数据结构为SortedSet 直接用key获取即可
        //1.对redisTemplate操作较多 先绑定 获得操作器ops
        BoundZSetOperations<String, String> ops = redisTemplate.boundZSetOps(key);
        //2.获取userId
        Long userId = UserContext.getUser();
        //3.获取积分
        Double points = ops.score(userId);
        //4.查询排名
        Long rank = ops.reverseRank(userId);
        //5.封装返回
        PointsBoard board = new PointsBoard();
        board.setPoints(points == null ? 0 : points.intValue());
        board.setRank(rank == null ? 0 : rank.intValue());
        return board;
    }
    //TODO
    private PointsBoard queryMyHistoryBoard(Long season) {
        return null;

    }



}
