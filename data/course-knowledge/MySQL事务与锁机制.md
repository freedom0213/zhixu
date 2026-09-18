# MySQL 事务与锁机制

> 本文件为「知序学堂」课程知识库资料，供课程 AI 助教检索与问答使用。

## 课程简介

讲透事务的 ACID 与隔离级别，以及 InnoDB 的锁与 MVCC 是如何配合工作的。

本课程从事务的四大特性出发，逐层讲解四种隔离级别、各类并发异常、InnoDB 的行锁与间隙锁，以及 MVCC 的多版本并发控制原理，最后落到死锁分析与事务优化实践。

---

## 第一章 事务基础

本章建立事务的概念框架，重点理解隔离级别与并发异常之间的对应关系。

### 1.1 事务与 ACID

**学习目标**：理解事务的四个特性，并掌握事务的开启与提交方式。

**核心知识点**

- 原子性 A：事务内的操作要么全部成功，要么全部回滚
- 一致性 C：事务前后数据都满足业务约束，是最终目的
- 隔离性 I：并发事务之间互不干扰，靠锁与 MVCC 实现
- 持久性 D：提交后的数据不因宕机丢失，靠 redo log 保证

**示例**

```sql
START TRANSACTION;
UPDATE account SET balance = balance - 100 WHERE id = 1;
UPDATE account SET balance = balance + 100 WHERE id = 2;
COMMIT;   -- 出错时改为 ROLLBACK;
```

**易错点**：开启事务后长时间不提交，会一直持有锁并撑大 undo log，务必让事务尽量短。

### 1.2 事务的隔离级别

**学习目标**：掌握四种隔离级别及其默认值，理解隔离性与性能的权衡。

**核心知识点**

- 读未提交 READ UNCOMMITTED 隔离最低，可能读到别的事务未提交的数据
- 读已提交 READ COMMITTED 避免脏读，是 Oracle、PostgreSQL 的默认级别
- 可重复读 REPEATABLE READ 避免不可重复读，是 MySQL InnoDB 的默认级别
- 串行化 SERIALIZABLE 隔离最高但并发最差，一般不用于生产

**示例**

```sql
SELECT @@transaction_isolation;
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
```

**易错点**：把隔离级别与数据库默认值记混很常见：MySQL 默认是可重复读，而不是读已提交。

### 1.3 隔离级别引发的问题

**学习目标**：能区分脏读、不可重复读、幻读三种并发异常。

**核心知识点**

- 脏读：读到了其他事务尚未提交的数据，一旦对方回滚，读到的就是脏数据
- 不可重复读：同一事务内两次读同一行结果不同，因为别的事务提交了修改
- 幻读：同一事务内两次范围查询多出或少了几行，因为别的事务提交了插入或删除
- InnoDB 在可重复读下用 MVCC 解决不可重复读，用间隙锁在很大程度上避免幻读

**示例**

```sql
-- 不可重复读示例（READ COMMITTED 下）
SELECT balance FROM account WHERE id = 1;  -- 得到 100
-- 此时另一个事务提交了 update balance = 200
SELECT balance FROM account WHERE id = 1;  -- 得到 200
```

**易错点**：把幻读与不可重复读混为一谈：不可重复读针对同一行的值变化，幻读针对结果集行数变化。

---

## 第二章 InnoDB 锁机制

本章讲解 InnoDB 各个粒度的锁，以及不同查询方式加锁行为的差异。

### 2.1 共享锁与排他锁

**学习目标**：掌握 S 锁与 X 锁的兼容关系及加锁语法。

**核心知识点**

- 共享锁 S 锁：多个事务可以同时持有同一行的 S 锁，用 LOCK IN SHARE MODE 加
- 排他锁 X 锁：与任何锁都不兼容，用 FOR UPDATE 加
- 普通 SELECT 不加锁，属于快照读；加锁读需要显式写 FOR UPDATE 或 LOCK IN SHARE MODE
- S 锁与 X 锁的互斥是保证写一致性的基础

