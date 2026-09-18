# Redis 数据结构精讲

> 知序学堂演示课程资料（Demo）。本文件同时用于「课程目录」与「课程 AI 助教知识库」。

## 课程简介

本课程系统讲解 Redis 的九种数据结构及其底层实现，结合实际场景说明每种结构的
适用边界与常见坑点。学完后应能根据业务需求选择正确的数据结构，
并理解 Redis 单线程模型下的性能特征。

**适合人群**：具备 Java 或 Python 基础、了解基本数据库概念的开发者。

---

## 第一章 Redis 基础与核心数据结构

本章目标：理解 Redis 的定位与单线程模型，掌握 String、Hash、List 三种基础结构。

### 1.1 Redis 是什么

Redis 是一个基于内存的 **key-value 数据库**，常被用作缓存、分布式锁、消息队列和计数器。

**核心特点**

| 特点 | 说明 |
| --- | --- |
| 基于内存 | 读写速度极快，可达 10 万级 QPS |
| 单线程模型 | 命令串行执行，天然避免并发竞争 |
| 丰富的数据结构 | String、Hash、List、Set、ZSet、BitMap、HyperLogLog、GEO、Stream |
| 持久化 | RDB 快照与 AOF 日志两种方式 |
| 高可用 | 主从复制、哨兵、Cluster 集群 |

**为什么单线程还快**

1. 操作都在内存中完成，无磁盘 IO。
2. 避免了多线程的上下文切换与锁竞争。
3. 采用 IO 多路复用（epoll）处理大量连接。

**注意**：Redis 6.0 之后网络 IO 采用多线程，但命令执行仍是单线程。
因此**单条命令必须是原子的、快的**，一个 `keys *` 就可能阻塞整个实例。

### 1.2 String（字符串）

最基础的类型，value 最大 512MB。

**常用命令**

```
SET key value            设置值
GET key                  获取值
SETNX key value          不存在才设置（分布式锁基础）
SETEX key seconds value  设置值并指定过期时间
INCR key                 原子自增
DECR key                 原子自减
MSET / MGET              批量设置 / 获取
```

**典型场景**

- **缓存对象**：把对象序列化为 JSON 存入。
- **计数器**：文章阅读量、点赞数，用 `INCR` 保证原子性。
- **分布式锁**：`SET key value NX EX 10`，一条命令同时保证互斥与过期。
- **限流**：结合 `INCR` + `EXPIRE` 实现固定窗口计数。

**易错点**

- `SETNX` + `EXPIRE` 分两条命令不是原子的，推荐用 `SET key value NX EX`。
- 大 key（如几 MB 的字符串）会阻塞网络传输，应拆分。

### 1.3 Hash（哈希）

value 本身是一个键值对集合，适合存储对象的多个字段。

**常用命令**

```
HSET key field value     设置字段
HGET key field           获取字段
HMSET / HMGET            批量操作
HGETALL key              获取全部字段
HDEL key field           删除字段
HINCRBY key field n      字段原子自增
```

**典型场景**

- **存储对象**：`HSET user:1 name tom age 18`，可单独更新某个字段。
- **购物车**：`HSET cart:{userId} {productId} {count}`，天然支持增减数量。

**String 存对象 vs Hash 存对象**

| 对比项 | String（JSON） | Hash |
| --- | --- | --- |
| 更新单个字段 | 需读出整个对象再写回 | 直接 HSET 该字段 |
| 存储开销 | 较小（只有一个 key） | 略大（有 field 元数据） |
| 适合场景 | 整体读写 | 频繁局部更新 |

### 1.4 List（列表）

有序、可重复的双向链表，支持两端插入删除。

**常用命令**

```
LPUSH / RPUSH            左 / 右插入
LPOP / RPOP              左 / 右弹出
LRANGE key start stop    范围查询
LLEN key                 长度
BLPOP / BRPOP            阻塞式弹出
```

**典型场景**

- **消息队列**：`LPUSH` 生产、`BRPOP` 消费，实现简单的先进先出队列。
- **最新列表**：`LPUSH` + `LTRIM 0 99` 只保留最近 100 条。
- **栈**：同一端 `LPUSH` + `LPOP`。

**易错点**

- List 底层是 quicklist（链表 + 压缩列表），随机访问中间元素是 O(n)。
- 作为队列使用时，若消费端不处理失败重试，消息可能丢失，可靠性不如专业的 MQ。

---

## 第二章 高级数据结构与底层实现

本章目标：掌握 Set、ZSet 及扩展结构的使用场景，理解底层编码与性能特征。

### 2.1 Set（集合）

无序、不重复，支持集合运算。

**常用命令**

```
SADD / SREM              添加 / 移除元素
SMEMBERS key             获取全部元素
SISMEMBER key member     判断是否存在
SCARD key                元素个数
SINTER / SUNION / SDIFF  交集 / 并集 / 差集
```

**典型场景**

