# P21 · 乱码修复 + 录题「可见范围」补全

> 用户 2026-09-16 晚反馈：① 学生端答卷里解析乱码（`Nacos åæ¶æä¾›æåŠ¡æ³¨å†Œå‘çŽ°å’Œé…ç½®ç®¡ç†èƒ½åŠ›ã€‚`）；
> ② 困惑「我录题时没开公开，为什么题库里大家的题一样」——根因是录题页**根本没有可见范围开关**。

## 1 · 乱码：根因、范围、修复

**特征**：`åæ¶` = 「同时」的 UTF-8 字节被按 Latin-1 解读 —— 典型双重编码，**可以无损还原**。

**范围**（扫了三处，只有一条数据坏）：

| 位置 | 状态 |
|---|---|
| `question_detail.analysis`（id=9001，Nacos 题） | ❌ 乱码 |
| `exam.snapshot_items`（发布时冻结的快照） | ❌ 同样的乱码（快照把解析也冻进去了） |
| `exam_record.snapshot_items`（学生作答时冻结的快照） | ❌ 同上 —— **用户看到乱码的就是这份** |

题干 / 选项 / 其它题目全部正常（不是连接字符集的全局问题，是**当初这条演示数据导入时**的编码事故）。

**修复**（全部在服务端做字节转换，不经过客户端字符集；备份 `%TEMP%\p21-mojibake-backup.sql`）：
```sql
UPDATE question_detail SET analysis = CONVERT(BINARY(CONVERT(analysis USING latin1)) USING utf8mb4) WHERE id=9001 AND ...;
UPDATE exam         SET snapshot_items = JSON_SET(snapshot_items, '$[0].analysis', CONVERT(BINARY(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(...)) USING latin1)) USING utf8mb4)) ...;
UPDATE exam_record  SET snapshot_items = JSON_SET(...) /* 同上 */;
```

**实测**（四个视角都验过，全部是「Nacos 同时提供服务注册发现和配置管理能力。」）：
讲师看学生答卷 / 讲师看卷面 / 题库详情 / **学生看自己的答卷**。

**防复发**：跑任何带中文的 SQL，`mysql` 客户端必须带 `--default-character-set=utf8mb4` —— 本轮排查时我自己漏了一次，
中文 WHERE 条件按 latin1 比对直接查不到数据（正是这类乱码的产生机制）。

## 2 · 「我没开公开」的真相 + 录题开关补全

证据链：
1. `question.visibility` 的**列默认是 0（仅我）**；
2. 但 P17 迁移把**存量题一律置 1（公开）**——你录的「你好」就是那时被置公开的；
3. 录题页（`questionEdit.vue`）**从来没有可见范围控件**，列表里的「发布/撤回」是唯一入口。

所以「默认公开」不是设计，是**迁移的一次性动作** + **录题入口缺失**叠加出来的观感。

**补全**（默认私有、主动公开的既定模型落到录题入口）：
- 后端：`QuestionFormDTO` 加 `visibility`（0/1，**不传=不动**：新增按 0，编辑保持原值）；
  发布/撤回抽成唯一实现 `changeVisibility(id, target)`（撤回被引用的题会拒绝并报卷名），
  `setVisibility` 与新增/编辑**都走它**；编辑时把 copyBean 带来的 `visibility` 摘掉再 updateById —— 否则会**绕过撤回校验**直接写库。
- 录题页：新增 **⑧ 可见范围**（仅我 / 公开到平台），默认**仅我**，并写明语义（含「被引用不能撤回」）；
  「保存并继续新建」会把可见范围复位为仅我 —— 上一题选了公开不该连坐下一题。
- 详情 VO 补 `visibility`（`BeanUtils.copyBean` 自动带过来），编辑态能回填。

## 3 · 实测矩阵（全绿）

| 场景 | 结果 |
|---|---|
| 新建**不传** visibility | ✅ `visibility=0, publish_time=NULL`（仅我） |
| 新建显式 `visibility=1` | ✅ `visibility=1`，`publish_time` 有值 |
| 编辑**不带** visibility | ✅ 保持原值（公开题不会因为改个题干就被悄悄撤回） |
| 编辑显式改 0（未被引用） | ✅ 撤回成功 |
| 编辑显式改 0（**被试卷引用**） | ✅ 400「这道题已被 1 份试卷引用（第一章检测 · Spring Boot 基础）。撤回会让那些卷子少一道题…」且库中保持公开 |
| 录题页 ⑧ 区块 / 前端编译 | ✅ sfc-check 通过、vite 200；区块顺序 ①→⑧ |

> 测试造的 3 道临时题已删（库里剩 3 道演示题，全部公开、解析正常）。
> ⚠️ 踩坑记录：`POST /es/questions` 返回**空响应体**（controller 是 void，拿不到新题 id）——测试要用库里的 id；
> `mysql` 客户端查中文必须 `--default-character-set=utf8mb4`。

## 4 · 遗留

- 列表行 / 详情 VO 仍未返回 `publish_time`（只有管理端的「发布到平台」排序可能用到，暂不需要）。
- 学生数 / 应考人数的聚合依旧没有来源（「—」），等「选课名单」类需求一起定。
