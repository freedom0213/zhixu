package com.zhixu.user.controller;

import com.zhixu.api.dto.user.LoginFormDTO;
import com.zhixu.api.dto.user.UserDTO;
import com.zhixu.common.domain.dto.LoginUserDTO;
import com.zhixu.common.enums.UserType;
import com.zhixu.common.exceptions.BadRequestException;
import com.zhixu.common.utils.BeanUtils;
import com.zhixu.common.utils.CollUtils;
import com.zhixu.user.constants.UserErrorInfo;
import com.zhixu.user.domain.dto.UserFormDTO;
import com.zhixu.user.domain.po.User;
import com.zhixu.user.domain.po.UserDetail;
import com.zhixu.user.domain.vo.UserDetailVO;
import com.zhixu.user.enums.UserStatus;
import com.zhixu.user.service.IUserDetailService;
import com.zhixu.user.service.IUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("users")
@Api(tags = "用户管理接口")
public class UserController {

    @Autowired
    private IUserService userService;
    @Autowired
    private IUserDetailService detailService;

    @ApiOperation("新增用户，一般是员工或教师")
    @PostMapping
    public Long saveUser(@Valid @RequestBody UserDTO userDTO){
        userDTO.setId(null);
        return userService.saveUser(userDTO);
    }

    @ApiOperation("更新用户信息")
    @PutMapping("/{id}")
    public void updateUser(@RequestBody UserDTO userDTO){
        userService.updateUser(userDTO);
    }

    @ApiOperation("更新当前登录用户信息，可修改密码")
    @PutMapping
    public void updateCurrentUser(@Valid @RequestBody UserFormDTO userDTO){
        userService.updateUserWithPassword(userDTO);
    }

    @PutMapping("/{id}/password/default")
    @ApiOperation("重置密码")
    public void resetPassword(
            @ApiParam(value = "要重置的用户的id", example = "1") @PathVariable("id") Long userId) {
        userService.resetPassword(userId);
    }

    @PutMapping("/{id}/status/{status}")
    @ApiOperation("修改用户状态, status=0为禁用，status=1为正常")
    public void updateUserStatus(
            @ApiParam(value = "要重置的用户的id", example = "1") @PathVariable("id") Long userId,
            @ApiParam(value = "状态", example = "1") @PathVariable("status") Integer status
    ) {
        User user = new User();
        user.setId(userId);
        user.setStatus(UserStatus.of(status));
        userService.updateById(user);
    }

    @ApiOperation("获取当前登录用户信息")
    @GetMapping(value = "/me")
    public UserDetailVO me() {
        return userService.myInfo();
    }

    @ApiOperation("根据id查询用户信息")
    @GetMapping("/{id}")
    public UserDTO queryUserById(
            @ApiParam("用户id") @PathVariable("id") Long id) {
        UserDetail userDetail = detailService.queryById(id);
        return BeanUtils.copyBean(userDetail, UserDTO.class, (d, u) -> u.setType(d.getType().getValue()));
    }

    /**
     * 登录结构
     * @param loginDTO 登录表单
     * @param isStaff 是否是后台登录
     * @return 登录用户信息
     */
    @ApiIgnore
    @PostMapping("/detail/{isStaff}")
    public LoginUserDTO queryUserDetail(
            @Valid @RequestBody LoginFormDTO loginDTO, @PathVariable("isStaff") boolean isStaff) {
        return userService.queryUserDetail(loginDTO, isStaff);
    }

    /**
     * <h1>根据id批量查询用户信息</h1>
     *
     * @param ids 用户id集合
     * @return 用户集合
     */
    @ApiIgnore
    @GetMapping("/list")
    public List<UserDTO> queryUserByIds(
            @ApiParam("用户id的列表") @RequestParam("ids") List<Long> ids) {
        if(CollUtils.isEmpty(ids)){
            return CollUtils.emptyList();
        }
        // 1.查询列表
        List<UserDetail> list = detailService.queryByIds(ids);
        // 2.转换
        return BeanUtils.copyList(list, UserDTO.class, (d, u) -> u.setType(d.getType().getValue()));
    }

