package com.zhixu.message.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixu.api.client.user.UserClient;
import com.zhixu.api.dto.user.UserDTO;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.common.exceptions.BadRequestException;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.common.utils.UserContext;
import com.zhixu.message.domain.po.ChatConversation;
import com.zhixu.message.domain.po.ChatMessage;
import com.zhixu.message.domain.vo.ChatConversationVO;
import com.zhixu.message.domain.vo.ChatMessageVO;
import com.zhixu.message.mapper.ChatConversationMapper;
import com.zhixu.message.mapper.ChatMessageMapper;
import com.zhixu.message.service.IChatService;
import com.zhixu.message.ws.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 师生对话（P22）。
 * -----------------------------------------------------------------------------
 * 会话按「两人成对」组织：user_low/user_high 是排序后的两个用户 id，配 uk_pair 唯一键。
 * 未读数记在会话行上（各记各的），所以会话列表 + 角标一次查询就够：
 *   发消息 → 接收方那一侧 +1；接收方拉历史 → 自己那一侧清 0。
 * 发送成功后向**接收方**在线的端推 WebSocket（发送方页面自己重拉第一页，不推重复数据）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_CONTENT = 500;

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final UserClient userClient;

    // ------------------------------------------------------------------ 会话列表

    @Override
    public PageDTO<ChatConversationVO> conversations(Integer pageNo, Integer pageSize) {
        Long me = requireUser();
        Page<ChatConversation> page = conversationMapper.selectPage(new Page<>(
                        pageNo == null || pageNo < 1 ? 1 : pageNo,
                        pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100)),
                Wrappers.<ChatConversation>lambdaQuery()
                        .and(w -> w.eq(ChatConversation::getUserLow, me).or().eq(ChatConversation::getUserHigh, me))
                        .orderByDesc(ChatConversation::getLastTime));
        List<ChatConversation> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 对方 id 集合 → 一次查名字/头像
        List<Long> otherIds = records.stream()
                .map(c -> otherOf(c, me)).distinct().collect(Collectors.toList());
        Map<Long, UserDTO> users = loadUsers(otherIds);

        List<ChatConversationVO> list = new ArrayList<>(records.size());
        for (ChatConversation c : records) {
            Long otherId = otherOf(c, me);
            UserDTO u = users.get(otherId);
            list.add(new ChatConversationVO()
                    .setId(c.getId())
                    .setOtherUserId(otherId)
                    .setOtherUsername(u == null ? null : u.getName())
                    .setOtherAvatar(u == null ? null : u.getIcon())
                    .setLastMessage(c.getLastMessage())
                    .setLastMessageTime(c.getLastTime())
                    .setUnReadCount(Objects.equals(c.getUserLow(), me) ? c.getUnreadLow() : c.getUnreadHigh()));
        }
        return PageDTO.of(page, list);
    }

    // ------------------------------------------------------------------ 聊天记录

    @Override
    public PageDTO<ChatMessageVO> messages(Long otherUserId, Integer pageNo, Integer pageSize) {
        Long me = requireUser();
        if (otherUserId == null) {
            throw new BadRequestException("缺少对方用户id");
        }
        ChatConversation conv = findPair(me, otherUserId);
        if (conv == null) {
            // 会话还没建 = 一句话没说过：返回空页即可（不建空会话）
            Page<ChatMessage> empty = new Page<>(pageNo == null ? 1 : pageNo,
                    pageSize == null ? 20 : pageSize, 0);
            return PageDTO.empty(empty);
        }

        // 拉历史 = 我已读：把我在该会话上的未读清零
        clearUnread(conv, me);

        // 约定：pageNo=1 是**最新的一页**（页内正序），pageNo 越大越早 →
        // 按 push_time 倒序取一页，再反转让页内变成正序
        Page<ChatMessage> page = messageMapper.selectPage(new Page<>(
                        pageNo == null || pageNo < 1 ? 1 : pageNo,
                        pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100)),
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getConversationId, conv.getId())
                        .orderByDesc(ChatMessage::getPushTime)
                        .orderByDesc(ChatMessage::getId));
        List<ChatMessage> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        Collections.reverse(records);

        // 气泡头像：会话里只有两个人，一次查齐
        Map<Long, UserDTO> users = loadUsersColl(listOfDistinctSenders(records));
        List<ChatMessageVO> list = new ArrayList<>(records.size());
        for (ChatMessage m : records) {
            UserDTO u = users.get(m.getSenderId());
            list.add(new ChatMessageVO()
                    .setId(m.getId())
                    .setSenderId(m.getSenderId())
                    .setSenderIcon(u == null ? null : u.getIcon())
                    .setContent(m.getContent())
                    .setPushTime(m.getPushTime()));
        }
        return PageDTO.of(page, list);
    }

    // ------------------------------------------------------------------ 发送

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long send(Long receiverId, String content) {
        Long me = requireUser();
        if (receiverId == null) {
            throw new BadRequestException("缺少接收人");
        }
        if (Objects.equals(receiverId, me)) {
            throw new BadRequestException("不能给自己发私信");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BadRequestException("消息内容不能为空");
        }
        if (text.length() > MAX_CONTENT) {
            throw new BadRequestException("消息太长了（最多 " + MAX_CONTENT + " 字）");
        }
        // 接收人必须真实存在：拼错 id 会建出一条永远没人收的会话
        UserDTO receiver = userClient.queryUserById(receiverId);
        if (receiver == null) {
            throw new BadRequestException("对方账号不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        ChatConversation conv = findOrCreate(me, receiverId, now);

        ChatMessage msg = new ChatMessage()
                .setConversationId(conv.getId())
                .setSenderId(me)
                .setContent(text)
                .setPushTime(now);
        messageMapper.insert(msg);

        // 会话摘要 + 接收方未读 +1（用 setSql 做自增，避免并发下读改写丢计数）
        boolean meIsLow = me < receiverId;
        conversationMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<ChatConversation>lambdaUpdate()
                .eq(ChatConversation::getId, conv.getId())
                .set(ChatConversation::getLastMessage, abbreviate(text))
                .set(ChatConversation::getLastTime, now)
                .setSql(meIsLow ? "unread_high = unread_high + 1" : "unread_low = unread_low + 1"));

        // 推给接收方在线的端（不推发送方：页面发送后自己重拉第一页）
        pushToReceiver(me, receiverId, receiver, conv.getId(), msg);
        return msg.getId();
    }

    // ------------------------------------------------------------------ 私有助手

    private Long requireUser() {
        Long me = UserContext.getUser();
        if (me == null) {
            throw new BadRequestException("请先登录");
        }
        return me;
    }

    private Long otherOf(ChatConversation c, Long me) {
        return Objects.equals(c.getUserLow(), me) ? c.getUserHigh() : c.getUserLow();
    }

    private ChatConversation findPair(Long a, Long b) {
        long low = Math.min(a, b);
        long high = Math.max(a, b);
        return conversationMapper.selectOne(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getUserLow, low)
                .eq(ChatConversation::getUserHigh, high));
    }

    /** 找不到就建（uk_pair 挡并发重复；真撞了唯一键就再查一次） */
    private ChatConversation findOrCreate(Long a, Long b, LocalDateTime now) {
        ChatConversation conv = findPair(a, b);
        if (conv != null) {
            return conv;
        }
        long low = Math.min(a, b);
        long high = Math.max(a, b);
        ChatConversation c = new ChatConversation()
                .setUserLow(low)
                .setUserHigh(high)
                .setUnreadLow(0)
                .setUnreadHigh(0)
                .setCreateTime(now)
                .setUpdateTime(now);
        try {
            conversationMapper.insert(c);
            return c;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发下对方同时建了同一条会话：再查一次即可
            ChatConversation again = findPair(a, b);
            if (again == null) {
                throw e;
            }
            return again;
        }
    }

    private void clearUnread(ChatConversation conv, Long me) {
        boolean meIsLow = Objects.equals(conv.getUserLow(), me);
        int mine = meIsLow ? conv.getUnreadLow() : conv.getUnreadHigh();
        if (mine == 0) {
            return;
        }
        conversationMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<ChatConversation>lambdaUpdate()
                .eq(ChatConversation::getId, conv.getId())
                .set(meIsLow ? ChatConversation::getUnreadLow : ChatConversation::getUnreadHigh, 0));
    }

    private Map<Long, UserDTO> loadUsers(List<Long> ids) {
        if (CollUtils.isEmpty(ids)) {
            return new HashMap<>();
        }
        try {
            List<UserDTO> users = userClient.queryUserByIds(ids);
            Map<Long, UserDTO> map = new HashMap<>();
            for (UserDTO u : CollUtils.emptyIfNull(users)) {
                map.put(u.getId(), u);
            }
            return map;
        } catch (Exception e) {
            // 用户服务不可用不该让会话页崩：名字/头像留空，前端回落「同学」与默认图
            log.warn("聊天：查询用户信息失败 ids={}", ids, e);
            return new HashMap<>();
        }
    }

    private Map<Long, UserDTO> loadUsersColl(List<Long> ids) {
        return loadUsers(ids);
    }

    private List<Long> listOfDistinctSenders(List<ChatMessage> records) {
        return records.stream().map(ChatMessage::getSenderId).distinct().collect(Collectors.toList());
    }

    private String abbreviate(String text) {
        return text.length() <= 50 ? text : text.substring(0, 50) + "…";
    }

    /** 实时推送给接收方在线的端（页面上：在当前会话则追加气泡，否则只刷新列表未读） */
    private void pushToReceiver(Long me, Long receiverId, UserDTO receiver, Long conversationId, ChatMessage msg) {
        try {
            UserDTO sender = userClient.queryUserById(me);
            Map<String, Object> message = new HashMap<>();
            message.put("id", msg.getId());
            message.put("senderId", me);
            message.put("senderIcon", sender == null ? null : sender.getIcon());
            message.put("content", msg.getContent());
            // ⚠️ 时间必须先格式化成字符串：裸 ObjectMapper 序列化 LocalDateTime 会直接抛
            //    （没有注册 JavaTimeModule），推送就静默失败了
            message.put("pushTime", msg.getPushTime() == null ? null : TIME_FMT.format(msg.getPushTime()));

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "chat");
            payload.put("conversationId", conversationId);
            payload.put("otherUserId", me);
            payload.put("otherUsername", sender == null ? null : sender.getName());
            payload.put("otherAvatar", sender == null ? null : sender.getIcon());
            payload.put("message", message);
            ChatWebSocketHandler.pushTo(receiverId,
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
        } catch (Exception e) {
            // 推送失败不影响消息已落库：对方上线拉会话列表仍能看到
            log.warn("聊天：WebSocket 推送失败 receiver={}", receiver == null ? null : receiver.getId(), e);
        }
    }
}
