# P23 · 视频播放（本地媒资直链）

> 用户决策（2026-09-17）：**不配置任何云厂商（腾讯云 VOD/OSS）**，视频像本地测试那样「本地上传、本地存储、直链播放」。
> 这顺带揭开了 media-service 崩溃循环 12 天的真相。

## 0 · 真相：media-service 为什么一直起不来

| 环节 | 事实 |
|---|---|
| 容器状态 | `Restarting (1)` 循环，网关 `/ms` 永远「服务不存在」 |
| 直接死因 | `NoSuchBeanDefinitionException: IFileStorage` —— 阿里/腾讯实现都要云凭证才能装配，本地一张都没有 |
| **根本死因** | **`tj-media/pom.xml` 漏引 lombok** → 全模块 `@Data` 失效 → 该模块在仓库里**从未编译通过** → 容器里跑的一直是旧项目的 `com.tianji.media` 旧 jar |

修 lombok 后还有两层雷：`tj.platform.media` / `tj.file.platform` 绑定的是**枚举**（值里没有 LOCAL，配置直接绑定失败）；
`PullEventTask`（腾讯 VOD 事件轮询）无条件注入 `VodClient`。逐一修掉，服务首次以本仓库代码启动。

## 1 · 架构（LOCAL 平台模式）

```
上传：向导选文件 → 预读时长(<video>元数据) → POST /ms/medias/upload (multipart)
        → LocalMediaStorage 落盘卷 /data/media/{uuid}.mp4 → media 表登记 → 返回 {mediaId, mediaUrl}
登记：PUT /cs/teacher/sections/{sid}/video （带 mediaId）→ 草稿小节落 media_id
        → 上架复制是全列 INSERT SELECT → media_id 自动进正式表
播放：GET /ms/medias/signature/play?sectionId=（前端原有调用不变）
        → mediaUrl 非空的媒资【短路】返回 {mediaUrl}，不走 VOD 签名
        → 前端 initNativePlay：原生 <video> 播放（TCPlayer 的 CDN 本来就被注释，VOD 分支保留但不可达）
流控：GET /ms/media-stream/{key} 返回 FileSystemResource —— Spring 自动处理 Range 206（拖进度条必需）
免登：auth 白名单改 include 模式（/medias/** 要登录）；/media-stream/** 不在列表 → <video> 免登可播
```

改动面：

| 层 | 文件 | 内容 |
|---|---|---|
| tj-media | `storage/local/LocalFileStorage|LocalMediaStorage`、`config/LocalConfig`、`controller/LocalMediaStreamController` | 新文件，全部 `@ConditionalOnProperty(…=LOCAL)` |
| tj-media | `MediaController`（+`/upload`）、`MediaServiceImpl`（+`uploadLocalVideo`、播放短路）、`VideoPlayVO`/`MediaDTO`（+mediaUrl）、`Platform`/`FilePlatform` 枚举（+LOCAL） | 补丁 |
| tj-common | `WrapperResponseBodyAdvice` | **Resource 豁免**：全局 R 包装会把流包坏（ClassCastException→404） |
| tj-course | `SectionVideoDTO`(+mediaId)、`TeacherCatalogueDraftMapper`(+updateSectionMedia)、`saveSectionVideo` 落 mediaId | 补丁 |
| tj-course | `getSimpleSectionInfo` 的 `courseProperties.getMedia()` NPE | 该 Feign 链路从未跑通的老 bug，判空兜底 |
| deploy | `p23-media-tables.sql`（media/file 表，AUTO_INCREMENT）、compose media-service 挂 `media-data` 卷 | 新表+卷 |
| 前端 | `api/teacher/course.js`（uploadSectionVideo/probeVideoDuration/setSectionVideo+mediaId）、`courseWizard.vue`（假进度→真上传）、`learn.vue`（initNativePlay） | 补丁 |

## 2 · 实测（全绿）

