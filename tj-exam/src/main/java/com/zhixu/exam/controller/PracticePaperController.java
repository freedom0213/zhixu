package com.zhixu.exam.controller;

import com.zhixu.api.dto.exam.PracticeUpsertDTO;
import com.zhixu.exam.service.IPracticePaperService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 随堂练习卷（p15 方案 A）
 * -----------------------------------------------------------------------------
 * 这是**内部调用**接口：由 `course-service` 在「建课向导第④步 配题」保存后通过 Feign 调用，
 * 把这一节的题目引用同步成一张练习卷。学生与讲师都不会直接打这个接口。
 *
 * 为什么不做成前端调两次（先存配题、再建卷）：
 *   那样"这一节的题"就有了两处写入，谁失败谁成功没人管 —— 现在两件事在**同一个事务**里：
 *   配题写不进去，或者卷子建不出来，老师都会看到明确报错，不会出现"界面显示配好了、学生却没有题"。
 */
@Api(tags = "随堂练习卷（课程侧配题驱动）")
@RequiredArgsConstructor
@RestController
public class PracticePaperController {

    private final IPracticePaperService practicePaperService;

    @ApiOperation("随堂练习卷幂等 upsert（内部：course-service 配题后调用）")
    @PostMapping("/practice/upsert")
    public Long upsert(@RequestBody PracticeUpsertDTO dto) {
        return practicePaperService.upsert(dto);
    }
}
