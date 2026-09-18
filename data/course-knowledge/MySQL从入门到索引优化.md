# MySQL 从入门到索引优化

> 本文件为「知序学堂」课程知识库资料，供课程 AI 助教检索与问答使用。

## 课程简介

从零掌握 MySQL 的建库建表、SQL 查询，直到索引原理与 SQL 性能优化。

本课程从数据库基本操作讲起，覆盖数据类型、约束、多表连接与聚合，再深入到 B+ 树索引原理、索引设计原则与执行计划分析，帮助学习者写出跑得更快的 SQL。

---

## 第一章 MySQL 基础与 SQL 入门

本章建立对关系型数据库的基本认知，掌握建库建表与最常用的增删改查语法。

### 1.1 数据库与表的基本操作

**学习目标**：能够独立完成数据库、数据表的创建与基本的增删改查。

**核心知识点**

- DDL 负责定义结构：CREATE / ALTER / DROP，作用于库和表本身
- DML 负责操作数据：INSERT / UPDATE / DELETE，作用于表中的行
- DQL 负责查询数据：SELECT，是日常使用频率最高的语句
- 生产环境执行 DROP 与 DELETE 前必须先确认影响范围，DELETE 建议先写成 SELECT 验证

**示例**

```sql
CREATE DATABASE demo DEFAULT CHARACTER SET utf8mb4;
USE demo;
CREATE TABLE user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  age INT DEFAULT 0
);
INSERT INTO user (name, age) VALUES ('张三', 20);
SELECT id, name, age FROM user WHERE age > 18;
```

**易错点**：DELETE 不带 WHERE 会清空整表；DROP TABLE 会连同结构与数据一起删除且不可回滚。

### 1.2 数据类型与约束

**学习目标**：能根据业务含义选择合适的数据类型，并用约束保证数据正确性。

**核心知识点**

- 整数按取值范围选 TINYINT / INT / BIGINT，金额用 DECIMAL 而非 FLOAT，避免精度丢失
- 字符串区分 CHAR（定长）与 VARCHAR（变长），长度按业务上限设置而不是一律 255
- 主键约束唯一且非空；UNIQUE 保证唯一但允许出现一个空值
- 外键约束能保证引用完整性，但高并发场景常由应用层保证，避免锁竞争

**示例**

```sql
CREATE TABLE orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_no (user_id)
);
```

**易错点**：用 FLOAT 存金额会出现 0.1 + 0.2 不等于 0.3 的精度问题，金额一律用 DECIMAL。

### 1.3 基础查询与条件过滤

**学习目标**：掌握 SELECT 的过滤、排序、分页与去重写法。

**核心知识点**

- WHERE 在分组前过滤行，HAVING 在分组后过滤组，两者位置不能互换
- ORDER BY 支持多字段排序，分页用 LIMIT offset, size，注意大 offset 会越翻越慢
- IN、BETWEEN、LIKE 是常用过滤手段，LIKE 以 % 开头会导致索引失效
- NULL 不能与 = 比较，必须用 IS NULL / IS NOT NULL

**示例**

```sql
SELECT id, name, age FROM user
WHERE age BETWEEN 18 AND 30
  AND name LIKE '张%'
ORDER BY age DESC, id ASC
LIMIT 0, 10;
```

**易错点**：WHERE age = NULL 永远返回空结果，必须写 age IS NULL。

---

## 第二章 多表查询与聚合

本章解决多表关联取值与统计汇总的问题，是业务查询的主要形态。

### 2.1 连接查询（JOIN）

**学习目标**：掌握 INNER JOIN、LEFT JOIN 的区别与选择。

**核心知识点**

- INNER JOIN 只保留两边都匹配上的行，LEFT JOIN 保留左表全部行、右表无匹配补 NULL
- 小表驱动大表是优化思路，Join 字段上应有索引
- LEFT JOIN 后再对右表字段做 WHERE 条件，会让 LEFT JOIN 退化成 INNER JOIN，条件应写在 ON 里
- 多表连接字段类型必须一致，否则会触发隐式转换导致索引失效

**示例**