| 场景 | 结果 |
|---|---|
| media-service 启动 | ✅ Up 不再重启；网关 `/ms` 通 |
| 上传 2.85MB 真视频（sample-5s.mp4） | ✅ `{"id":"3",…,"mediaUrl":"/ms/media-stream/8b5e…mp4"}` |
| 直链全量 | ✅ 200 video/mp4 2848208B，**md5 与源一致** |
| 直链 Range 0-1023 | ✅ 206 / 1024B |
| 经网关全量 | ✅ 200 / 2848208B，md5 一致 |
| 签名接口短路（学生 token 经网关） | ✅ `{"code":200,"data":{"mediaUrl":…}}` |
| 课程 1002 · 1.1 小节 | ✅ 已挂真实可播视频（正式表+草稿表） |
| 前端 | ✅ sfc-check 全过、vite 200 |

## 3 · 深坑备忘（全部进了项目记忆）

1. **模块要用 @Data 必须显式引 lombok**（P23）/ **@FeignClient 服务必须显式引 loadbalancer**（P22）——这类"依赖失踪"故障的表现都是服务起不来或大面积找不到符号。
2. **MP 全局 id-type=AUTO 时 `setId` 被忽略** → 新表主键必须 `AUTO_INCREMENT`。
3. **全局 R 包装 advice 会包装 Resource 流** → 必须豁免，否则流式下载必炸（表现是 404+ClassCastException）。
4. **excludeLoginPaths 实测不生效**（原因未深究）→ SDK 登录白名单改用 **include 模式**（精确列举要登录的接口）。
5. 直连服务测试别带网关前缀（`/ms` 是网关 StripPrefix 用的），否则 404 迷雾。

## 4 · 已知边界

- **网关对 Range 的转发**：SCG 转发 206 时截断了部分字节（实测 bytes=0-1023 经网关只回 89B）。浏览器 `<video>` 主要靠全量 200 播放不受影响；若实测拖动进度条异常，备选方案是学习页直连 8084 播放（本地环境无妨）。
- **试看截断**：本地模式 mediaUrl 全段可播，`freeDuration` 试看截断未实现（VOD 才有的能力）；免费课全段可见。
- **播放进度记录**：上报逻辑保留（lessonId 存在时每 15s 上报），但 learning 服务的进度表此前缺 `learningTime`——见 P8 遗留。
- 课程 1002 仅 1.1 小节挂了演示视频；其余小节按同样方式在向导里传即可。

---

## 5 · 用户实测反馈修复（2026-09-17 16:40）

用户用 teacher 在 **1.2 小节上传了真视频**（61MB / 55.76s），然后 demo 端出现两个现象。

### 现象 1：1.2 显示「媒资不存在」（`MEDIA_NOT_EXISTS`）

查库结论：**上传本身完全成功**（media id=4、文件在卷里、时长对），但该小节的 `media_id` 是 **NULL**，
只有 `video_name` 落了库 —— 处方是「新前端 + 旧后端 jar」的指纹，但实测**后端 jar 含 `updateSectionMedia`**
且手动调用带 `mediaId=4` **立刻落库** → 说明是我这边修好后端后、**用户浏览器仍在跑更早的前端 bundle**
（dev server 中途重启过，HMR 连接已断，页面不会自动更新）。→ 处置：**告知强刷** + 直接把用户上传的那个视频补齐到
1.2（正式表 + 草稿表，`media_id=4`，video_name 从草稿表同步避免手打中文），让 demo 立刻可播。

### 现象 2：1.1 视频一直黑屏（真 bug，两处）

| 问题 | 根因 | 修法 |
|---|---|---|
| `<video>` 拿不到流 | 后端返回的 `mediaUrl` 是**网关视角的相对路径** `/ms/media-stream/x.mp4`，前端直接当 `src` 用 → 请求打到 **dev server（18090）** → 404 黑屏 | `absoluteMediaUrl()` 拼绝对地址 |
| 走网关也播不了 | **网关转发 Range 请求返回 206 + 89B 的 JSON**（`Content-Type: application/json`，响应头 `IS_BODY_PROCESSED: true` 暴露网关包装器介入）；浏览器播 mp4 依赖 Range（分段加载 / 拖进度）→ 无法播放。直连媒体服务则完美（206 video/mp4 1024B） | 媒体流**不走网关**：`MEDIA_BASE` 默认 `http://localhost:8084`（可用 `VITE_MEDIA_BASE_URL` 覆盖），路径去掉 `/ms` 前缀 |
| 失败时只有黑屏 | 原生 `<video>` 没绑 error | 加 `error` 事件 → 显示「视频加载失败…」 |

