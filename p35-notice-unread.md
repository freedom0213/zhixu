# P35 · 公告「点了还是未读」——两个 bug 叠在一起

> 2026-09-18 · 用户报：公告与新闻页的**未读公告，点进去还是未读**。
> 查下来是**两个独立缺陷叠加**，而且第二个才让问题"看起来无法解释"。

---

## 1 · Bug ①：前后端 HTTP 方法不一致 → 标记已读**从未成功过**

| | |
|---|---|
| 后端 `UserInboxController` | `@PutMapping("/mark/{id}")` · `@PutMapping("/markAll")` — **PUT** |
| 前端 `api/message.js` | `method: 'post'` — **POST** |

实测：

```
POST /inboxes/mark/1 → HTTP 500
PUT  /inboxes/mark/1 → HTTP 200  ✅
```

而前端这段是这么写的：

```js
if (!item.isRead) {
  try {
    await markMessageAsRead(item.id);
    item.isRead = true;
  } catch (e) { /* 静默：已读状态不影响查看 */ }   // ← 错误被吞掉
}
```

**`catch` 把 500 静默吞了**，同时本地又把 `isRead` 置成了 `true` —— 所以点开的当次看着"已读"，**一刷新（或下次进入）又变回未读**。这就是用户看到的现象。

> 顺带：`markAllMessageAsRead`（「全部标为已读」按钮）用的是**同一个错误方法**，所以那个按钮其实也一直是坏的。

**修复**：前端两处改为 `method: 'put'`。

---

## 2 · Bug ②：收件箱写入**没有去重** → 同一条公告有多个副本

公告页的数据流是：从 `public_notice`（公告本体，3 条）按时间增量拉取 → **写入 `user_inbox`**（每人的收件箱，带 `is_read`）→ 从收件箱分页返回。

问题出在写入那一步：

```java
private void saveNoticeListToInbox(List<PublicNotice> notices, Long userId) {
    for (PublicNotice notice : notices) { /* 逐个塞进 list */ }
    saveBatch(list);        // ← 无条件批量插入，没有任何去重
}
```

而拉取的时间点用的是**收件箱里最新一条的推送时间**、条件是 `push_time >= minTime` —— 于是**最新那条公告会被反复插入**：

```
user_id=3 的收件箱：31 条   ← 实际只有 3 条公告
```

**这直接放大了 Bug ① 的观感**：点掉一份副本，**其它 10 份副本还是未读** —— 用户会觉得"怎么点都还是未读"。

**修复**：写入前先查该用户已有的 `(推送时间, 标题)`，已存在就跳过。（表里**没有存 `notice_id`**，加列需要迁移；先用业务键去重，够用且零迁移风险。）

---

## 3 · 数据清理（含我造成的连带问题）

清理前先**完整备份**：`.workbuddy/user_inbox-backup.sql`（mysqldump，可回滚）。

```sql
-- 同一 (用户, 推送时间, 标题) 只保留一条；优先保留"已读"的那条，避免丢状态
DELETE u FROM user_inbox u JOIN (... GROUP BY user_id, push_time, title ...) k
WHERE u.id <> k.keep_id;
```

结果：**34 条 → 5 条**（user 3：31→2，user 9：3→3）。反复打开公告页验证：**总数不再增长** ✅

> ⚠️ **清理时我发现少了一条，而且它补不回来**：后端的时间点是"收件箱里最新一条"，一旦推进就**跳过了更早的公告**。
> 我手工把缺的那条（「新课程上线：微服务网关与治理专题」）补回 user 3 的收件箱，现在**共 3 条公告**、
> 其中 1 条未读。

---

## 4 · 验证

| 场景 | 结果 |
|---|---|
| 反复打开公告页 6 次 | ✅ 收件箱总数**保持 3**（不再重复插入） |
| 点开未读公告 → `PUT /inboxes/mark/{id}` | ✅ 返回 `(200, true)` |
| **刷新后复查同一条** | ✅ `isRead=true`（**已读状态真正落库**，不会变回未读） |
| 剩余未读 | ✅ 归零 |
| 旧方法 `POST` | ✅ HTTP 500（证实方法不匹配就是根因） |
| 前端语法 / vite 编译 | ✅ 通过 |

> 验证完我把那条公告**重新置回未读**，留给你自己点一次看效果。

---

## 5 · 两个附带发现（未改，等你决定）

1. **顶栏未读数一直是写死的 0**
   `UserInboxController` 里这两个接口直接 `return 0`（注释写着"本地演示还没种通知数据，先让门户头部安静"）：
   ```java
   @GetMapping("/unread")            public Integer queryUnreadCount() { return 0; }
   @GetMapping("/unread/{type}")     public Integer queryUnreadCountByType(...) { return 0; }
   ```
   现在收件箱里有真实数据了，**顶栏的未读红点永远不会出现**、`我的消息` 页的分类未读数也恒为 0。
   要接真数据的话就是两个 count 查询，改动很小。

2. **只能增量拉取会漏公告 + 表里没存 `notice_id`**
   上面说的"缺的那条补不回来"就是这个设计导致的；而且因为没存 `notice_id`，去重只能按"时间 + 标题"这个业务键。
   如果你希望公告页**始终展示全部有效公告**（而不是增量），那是另一套逻辑，需要改后端查询口径。

---

## 6 · 教训（已写进项目记忆）

> **前端调用的 HTTP 方法必须与后端 `@XxxMapping` 对齐** —— 不一致时后端会直接失败，
> 而**前端如果把异常静默吞掉（`catch {}` 空实现），这个 bug 可以潜伏很久**，且症状会伪装成"数据没保存"。
> 规则：`catch` 里**至少留一条 `console.warn`**，别让失败无声无息。
