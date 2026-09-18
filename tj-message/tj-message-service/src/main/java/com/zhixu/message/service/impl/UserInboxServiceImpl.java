package com.zhixu.message.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixu.api.dto.user.UserDTO;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.message.config.MessageProperties;
import com.zhixu.message.domain.dto.UserInboxDTO;
import com.zhixu.message.domain.dto.UserInboxFormDTO;
import com.zhixu.message.domain.po.NoticeTemplate;
import com.zhixu.message.domain.po.PublicNotice;
import com.zhixu.message.domain.po.UserInbox;
import com.zhixu.message.domain.query.UserInboxQuery;
import com.zhixu.message.enums.NoticeType;
import com.zhixu.message.mapper.UserInboxMapper;
import com.zhixu.message.service.IPublicNoticeService;
import com.zhixu.message.service.IUserInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * <p>
 * 用户通知记录 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
@Service
@RequiredArgsConstructor
public class UserInboxServiceImpl extends ServiceImpl<UserInboxMapper, UserInbox> implements IUserInboxService {

    private final MessageProperties properties;
    private final IPublicNoticeService publicNoticeService;

    @Override
    public void saveNoticeToInbox(NoticeTemplate notice, List<UserDTO> users) {
        LocalDateTime pushTime = LocalDateTime.now();
        LocalDateTime expireTime = pushTime.plusMonths(properties.getMessageTtlMonths());
        // 1.初始化信箱数据
        List<UserInbox> list = new ArrayList<>(users.size());
        // 2.组装
        for (UserDTO user : users) {
            UserInbox box = new UserInbox();
            box.setTitle(notice.getTitle());
            box.setContent(notice.getContent());
            box.setUserId(user.getId());
            box.setType(notice.getType());
            box.setPushTime(pushTime);
            box.setExpireTime(expireTime);
            list.add(box);
        }
        // 3.保存
        saveBatch(list);
    }

    @Override
    @Transactional
    public PageDTO<UserInboxDTO> queryUserInBoxesPage(UserInboxQuery query) {
        // 1.获取用户信息
        Long userId = UserContext.getUser();
        // 2.查询用户信箱中的最后一条公告，确认本次加载公告的最早时间点
        UserInbox latest = getBaseMapper().queryLatestPublicNotice(userId);
        // 2.1.默认时间点是当前时间减去公告的最大有效期时间（未过期的最早公告时间）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime minTime = now.minusMonths(properties.getNoticeTtlMonths());
        // 2.2.如果有最后一条公告，判断公告时间是不是比最早时间要晚
        if(latest != null && latest.getPushTime().isAfter(minTime)){
            // 用户上次加载时间比最早时间晚，更新一下时间
            minTime = latest.getPushTime();
        }
        // 3.按照发布时间倒序，查看公告箱中的消息，最多加载200条
        Page<PublicNotice> page = new Page<PublicNotice>(1, 200)
                .addOrder(new OrderItem("push_time", false));
        page = publicNoticeService.lambdaQuery()
                .ge(PublicNotice::getPushTime, minTime)
                .page(page);
        // 4.将公告写入用户收件箱
        if (CollUtils.isNotEmpty(page.getRecords())) {
            saveNoticeListToInbox(page.getRecords(), userId);
        }
        // 5.分页查询收件箱信息并返回
        Page<UserInbox> userInboxPage = query.toMpPage("push_time", false);
        userInboxPage = lambdaQuery()
                .eq(UserInbox::getUserId, userId)
                .eq(query.getIsRead() != null, UserInbox::getIsRead, query.getIsRead())
                .eq(query.getType() != null, UserInbox::getType, query.getType())
                .page(userInboxPage);
        return PageDTO.of(userInboxPage, UserInboxDTO.class);
    }

    private void saveNoticeListToInbox(List<PublicNotice> notices, Long userId) {
        if (CollUtils.isEmpty(notices)) {
            return;
        }
        // 🔴 P35：这里原先**无条件批量插入** —— 每次打开公告页都会把同一批公告再插一份，
        //    收件箱很快积出大量重复行（实测一个用户 3 条公告积成了 31 条）。
        //    重复行会让"标记已读"看起来失灵：点掉一份，其它副本仍是未读。
        //    去重键 = (push_time, title)：公告的推送时间 + 标题足以唯一标识一条公告
        //    （表里没有存 notice_id，加列需要迁移，这里先按业务键去重）。
        Set<String> existing = new HashSet<>();
        List<UserInbox> owned = lambdaQuery()
                .eq(UserInbox::getUserId, userId)
                .select(UserInbox::getPushTime, UserInbox::getTitle)
                .list();
        for (UserInbox row : owned) {
            existing.add(noticeKey(row.getPushTime(), row.getTitle()));
        }
        List<UserInbox> list = new ArrayList<>(notices.size());
        for (PublicNotice notice : notices) {
            String key = noticeKey(notice.getPushTime(), notice.getTitle());
            if (!existing.add(key)) {
                continue; // 本次这批里已有，或收件箱里已存在 → 跳过，不重复插
            }
            UserInbox box = new UserInbox();
            box.setTitle(notice.getTitle());
            box.setContent(notice.getContent());
            box.setUserId(userId);
            box.setType(notice.getType());
            box.setPushTime(notice.getPushTime());
            box.setExpireTime(notice.getExpireTime());
            list.add(box);
        }
        if (!list.isEmpty()) {
            saveBatch(list);
        }
    }

    /** 收件箱里标识一条公告的业务键（表里没有 notice_id，用推送时间 + 标题） */
    private String noticeKey(java.time.LocalDateTime pushTime, String title) {
        return (pushTime == null ? "" : pushTime.toString()) + '|' + (title == null ? "" : title);
    }

    @Override
    public Long sentMessageToUser(UserInboxFormDTO userInboxFormDTO) {
        // 1.计算时间
        LocalDateTime pushTime = LocalDateTime.now();
        LocalDateTime expireTime = pushTime.plusMonths(properties.getMessageTtlMonths());
        // 2.获取当前用户
        Long userId = UserContext.getUser();
        // 3.组织数据
        UserInbox inbox = new UserInbox();
        inbox.setUserId(userInboxFormDTO.getUserId());
        inbox.setContent(userInboxFormDTO.getContent());
        inbox.setType(NoticeType.PRIVATE_MESSAGE.getValue());
        inbox.setPushTime(pushTime);
        inbox.setExpireTime(expireTime);
        inbox.setPublisher(userId);
        save(inbox);
        return inbox.getId();
    }
}