### 实测

| 场景 | 结果 |
|---|---|
| 1.1 走网关全量 | ✅ 200 video/mp4 2848208B（md5 与源一致） |
| 1.1 / 1.2 直连 8084 + Range | ✅ 206 video/mp4（1024B 分段） |
| 1.2 数据补齐后两端签名 | ✅ 均返回 `mediaUrl` |
| 1.2 正式表 | ✅ `media_id=4`，video_name 已同步 |

> 待用户强刷后重新走一遍上传流程复验（若仍出现 media_id 为空，再按新证据深挖）。

---

## 6 · 用户实测反馈二（2026-09-17 16:47）

用户强刷后：**视频能播了**，但报两个新问题。

### 6.1 无法暂停 / 无法拖动进度条

**根因**：`learn.vue` 的 `<video>` **没有 `controls` 属性**（`<video id="videoRef" ref="videoRef"></video>`）。
老项目里控制条是 **TCPlayer 渲染**的（它接管容器 DOM），本地模式换成原生 `<video>` 后，
**没人来给控制条** → 画面能播，但没有暂停按钮、没有进度条 → 表现就是「只在那儿播，动不了」。

**修法**：给 video 补原生控制条 —— `controls + controlsList="nodownload"`（去掉下载入口）+ `playsinline`。

> 顺带说明：拖动进度条依赖 HTTP Range，而媒体流直连 8084 的 Range 是好的（见 §5），所以补上控件后拖动即可用。
> 若当初仍走网关（Range 被破坏），即便有进度条也拖不动 —— 两个修复是配套的。

### 6.2 点 1.3 显示「媒资不存在」

**根因有两层**：
1. 1.3 的 `media_id` 本来就是 `NULL`（该小节确实没传视频）——这是**正常业务状态**；
   但后端直接把内部异常原话 `"媒资不存在"` 透给了学生，读起来像系统故障。
2. `<video>` 里**还挂着上一节的 src** —— 报错提示虽然盖在上面（`pointer-events:none`），
   但旧视频的画面/声音仍在，观感很怪（切到没视频的小节时并没有清空播放器）。

**修法**：
| 位置 | 改动 |
|---|---|
| `FileErrorInfo` | 新增 `MEDIA_NOT_UPLOADED = "该小节暂未上传视频"` |
| `MediaServiceImpl`（两处分支） | 先判 `sectionInfo.getMediaId()` 是否为空 → 抛「该小节暂未上传视频」；有 id 但查不到记录才叫「媒资不存在」（数据异常）——**两种语义分开** |
| `learn.vue` | 新增 `setMediaError()` 统一错误出口：展示原因的同时 `pause() + removeAttribute('src') + load()`，把播放器复位；4 处错误赋值全部改走它 |

### 6.3 实测

| 场景 | 结果 |
|---|---|
| 1.1 签名（学生 token 经网关） | ✅ `mediaUrl: /ms/media-stream/8b5e...mp4` |
| 1.2 签名 | ✅ `mediaUrl: /ms/media-stream/23ef...mp4` |
| 1.3 签名 | ✅ `code 400 / "该小节暂未上传视频"`（不再是「媒资不存在」） |
| 1.1 Range 0-1023 | ✅ 206 video/mp4 1024B |
| 1.2 Range 0-1023 | ✅ 206 video/mp4 1024B，**与全量前 1024 字节逐字节一致** |
| 1.2 全量 | ✅ 200 video/mp4 61419682B（= 上传文件大小） |
| 文件头 | ✅ 均为合法 mp4（`ftyp` box） |

### 6.4 遗留建议（未做）

目录里**没有视频的小节没有任何标记** —— 学生要点进去才知道「暂未上传」。
更好的做法是在目录项上打标（灰显 / 「未上传」标签），需要目录接口带出 `hasVideo` 或 `mediaId`。
本次未改（属接口契约扩展），需要时再做。
