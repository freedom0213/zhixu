package com.zhixu.user.service;

import com.zhixu.common.domain.dto.PageDTO;
import com.zhixu.user.domain.dto.StudentFormDTO;
import com.zhixu.user.domain.query.UserPageQuery;
import com.zhixu.user.domain.vo.StudentPageVo;
import com.zhixu.api.dto.user.UserDTO;

/**
 * <p>
 * 学员详情表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-07-12
 */
public interface IStudentService {

    void saveStudent(StudentFormDTO studentFormDTO);

    void updateMyPassword(StudentFormDTO studentFormDTO);

    void updateUser(UserDTO userDTO);

    PageDTO<StudentPageVo> queryStudentPage(UserPageQuery pageQuery);
}