**示例**

```sql
SELECT * FROM account WHERE id = 1 FOR UPDATE;          -- 加 X 锁
SELECT * FROM account WHERE id = 1 LOCK IN SHARE MODE;  -- 加 S 锁
```

**易错点**：以为普通 SELECT 会加锁，因此在事务里读到的值可以直接拿来更新，实际上快照读可能读到旧值导致更新丢失。

### 2.2 行锁、间隙锁与临键锁

**学习目标**：理解三种行级锁粒度的作用范围。

**核心知识点**

- 记录锁 Record Lock 锁住索引上的一条具体记录
- 间隙锁 Gap Lock 锁住两条记录之间的空隙，防止其他事务在间隙中插入
- 临键锁 Next-Key Lock 是记录锁加间隙锁，左开右闭，是 InnoDB 可重复读下的默认加锁单位
- 若查询没有命中索引，行锁会退化为表锁，这是线上事故的常见原因

**示例**

```sql
-- 假设 id 为 1、5、10，执行下列语句会锁住 (1,5] 这个区间
SELECT * FROM t WHERE id > 1 AND id <= 5 FOR UPDATE;
```

**易错点**：在无索引或索引失效的列上做加锁读，会把行锁升级成表锁，直接堵死整张表。

### 2.3 意向锁与锁等待

**学习目标**：理解意向锁的作用，并能读懂锁等待与超时。

**核心知识点**

- 意向共享锁 IS 与意向排他锁 IX 是表级锁，用来快速判断表中是否存在行级锁
- 意向锁之间互相兼容，只与表级 S 锁和 X 锁互斥
- lock_wait_timeout 控制锁等待超时，超时后报 Lock wait timeout exceeded
- 通过 information_schema.INNODB_TRX 与 INNODB_LOCK_WAITS 可以定位阻塞关系

**示例**

```sql
SELECT trx_id, trx_state, trx_started, trx_query
FROM information_schema.INNODB_TRX;
```

**易错点**：出现锁等待时只重启应用不查事务，往往会导致同一问题反复发生，应先找出长事务的持有者。

---

## 第三章 MVCC 与实战

本章解释 MVCC 如何让读不加锁也能保证一致性，并给出死锁排查思路。

### 3.1 MVCC 原理

**学习目标**：理解隐藏列、undo log 版本链与 Read View 三者如何协作。

**核心知识点**

- InnoDB 每行有隐藏列 DB_TRX_ID 记录最后修改事务的 id，DB_ROLL_PTR 指向 undo log 中的旧版本
- undo log 把同一行的历史版本串成版本链，越旧越靠后
- Read View 记录创建时刻活跃的事务列表，用于判断某个版本是否可见
- 读已提交在每次 SELECT 时新建 Read View，可重复读在事务首次 SELECT 时创建并复用

**示例**

```sql
-- 查看当前事务 id
SELECT TRX_ID FROM information_schema.INNODB_TRX WHERE TRX_MYSQL_THREAD_ID = CONNECTION_ID();
```

**易错点**：认为可重复读下看到的永远是最新数据，实际上它读到的是事务开始时的快照版本。

### 3.2 快照读与当前读

**学习目标**：能区分快照读与当前读，避免更新丢失。

**核心知识点**

- 快照读：普通 SELECT，读取 MVCC 生成的快照，不加锁
- 当前读：SELECT ... FOR UPDATE、SELECT ... LOCK IN SHARE MODE，以及 UPDATE、DELETE、INSERT，读取最新版本并加锁
- UPDATE 与 DELETE 都属于当前读，所以它们能看到并修改别的事务已提交的最新值
- 先快照读再据此计算更新，容易出现丢失更新，正确做法是使用当前读或原子更新

**示例**

```sql
-- 丢失更新：两条 SET 都基于旧值，最终结果会少算一次
UPDATE account SET balance = balance - 100 WHERE id = 1;  -- 原子更新更安全
```

