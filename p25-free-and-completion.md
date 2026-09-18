# P25 · 免费课的「试看」语义 + 学习完成的判定口径

> 2026-09-17 · 用户报两件事：① 免费的《Spring Boot 快速入门》为什么还显示「试看」；
> ② 看完一个小节怎样才算「学习完成」（用户建议：视频进度 70%）。
> 结论：两个都是真问题，而且第 ② 个牵出了**两个更严重的隐藏故障** —— 学习记录此前**一条都没落过库**。

## 1 · 免费课不该有「试看」

**根因**：「试看」是**付费课**的营销手段（`course_catalogue.trailer`），而免费课程本就对所有用户开放。
但三个地方把它当成通用概念：

| 位置 | 原行为 |
|---|---|
| `MediaServiceImpl.getPlaySignatureBySectionId` | 未报名用户看 `trailer=0` 的小节 → 抛「课程不支持试看」——**免费课也被拦** |
| `courseDetail.vue` | `v-if="sec.trailer"` 无条件显示「试看」标签 |
| 学习页目录（复用 `Catalogue.vue`） | 同上 |

**修改**：

1. `SectionInfoDTO` 增 `free`；`CourseCatalogueServiceImpl.getSimpleSectionInfo` 查 `course.free` 带出；
2. `MediaServiceImpl`：`if (免费课) 放行；else if (trailer=0) 才抛「课程不支持试看」`；
3. `CourseFullInfoDTO` / `CourseAndSectionVO` 增 `free`（BeanUtils 自动映射），
   `courseDetail.vue` 与学习页据此**免费课不显示「试看」**（学习页在拿到目录后把 `trailer` 抹平，目录组件零改动）。

## 2 · 学习完成：70% 已落地，但它此前**根本没生效过**

### 2.1 你提的口径已实现
`LearningRecordServiceImpl.handleVideoRecord` 原为 `moment * 2 >= duration`（**50%**），
现改为 **70%**：`moment * 10 >= duration * 7`（整数比较避免浮点误差），并加 `duration > 0` 保护
（否则时长为 0 时任何进度都会「越算越完」）。

### 2.2 🔴 隐藏故障一：上报接口收不到数据 → 学习记录从未写入
`POST /ls/learning-records` 的 Controller 参数**没有 `@RequestBody`**，Spring 按表单绑定，
而前端（老学习页与新学员端都）用 **JSON body** 发送 → `lessonId/sectionId/moment/duration` **全部为 null**
→ 写库时 `Field 'lesson_id' doesn't have a default value` → **500 回滚**。
所以 `learning_record` 一直是 **0 行**：**进度、完成状态、续播位置都没有真正记录过**
（前端 `.catch(err => console.log(err))` 把错误吞了，问题长期不可见）。
→ 已加 `@RequestBody`。

### 2.3 🔴 隐藏故障二：上报的时长是**课程里预设值**，不是视频真实时长
前端 `currentPlayData.duration = item.mediaDuration`（课程目录里预设的 `media_duration`）。
1.1 预设 900 秒、实际视频只有 5 秒 → 哪怕完整看完也到不了 50%/70% → **永远学不完**。
→ 学习页在 `loadedmetadata` 时改用播放器的 `el.duration`（真实秒数）作为上报时长。

### 2.4 顺带修掉的两个小问题
- **播到结尾不补报**：原来 `ended` 只停计时器，最后一次上报可能是 15 秒前的 → 完整看完也可能卡在线下。
  现在 `ended` 时补报一次最终进度（并置回未播放态，用户重播时才会重新开始上报）。
- 免费试看不记进度：`!currentPlayData.lessonId` 时不上报 —— 报名后 `lessonId` 有值，不受影响。

## 3 · 最终口径

| 项 | 口径 |
|---|---|
| 「学完一个小节」 | 播放进度达到**视频真实时长的 70%**（`moment*10 >= duration*7`） |
| 上报节奏 | 点播放立即报一次 + 每 15 秒一次 + 播到结尾补报一次 |
| 学完的影响 | `learning_record.finished=1` → 课表 `learned_sections` +1 → 目录该小节显示完成 |
| 已完成的小节 | 再次上报不会重复累加（`!old.getFinished()` 守住） |
| 续播 | `learning_record.moment` 记录播放位置 |

## 4 · 实测

| 场景 | 结果 |
|---|---|
| 目录接口 | ✅ `free: 1` |
| 小节信息（1.2，trailer=0） | ✅ `{"trailer":false,"free":true,...}` |
| **免费课 + 未报名学生**播 trailer=0 的小节 | ✅ 200 返回 `mediaUrl`（修复前报「课程不支持试看」） |
| 上报 60% → 60% → 69% | ✅ 记录写入、**不判完成** |
| 上报 70% | ✅ `finished=1`，课表 `learned_sections` +1 |
| 上报 95%（已完成后再报） | ✅ 不重复累加 |
| **经网关 + demo token** 上报（浏览器真实路径） | ✅ 50% 不完成 → 70% 完成 |

> 测试用的 3 条模拟记录已删除、课表进度已复位（`learning_record` 归 0），环境是干净的。

## 5 · 遗留 / 需你实测确认

- **`video.duration` 的读取只能在浏览器里真正验证**：请用 `teacher` 上传的真实视频（1.2）播放到 70% 以上，
  然后看学习页目录该小节是否变为「已学完」。若你上传的视频很短（几秒），看完整段即应完成。
- `LearningRecordDelayTaskHandler.writeRecordCache` 在**修复前**的脏请求上会抛 NPE（日志有记录），
  修复后未再复现；如后续仍出现，再给它加 null 防护（当前未改，避免无验证的改动）。
- 学习时长统计（热力图）仍缺数据源：`learning_lesson` 没有时长列，本次未动。
