# P31 · 真凶：上架 SQL 漏同步 `media_id`（「替换/删除视频后学生端不变」）

> 2026-09-18 · 用户反馈：2.1 能显示了，但 ① 给 2.2 传了视频学生端仍显示"未上传"；
> ② **把 2.1 的视频删掉或换成新视频，学生端还显示最开始的那个视频**。
> ② 是决定性线索 —— 它说明**学生端读的正式表根本没跟着变**。

## 1 · 铁证：两表只差 `media_id` 一个字段

| 小节 | 草稿表 media_id | **正式表 media_id** | video_name | 时长 |
|---|---|---|---|---|
| 2.1 | **11**（用户替换后的新视频） | **9**（最开始的旧视频） | 两表**都是新文件名** | 都是 30 |
| 2.2 | **12**（用户新传的） | **NULL** | 两表**都有文件名** | 都是 30 |

**`video_name`、`media_duration` 都同步过去了，唯独 `media_id` 没有** ——
这就是「文件名是新的、视频还是旧的」「显示已上传、学生端说没有」这类自相矛盾现象的来源。

## 2 · 真凶

`TeacherCourseShelfMapper.copyCatalogue`（上架：草稿表 → 正式表）：

```sql
INSERT INTO course_catalogue (..., media_id, video_id, video_name, play_back, ...)
SELECT d.media_id, d.video_id, d.video_name, d.play_back, ...
FROM course_catalogue_draft d WHERE d.course_id = ?
ON DUPLICATE KEY UPDATE name = VALUES(name), c_index = VALUES(c_index),
       parent_catalogue_id = VALUES(parent_catalogue_id), type = VALUES(type),
       trailer = VALUES(trailer), video_name = VALUES(video_name),
       media_duration = VALUES(media_duration), update_time = NOW()
       -- 🔴 唯独没有 media_id = VALUES(media_id)
```

`INSERT ... ON DUPLICATE KEY UPDATE` 的行为是：**行不存在 → 走 INSERT（列全）；行已存在 → 只更新 UPDATE 列表里列出的字段**。
`media_id` 不在列表里 → **已存在的小节永远保持旧的媒资 id，且不报错**。

- 2.1：行已存在 → 只更新了 name/trailer/video_name/media_duration → `media_id` 还是 9 ✗
- 2.2：行也**已存在**（早先有行，只是没有媒资）→ 同样走 UPDATE 分支 → `media_id` 永远是 NULL ✗

## 3 · 同一类问题（一并修了）

`copyCourse`（课程主体）也漏了列 —— 意味着**改了这些再上架，学生端看不到**：

| 漏掉的字段 | 用户可见后果 |
|---|---|
| `course_type` / `template_type` / `template_url` | 课程类型、模板改了不生效 |
| `purchase_start_time` / `purchase_end_time` | 售卖时间段改了不生效 |
| `score` / `media_duration` | 评分、课程总时长改了不生效 |
| `updater` | 谁改的没记录 |

`copyContent`（简介/适用人群/详情）对照过，**完整**，无需改。
`seedCatalogueFromShelf`（正式 → 草稿的播种）是 `INSERT IGNORE` 全列复制，**完整**。

> 已在 `TeacherCourseShelfMapper` 类注释里落下规则：
> **`ON DUPLICATE KEY UPDATE` 的字段列表必须与 INSERT 的列逐一对照；加字段时两处一起加。**

## 4 · 为什么前两轮我判断错了（如实记录）

| 我说的 | 真相 |
|---|---|
| 「上架复制是全列 INSERT SELECT，media_id 会自动带到正式表」 | **错**。我当时只看了 INSERT 的列，**没看 UPDATE 列表** —— 这两者不是一回事 |
| 「draft 与 formal 完全一致，说明上架会同步」 | **假象**。那次一致是因为**我手动同时 UPDATE 了两张表**，等于自己造了证据 |
| 「问题可能是浏览器跑旧前端 / 缓存」 | 方向错了。前端把 `mediaId` 传得好好的，是**后端同步丢了字段** |

教训：**验证"链路是否同步"时，不能自己同时改两端再去看"是否一致"** —— 必须走真实接口跑一遍。

## 5 · 实测（走真实接口，非手动改数据）

| 步骤 | 结果 |
|---|---|
| 上架前 | 2.1：草稿 11 / 正式 9 ❌；2.2：草稿 12 / 正式 NULL ❌ |
| **调真实上架接口** `POST /teacher/course-draft/publish?courseId=1002` | ✅ 200 |
| 上架后 | **两个小节草稿 = 正式，全部 ✅** |
| 学生端 2.1 播放凭证 | ✅ 指向 **media 11**（`750535…`），不再是旧的 `01ee0ae…` |
| 学生端 2.2 播放凭证 | ✅ 指向 media 12 |
| **场景 A：删除 2.1 视频 → 上架** | ✅ 正式表 `media_id` 变 NULL，学生端返回「该小节暂未上传视频」 |
| **场景 B：重新挂上视频 → 上架** | ✅ 正式表 `media_id` = 11，学生端返回直链 |
| 学生端目录接口 | ✅ 2.1 → `mediaId=11`、2.2 → `mediaId=12` |
| 视频流（media 11 / 12） | ✅ 206 `video/mp4` 1024B，文件头均为合法 mp4 |
| 一致性全表复核 | ✅ 11 个小节 `media_id` 全部 OK |

## 6 · 你要知道的流程（重要）

讲师端编辑的是**草稿**，学生端读的是**正式**，两者靠「**提交上架**」同步。所以：

> **上传 / 替换 / 删除视频后，必须再点一次「提交上架」，学生端才会更新。**

以前这个动作**会漏掉媒资**（就是本次修的），所以看起来"改了没用、两端不同步"；现在不会再漏了。
（2.1 / 2.2 我已经用真实上架接口同步好了，强刷即可看到，不必重传。）

## 7 · 待你确认的清理项（不可恢复，故未动）

| media | 文件 | 大小 | 状态 |
|---|---|---|---|
| 9 | Wuthering Waves…mp4 | 8 MB | **已无引用**（2.1 已换成新视频） |
| 13 | Enderlilies…mp4 | 262 MB | **从未被引用**（你传了但没登记上，且与 11 是同一文件） |
| 11 / 12 | Enderlilies…mp4 | 262 MB ×2 | 分别被 2.1 / 2.2 使用中（**同一文件的两份副本**） |

可回收约 **270 MB**（9 + 13）。视频卷当前 **1.2 GB**。
**删除不可恢复**，你确认后我再动手。