```sql
SELECT o.id, o.amount, u.name
FROM orders o
LEFT JOIN user u ON u.id = o.user_id
WHERE o.status = 1;
```

**易错点**：在 WHERE 里对右表字段加条件会丢掉左表未匹配的行，过滤条件应放到 ON 子句。

### 2.2 聚合函数与分组

**学习目标**：能用 GROUP BY 与聚合函数完成统计报表类需求。

**核心知识点**

- COUNT(*) 统计行数，COUNT(字段) 会忽略该字段的 NULL 值，两者结果可能不同
- GROUP BY 后的 SELECT 只应出现分组字段与聚合函数
- SUM、AVG、MAX、MIN 是最常用聚合，AVG 同样忽略 NULL
- 过滤聚合结果必须用 HAVING，不能用 WHERE

**示例**

```sql
SELECT user_id, COUNT(*) AS order_count, SUM(amount) AS total
FROM orders
WHERE status = 1
GROUP BY user_id
HAVING total > 1000
ORDER BY total DESC;
```

**易错点**：在 WHERE 中写聚合函数会直接报错，聚合结果的过滤只能用 HAVING。

### 2.3 子查询与常用函数

**学习目标**：掌握子查询的写法，并了解何时应改写为 JOIN。

**核心知识点**

- 标量子查询返回单值，可用于 SELECT 或 WHERE 中比较
- IN 子查询适合小结果集，NOT IN 遇到 NULL 会返回空结果，应改用 NOT EXISTS
- 相关子查询对外层每一行都执行一次，数据量大时性能差，优先改写成 JOIN
- 常用函数包括 IFNULL、CASE WHEN、DATE_FORMAT、CONCAT

**示例**

```sql
SELECT u.name,
       (SELECT COUNT(*) FROM orders o WHERE o.user_id = u.id) AS cnt
FROM user u
WHERE u.id IN (SELECT user_id FROM orders WHERE amount > 1000);
```

**易错点**：NOT IN 的子查询结果里只要含一个 NULL，整个条件就恒为空，应改用 NOT EXISTS。

---

## 第三章 索引原理与 SQL 优化

本章解释索引为什么快、什么时候失效，以及如何通过执行计划定位慢查询。

### 3.1 索引的数据结构（B+ 树）

**学习目标**：理解 InnoDB 索引为什么选用 B+ 树，以及聚簇索引与二级索引的区别。

**核心知识点**

- B+ 树只有叶子节点存数据，非叶子节点只存键，因此单页能容纳更多键、树更矮，磁盘 IO 更少
- InnoDB 主键索引即聚簇索引，叶子节点直接存放整行数据，一张表只有一个
- 二级索引叶子节点存放主键值，因此查询非索引列需要回表
- 覆盖索引指查询所需字段都在索引中，可避免回表，是常见优化手段

**示例**

```sql
-- 建复合索引
ALTER TABLE orders ADD INDEX idx_user_status (user_id, status);
-- 覆盖索引：只查索引里已有的列，无需回表
SELECT user_id, status FROM orders WHERE user_id = 8;
```

**易错点**：把主键设计得过大或无序（如 UUID）会让二级索引体积膨胀、插入随机化，建议用自增 BIGINT 主键。

### 3.2 索引类型与创建原则

**学习目标**：能够为真实查询设计出有效的索引，并识别索引失效场景。

**核心知识点**

- 复合索引遵循最左前缀原则，查询条件必须从索引最左列开始才能用上索引
- 在索引列上做函数运算或隐式类型转换会导致索引失效
- 区分度低的列（如性别）单独建索引意义不大，适合放在复合索引靠后位置
- 索引不是越多越好，每个索引都会拖慢写入并占用空间

**示例**

```sql
-- 复合索引 (a, b, c)
SELECT * FROM t WHERE a = 1;             -- 用上索引
SELECT * FROM t WHERE a = 1 AND b = 2;   -- 用上索引
SELECT * FROM t WHERE b = 2;             -- 用不上，违反最左前缀
```

**易错点**：WHERE DATE(created_at) = '2026-09-11' 会让索引失效，应改写成范围条件 created_at >= '2026-09-11' AND created_at < '2026-09-12'。

### 3.3 执行计划与慢查询优化