- **去重**：文章点赞用户、活动参与用户。
- **共同好友**：`SINTER user:1:friends user:2:friends`。
- **抽奖**：`SPOP` 随机弹出，或用 `SRANDMEMBER` 随机返回不删除。

**注意**：`SMEMBERS` 在元素多时会返回大量数据并阻塞，
生产环境应使用 `SSCAN` 分批遍历。

### 2.2 ZSet（有序集合）

每个元素关联一个 score，按 score 排序，是 Redis 最有特色的结构。

**常用命令**

```
ZADD key score member         添加元素
ZSCORE key member             获取分数
ZINCRBY key incr member       分数自增
ZRANGE key start stop         按分数升序取范围
ZREVRANGE key start stop      按分数降序取范围
ZRANGEBYSCORE key min max     按分数区间查询
ZRANK / ZREVRANK              获取排名
ZREM                          删除元素
```

**典型场景**

- **排行榜**：`ZADD rank 100 user:1`，`ZREVRANGE rank 0 9` 取前十。
- **延迟队列**：score 存执行时间戳，定时轮询到期的任务。
- **带权重的队列**：score 表示优先级。

**底层实现**：元素较少时用 ziplist（压缩列表），
超过阈值后转为 skiplist（跳表）+ 字典，兼顾范围查询与单点查询效率。

### 2.3 扩展数据结构

**BitMap（位图）**

基于 String 实现的位操作，极其节省空间。

```
SETBIT sign:202609 15 1       第 15 位设为 1
GETBIT sign:202609 15         查询某位
BITCOUNT sign:202609          统计 1 的个数
```

场景：签到统计、活跃用户统计。1 亿用户一个月只需约 12MB。

**HyperLogLog**

基数统计结构，用极小空间（12KB）估算去重元素个数，标准误差约 0.81%。

```
PFADD uv:20260911 user1 user2
PFCOUNT uv:20260911
```

场景：UV 统计。**注意**：只能估算基数，无法返回具体元素，也不够精确。

**GEO**

基于 ZSet 实现的地理位置结构。

```
GEOADD shops 116.40 39.90 shop1
GEODIST shops shop1 shop2 km
GEOSEARCH shops FROMMEMBER shop1 BYRADIUS 5 km
```

场景：附近的人、附近的店。

**Stream**

Redis 5.0 引入的消息队列结构，支持消费组、消息确认（ACK）与持久化，
比 List 更适合做消息队列。

```
XADD mystream * name tom
XREADGROUP GROUP g1 c1 COUNT 1 STREAMS mystream >
XACK mystream g1 {id}
```

### 2.4 编码转换与性能

Redis 会依据元素数量与大小自动切换底层编码，以平衡内存与性能：

| 类型 | 小数据量编码 | 大数据量编码 | 关键配置 |
| --- | --- | --- | --- |
| String | int / embstr | raw | 44 字节 |
| Hash | ziplist | hashtable | hash-max-ziplist-entries |
| List | quicklist | quicklist | list-max-ziplist-size |
| Set | intset / ziplist | hashtable | set-max-intset-entries |
| ZSet | ziplist | skiplist | zset-max-ziplist-entries |

**实践建议**：控制单 key 的元素数量，避免超过阈值导致编码转换带来性能波动。
可用 `OBJECT ENCODING key` 查看当前编码。

### 2.5 本章小结

- Set 用于去重与集合运算，注意避免 `SMEMBERS` 大范围返回。
- ZSet 是排行榜与延迟队列的首选，score 是排序依据。
- BitMap 省空间，HyperLogLog 适合估算基数，GEO 处理地理位置，Stream 做可靠消息队列。
- 理解编码转换有助于写出高性能的 Redis 用法。

---

## 第三章 项目实战与常见问题

本章目标：掌握缓存使用模式、过期策略与常见线上问题。

### 3.1 缓存使用模式

**Cache Aside（旁路缓存）**：最常用的模式。

读流程：先查缓存 → 命中则返回 → 未命中则查数据库 → 写回缓存。
写流程：先更新数据库 → 再删除缓存。

**为什么是删除缓存而不是更新缓存**

- 更新缓存可能产生并发写覆盖，导致缓存与数据库不一致。
- 删除缓存更简单，下次读取时自然重建。

**延迟双删**：更新数据库后删除缓存，稍后再删一次，
用于降低主从复制延迟导致的脏数据概率。

### 3.2 过期与淘汰策略

**过期策略**

- 惰性删除：访问 key 时才检查是否过期。
- 定期删除：周期性随机抽查一部分 key 删除。
- 两者结合，兼顾 CPU 与内存。

**内存淘汰策略**（`maxmemory-policy`）

| 策略 | 说明 |
| --- | --- |
| noeviction | 不淘汰，写入报错（默认） |
| allkeys-lru | 所有 key 中淘汰最近最少使用 |
| allkeys-lfu | 所有 key 中淘汰访问频率最低 |
| volatile-lru | 仅从设置了过期时间的 key 中淘汰 |
| volatile-ttl | 优先淘汰即将过期的 key |