**易错点**：把快照读出来的值作为计算基准再写回，在高并发下会覆盖别人的修改，应改为在 UPDATE 中直接做运算。

### 3.3 死锁分析与事务优化

**学习目标**：能读懂死锁日志并给出事务优化方案。

**核心知识点**

- 死锁的成因是两个事务以相反顺序持有并等待对方的锁
- InnoDB 检测到死锁后会主动回滚代价较小的事务，并输出 LATEST DETECTED DEADLOCK 日志
- 预防手段：统一加锁顺序、缩短事务、尽快提交、给查询走索引避免锁范围扩大
- SHOW ENGINE INNODB STATUS 可以查看最近一次死锁的详细信息

**示例**

```sql
SHOW ENGINE INNODB STATUS;   -- 查看 LATEST DETECTED DEADLOCK 段落
```

**易错点**：在事务中做远程调用或批量耗时操作，会把事务时长拉长到秒级，锁冲突概率成倍上升。

---

## 综合练习

### 单项选择题

**1. MySQL InnoDB 的默认事务隔离级别是？**

- A. 读未提交
- B. 读已提交
- C. 可重复读
- D. 串行化

**答案**：C

**解析**：InnoDB 的默认隔离级别是可重复读 REPEATABLE READ，通过 MVCC 避免不可重复读，并用间隙锁在很大程度上避免幻读。

**对应知识点**：隔离级别

**2. 在同一事务内两次执行同一条范围查询，第二次发现多出了几行，这属于哪种并发异常？**

- A. 脏读
- B. 不可重复读
- C. 幻读
- D. 丢失更新

**答案**：C

**解析**：结果集行数发生变化是幻读的典型特征，通常由别的事务提交了 INSERT 或 DELETE 引起。

**对应知识点**：并发异常

**3. 以下哪条语句会加排他锁（X 锁）？**

- A. SELECT * FROM t WHERE id = 1
- B. SELECT * FROM t WHERE id = 1 FOR UPDATE
- C. SELECT * FROM t WHERE id = 1 LOCK IN SHARE MODE
- D. SHOW ENGINE INNODB STATUS

**答案**：B

**解析**：FOR UPDATE 加排他锁；LOCK IN SHARE MODE 加共享锁；普通 SELECT 属于快照读，不加锁。

**对应知识点**：S 锁与 X 锁

**4. 关于临键锁 Next-Key Lock，下列说法正确的是？**

- A. 它是记录锁与间隙锁的组合，左开右闭
- B. 它只锁住一条具体记录
- C. 它只在串行化隔离级别下生效
- D. 它属于表级锁

**答案**：A

**解析**：临键锁是记录锁加间隙锁，左开右闭，是 InnoDB 可重复读下的默认加锁单位。

**对应知识点**：锁粒度

**5. 在加锁读时，如果查询条件没有走索引，可能发生什么？**

- A. 锁自动升级为表锁
- B. 锁自动降级为无锁
- C. 事务自动提交
- D. 索引自动创建

**答案**：A

**解析**：没有命中索引时无法定位到具体记录，行锁会退化为表锁，导致并发能力急剧下降。

**对应知识点**：锁升级

### 判断题

**1. UPDATE 语句属于当前读，读取的是最新已提交版本而不是快照版本。**

**答案**：正确

**解析**：正确。UPDATE、DELETE、INSERT 以及加锁读都属于当前读，会读取最新版本并加锁。

**对应知识点**：快照读与当前读

**2. 为避免丢失更新，可以先普通 SELECT 读出余额，在应用里减 100 后再写回。**

**答案**：错误

**解析**：错误。普通 SELECT 是快照读，高并发下会覆盖其他事务的修改，应改为在 UPDATE 中直接做原子运算。

**对应知识点**：丢失更新

**3. 统一多个事务的加锁顺序，有助于降低死锁发生的概率。**

**答案**：正确

**解析**：正确。死锁往往源于两个事务以相反顺序持有并等待锁，统一加锁顺序能有效破坏这个条件。

**对应知识点**：死锁预防