**学习目标**：会读 EXPLAIN 结果，并能据此定位与改写慢 SQL。

**核心知识点**

- EXPLAIN 的重点字段：type（访问类型）、key（实际使用的索引）、rows（预估扫描行数）、Extra
- type 从优到劣：system > const > eq_ref > ref > range > index > ALL，出现 ALL 说明全表扫描
- Extra 出现 Using filesort 或 Using temporary 通常意味着需要优化排序或分组
- 慢查询日志 slow_query_log 用于捞取执行超时的 SQL，是优化的入口

**示例**

```sql
EXPLAIN SELECT id, name FROM user WHERE age > 18 ORDER BY id;
-- 关注 type 是否为 range/ref，key 是否命中索引，rows 是否过大
```

**易错点**：只看 SQL 语句不看执行计划就加索引，容易加出用不上的索引，反而增加写入成本。

---

## 综合练习

### 单项选择题

**1. 存储金额时，以下哪种数据类型最合适？**

- A. FLOAT
- B. DOUBLE
- C. DECIMAL(10,2)
- D. VARCHAR(20)

**答案**：C

**解析**：FLOAT 与 DOUBLE 是浮点数，存在精度丢失，不能用于金额；DECIMAL 是定点数，能精确表示小数。VARCHAR 无法参与数值统计。

**对应知识点**：数据类型选择

**2. 关于 WHERE 与 HAVING 的区别，下列说法正确的是？**

- A. HAVING 在分组前过滤行
- B. WHERE 可以过滤聚合函数的结果
- C. WHERE 在分组前过滤行，HAVING 在分组后过滤组
- D. 两者完全等价，可以互相替换

**答案**：C

**解析**：WHERE 作用于分组前的原始行，HAVING 作用于分组后的结果，聚合函数只能出现在 HAVING 中。

**对应知识点**：聚合与分组

**3. 复合索引 (user_id, status, created_at)，以下哪个查询用不上该索引？**

- A. WHERE user_id = 1
- B. WHERE user_id = 1 AND status = 2
- C. WHERE status = 2
- D. WHERE user_id = 1 AND status = 2 AND created_at > '2026-01-01'

**答案**：C

**解析**：复合索引遵循最左前缀原则，查询条件必须以索引最左列 user_id 开头才能使用索引，直接查 status 会失效。

**对应知识点**：最左前缀原则

**4. EXPLAIN 结果中 type 字段出现 ALL，说明什么？**

- A. 使用了覆盖索引
- B. 进行了全表扫描
- C. 使用了主键等值查询
- D. 使用了范围索引扫描

**答案**：B

**解析**：type = ALL 表示全表扫描，是最差的访问类型，说明没有用上索引或索引失效。

**对应知识点**：执行计划

**5. InnoDB 中，二级索引的叶子节点存储的是什么？**

- A. 整行数据
- B. 主键值
- C. 指向磁盘地址的指针
- D. 被索引列的全部值

**答案**：B

**解析**：InnoDB 的二级索引叶子节点存放主键值，因此查询非索引列时需要拿主键回表到聚簇索引取整行数据。

**对应知识点**：聚簇索引与二级索引

### 判断题

**1. NOT IN 的子查询结果集中如果包含 NULL，整个条件会恒为假，返回空结果。**

**答案**：正确

**解析**：正确。与 NULL 比较的结果是 UNKNOWN，NOT IN 遇到 NULL 无法确定真值，通常改用 NOT EXISTS 规避。

**对应知识点**：子查询

**2. 为一张表创建的索引越多，查询越快，因此应当尽量多加索引。**

**答案**：错误

**解析**：错误。索引会占用存储空间，并且每次写入都要维护所有索引，索引过多会显著拖慢 INSERT / UPDATE / DELETE。

**对应知识点**：索引设计原则

**3. WHERE created_at >= '2026-09-11' AND created_at < '2026-09-12' 比 WHERE DATE(created_at) = '2026-09-11' 更有利于使用索引。**

**答案**：正确

**解析**：正确。在索引列上使用函数会导致索引失效，改写为范围条件后可以正常走索引。

**对应知识点**：索引失效场景
