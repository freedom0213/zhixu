package com.zhixu.user.controller;


import com.zhixu.api.dto.user.UserDTO;
import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.common.utils.UserContext;
import com.zhixu.user.domain.query.UserPageQuery;
import com.zhixu.user.domain.vo.TeacherPageVO;
import com.zhixu.user.service.ITeacherService;
import com.zhixu.user.service.IUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 教师详情表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-07-12
 */
@RestController
@RequestMapping("/teachers")
@Api(tags = "用户管理接口")
public class TeacherController {

    @Autowired
    private ITeacherService teacherService;

    @Autowired
    private IUserService userService;

    @GetMapping("/page")
    @ApiOperation("分页查询教师信息")
    public PageDTO<TeacherPageVO> queryTeacherPage(UserPageQuery pageQuery){
        return teacherService.queryTeacherPage(pageQuery);
    }

    @ApiOperation("更新当前登录讲师的基本资料（个人资料页）")
    @PutMapping("/profile")
    public void updateMyProfile(@RequestBody UserDTO userDTO) {
        // 🔴 id 一律取登录态 —— 讲师只能改自己的资料，传别的 id 也无效（P24）
        userDTO.setId(UserContext.getUser());
        userService.updateUser(userDTO);
    }
}