**缓存场景通常使用 allkeys-lru 或 allkeys-lfu。**

### 3.3 常见线上问题

**缓存穿透**：查询不存在的数据，请求每次都打到数据库。

- 解决：缓存空值并设短过期时间；使用布隆过滤器提前拦截。

**缓存击穿**：某个热点 key 过期瞬间，大量请求同时打到数据库。

- 解决：热点 key 不设过期时间；或加互斥锁只让一个线程重建缓存。

**缓存雪崩**：大量 key 同时过期，数据库瞬时压力剧增。

- 解决：过期时间加随机值，打散过期时间点；多级缓存兜底。

**三大问题的区别**

| 问题 | 根本原因 | 特征 |
| --- | --- | --- |
| 穿透 | 数据本身不存在 | 每次都穿透到库 |
| 击穿 | 单个热点 key 失效 | 某一个 key 集中打库 |
| 雪崩 | 大量 key 同时失效 | 整体打库，影响面最大 |

**分布式锁注意事项**

- 加锁：`SET lock uuid NX EX 30`，value 用唯一标识。
- 解锁：必须校验 value 一致再删除，建议用 Lua 脚本保证原子性。
- 续期：业务执行时间可能超过锁过期时间，需要看门狗机制续期。
- 正确性要求高时，建议使用 Redisson 的看门狗实现。

### 3.4 本章小结

- Cache Aside 是主流缓存模式，写操作后删除缓存而非更新缓存。
- 合理设置过期时间与淘汰策略，避免内存被写满。
- 穿透、击穿、雪崩的成因不同，防护手段也不同。
- 分布式锁要用唯一 value + Lua 解锁，并考虑续期问题。

---

## 综合练习

### 选择题

**1. 需要实现一个实时更新的排行榜，最适合的数据结构是？**
A. String　B. List　C. ZSet　D. Set
答案：C。ZSet 按 score 排序，支持范围查询与排名。

**2. 关于 Redis 单线程模型，下列说法正确的是？**
A. 所有操作都是多线程的
B. 命令执行是单线程，一条慢命令会阻塞其他请求
C. 因为是单线程，所以不需要考虑大 key 问题
D. 单线程意味着不能用 IO 多路复用
答案：B。

**3. 缓存击穿指的是？**
A. 查询不存在的数据，每次都打到数据库
B. 单个热点 key 过期的瞬间大量请求打到数据库
C. 大量 key 同时过期导致数据库压力剧增
D. 缓存与数据库数据不一致
答案：B。A 是穿透，C 是雪崩。

**4. 存储一个对象并且需要频繁更新其中某个字段，更合适的选择是？**
A. String 存整段 JSON
B. Hash
C. List
D. BitMap
答案：B。Hash 可直接更新单个字段，无需读改写整个对象。

### 判断题

**5. 使用 `SETNX` 加 `EXPIRE` 两条命令实现分布式锁是原子操作。**
答案：错误。两条命令之间存在时间窗口，若在设置过期前宕机，锁将永不过期。应使用 `SET key value NX EX seconds`。

**6. HyperLogLog 可以精确返回所有去重后的元素。**
答案：错误。它只做基数估算，标准误差约 0.81%，无法返回具体元素。

### 简答题

**7. 简述缓存穿透、击穿、雪崩的区别及各自的解决方案。**
参考答案：穿透是查询不存在的数据，每次都打到数据库，可用缓存空值或布隆过滤器；
击穿是单个热点 key 失效导致请求集中打库，可用互斥锁重建或热点 key 不设过期；
雪崩是大量 key 同时失效，可给过期时间加随机值并配合多级缓存。

**8. 为什么更新数据时要删除缓存而不是更新缓存？**
参考答案：并发写场景下，两个请求同时更新缓存可能出现后写的旧值覆盖新值；
删除缓存更简单且能保证下次读取时重建最新数据，配合延迟双删可进一步降低不一致概率。

**9. 什么是大 key？它会带来什么问题？**
参考答案：大 key 指单个 key 存储的数据量过大，如几 MB 的 String 或包含几十万元素的 Hash。
问题在于：传输耗时长会阻塞其他请求、删除时可能造成长时间阻塞、
集群模式下容易造成数据倾斜。应通过拆分 key 或分批处理来规避。

**10. 使用 Redis 做分布式锁时需要注意哪些点？**
参考答案：一是加锁要用 `SET NX EX` 保证原子性与过期保护；
二是 value 使用唯一标识，解锁时必须校验 value 并建议用 Lua 保证原子性，避免误删他人锁；
三是考虑业务执行超时，需要续期（看门狗）机制；
四是集群模式下需注意主从切换可能导致的锁失效问题，高要求场景可使用 Redlock 或 Redisson。
