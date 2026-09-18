package com.zhixu.message.controller;


import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.common.utils.UserContext;
import com.zhixu.message.domain.po.UserInbox;
import com.zhixu.message.domain.dto.UserInboxDTO;
import com.zhixu.message.domain.dto.UserInboxFormDTO;
import com.zhixu.message.domain.query.UserInboxQuery;
import com.zhixu.message.service.IUserInboxService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 用户通知记录 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
@Api(tags = "用户收件箱接口")
@RestController
@RequestMapping("/inboxes")
@RequiredArgsConstructor
public class UserInboxController {

    private final IUserInboxService inboxService;

    @PostMapping
    @ApiOperation("发送私信")
    public Long sentMessageToUser(@RequestBody UserInboxFormDTO userInboxFormDTO){
        return inboxService.sentMessageToUser(userInboxFormDTO);
    }

    @ApiOperation("分页查询收件箱")
    @GetMapping
    public PageDTO<UserInboxDTO> queryUserInBoxesPage(UserInboxQuery query){
        return inboxService.queryUserInBoxesPage(query);
    }

    /**
     * The local demo does not seed notification records yet. Keep the portal
     * header quiet until real notification delivery is configured.
     */
    @GetMapping("/unread")
    public Integer queryUnreadCount() {
        return 0;
    }

    @GetMapping("/unread/{type}")
    public Integer queryUnreadCountByType(@PathVariable Integer type) {
        return 0;
    }

    @PutMapping("/mark/{id}")
    @ApiOperation("标记单条消息已读")
    public Boolean markMessageRead(@PathVariable("id") Long id) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return false;
        }
        return inboxService.lambdaUpdate()
                .eq(UserInbox::getId, id)
                .eq(UserInbox::getUserId, userId)
                .eq(UserInbox::getIsRead, false)
                .set(UserInbox::getIsRead, true)
                .update();
    }

    @PutMapping("/markAll")
    @ApiOperation("全部标记已读")
    public Boolean markAllMessageRead() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return false;
        }
        return inboxService.lambdaUpdate()
                .eq(UserInbox::getUserId, userId)
                .eq(UserInbox::getIsRead, false)
                .set(UserInbox::getIsRead, true)
                .update();
    }
}