    /**
     * 查询用户类型
     *
     * @param id 用户id
     * @return 用户类型，0-普通学员，1-老师，2-其他员工
     */
    @ApiIgnore
    @GetMapping("/{id}/type")
    public Integer queryUserType(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BadRequestException(UserErrorInfo.Msg.USER_ID_NOT_EXISTS);
        }
        return user.getType().getValue();
    }

    @ApiIgnore
    @GetMapping("/ids")
    public Long exchangeUserIdWithPhone(@RequestParam("phone") String phone) {
        User user = userService
                .lambdaQuery().eq(User::getCellPhone, phone).one();
        if (user == null) {
            throw new BadRequestException(UserErrorInfo.Msg.USER_ID_NOT_EXISTS);
        }
        return user.getId();
    }

    /**
     * 按账号（用户名或手机号）查一位可授课教师 —— 建课向导「添加协作讲师」用。
     * -----------------------------------------------------------------------------
     * 为什么是「按账号查」而不是「拉一份教师名录下拉」：
     *   知序学堂是**面向所有有授课能力的老师**的开放平台，没有「校内教师名册」这种东西。
     *   所以由建课老师**自己填**协作者的平台账号，我们只负责把它解析成一个真实用户 id
     *   （course_teacher.teacher_id 必须是真的，不能存一个手输的名字）。
     *
     * 也顺手把「查不到」与「查到了但不是教师」分开报，避免用「不存在」掩盖真实原因。
     */
    @ApiOperation("按账号（用户名 / 手机号）查可授课教师")
    @GetMapping("/lookup")
    public UserDTO lookupTeacher(@ApiParam("用户名或手机号") @RequestParam("keyword") String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            throw new BadRequestException("请输入对方在平台上的账号（用户名或手机号）");
        }
        List<User> users = userService.lambdaQuery()
                .and(w -> w.eq(User::getUsername, kw).or().eq(User::getCellPhone, kw))
                .list();
        if (CollUtils.isEmpty(users)) {
            throw new BadRequestException("平台里没有这个账号：" + kw);
        }
        User user = users.get(0);
        if (user.getType() != UserType.TEACHER) {
            throw new BadRequestException("「" + kw + "」不是讲师身份的账号，不能加为讲师");
        }
        if (user.getStatus() != UserStatus.NORMAL) {
            throw new BadRequestException("「" + kw + "」的账号已被禁用");
        }
        UserDetail detail = detailService.queryById(user.getId());
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setType(user.getType().getValue());
        if (detail != null) {
            // 姓名 / 岗位 / 简介为空就留空，前端显示「—」，不编造
            dto.setName(detail.getName());
            dto.setJob(detail.getJob());
            dto.setIntro(detail.getIntro());
        }
        return dto;
    }

    /**
     * 按账号（用户名或手机号）查**任意**用户 —— 师生对话「发起新对话」用（P22）。
     * 与 /lookup 的区别：那个只认教师（协作讲师场景）；这里聊天对象可以是学生也可以是讲师，
     * 只要求账号真实存在且未禁用。只回 id/用户名/名字/头像，不泄露其它资料。
     */
    @ApiOperation("按账号（用户名 / 手机号）查任意用户（发起会话用）")
    @GetMapping("/chat-lookup")
    public UserDTO chatLookup(@ApiParam("用户名或手机号") @RequestParam("keyword") String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            throw new BadRequestException("请输入对方在平台上的账号（用户名或手机号）");
        }
        List<User> users = userService.lambdaQuery()
                .and(w -> w.eq(User::getUsername, kw).or().eq(User::getCellPhone, kw))
                .list();
        if (CollUtils.isEmpty(users)) {
            throw new BadRequestException("平台里没有这个账号：" + kw);
        }
        User user = users.get(0);
        if (user.getStatus() != UserStatus.NORMAL) {
            throw new BadRequestException("「" + kw + "」的账号已被禁用");
        }
        UserDetail detail = detailService.queryById(user.getId());
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setType(user.getType().getValue());
        if (detail != null) {
            dto.setName(detail.getName());
            dto.setIcon(detail.getIcon());
        }
        return dto;
    }

    @ApiOperation("检查用户手机号是否存在")
    @GetMapping("checkCellphone")
    public Boolean checkCellPhone(@RequestParam("cellphone") String cellPhone){
        return userService.lambdaQuery()
                .eq(User::getCellPhone, cellPhone)
                // .in(User::getType, UserType.STAFF, UserType.TEACHER)
                .count() <= 0;
    }
}
