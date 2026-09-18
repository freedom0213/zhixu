# Java 21 新特性实战

> 本文件为「知序学堂」课程知识库资料，供课程 AI 助教检索与问答使用。

## 课程简介

掌握 Java 21 在语言与并发层面的关键演进并落地到项目中。

本课程讲解记录类、密封类、模式匹配与文本块等语言特性，深入虚拟线程与结构化并发对高并发编程的改变，最后覆盖集合与 Stream 增强、新 API 以及升级迁移过程中的注意事项。

---

## 第一章 语言层面新特性

本章讲清让代码更简洁安全的新语法。

### 1.1 记录类 record

**学习目标**：掌握用 record 声明不可变数据载体。

**核心知识点**

- record 自动生成构造方法、访问器、equals、hashCode 与 toString
- record 字段隐式为 final，天然不可变，适合做 DTO 与值对象
- 访问器方法名与字段同名（如 name()），不像 JavaBean 那样带 get 前缀
- record 可以定义额外方法与静态成员，但不能声明实例字段

**示例**

```java
public record Course(Long id, String name, int price) {
    public boolean isFree() { return price == 0; }
}

Course c = new Course(1003L, "Java 集合与并发编程", 0);
System.out.println(c.name());   // Java 集合与并发编程
```

**易错点**：期望 record 能像普通类一样添加可变实例字段，实际上 record 的字段在声明处就已固定且不可变。

### 1.2 密封类与模式匹配

**学习目标**：掌握用密封类限制继承并配合模式匹配做分支。

**核心知识点**

- sealed 类通过 permits 明确列出允许继承的子类，子类必须是 final、sealed 或 non-sealed
- 密封类让编译器能够穷尽检查分支覆盖情况
- switch 支持类型模式匹配，可直接对类型做强转与绑定变量
- 记录模式可解构 record 的组件，实现嵌套匹配

**示例**

```java
sealed interface Shape permits Circle, Rect {}
record Circle(double r) implements Shape {}
record Rect(double w, double h) implements Shape {}

double area(Shape s) {
    return switch (s) {
        case Circle c -> Math.PI * c.r() * c.r();
        case Rect r   -> r.w() * r.h();
    };
}
```

**易错点**：switch 模式匹配中漏写某个子类型分支，在密封类场景下会导致编译报错，需完整覆盖或提供 default。

### 1.3 文本块与 switch 表达式

**学习目标**：掌握多行字符串与表达式式分支写法。

**核心知识点**

- 文本块用三引号声明，自动处理换行与缩进，适合 SQL 与 JSON 字面量
- 文本块中可用 \s 保留行尾空格，用 \ 取消换行
- switch 表达式可直接返回值，箭头语法不穿透，可避免忘记 break
- yield 用于在 switch 代码块分支中返回值

**示例**

```java
String sql = """
        SELECT id, name
        FROM course
        WHERE id = ?
        """;

String level = switch (score / 10) {
    case 10, 9 -> "优秀";
    case 8 -> "良好";
    default -> "待提升";
};
```

**易错点**：文本块会自动去掉公共缩进，若需要保留特定缩进要显式使用空格，否则格式会与预期不一致。

---

## 第二章 并发与虚拟线程

本章讲清 Java 21 在并发能力上的最大变化。

### 2.1 虚拟线程原理

**学习目标**：理解虚拟线程与平台线程的区别及适用场景。

**核心知识点**

- 虚拟线程由 JVM 调度，多个虚拟线程复用一个平台线程（载体线程）
- 虚拟线程在阻塞时会让出载体线程，因此能用同步写法获得高并发吞吐
- 创建成本极低，可以按请求创建，无需依赖线程池
- 适合高并发 IO 密集型任务，CPU 密集型任务仍应使用固定大小线程池

**示例**

```java
Thread.startVirtualThread(() -> {
    // 阻塞式调用不再需要改造成异步
    var data = httpClient.send(req, BodyHandlers.ofString());
});

var executor = Executors.newVirtualThreadPerTaskExecutor();
```

**易错点**：在虚拟线程中执行长时间同步的 CPU 计算，会占住载体线程，反而降低整体吞吐。

### 2.2 结构化并发

**学习目标**：理解把并发子任务当作一个整体的价值。

**核心知识点**

- 结构化并发把一组相关子任务放在同一作用域内，父任务等待全部子任务结束
- 任一子任务失败时可统一取消其余子任务，避免线程泄漏与资源悬挂
- 让并发代码的调用关系像结构化编程一样清晰，便于异常传播与调试
- 需在同一个线程作用域内使用，与父子任务的生命周期绑定

**示例**

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var user  = scope.fork(() -> loadUser(id));
    var order = scope.fork(() -> loadOrder(id));
    scope.join().throwIfFailed();
    return new Detail(user.get(), order.get());
}
```

**易错点**：把结构化并发当成线程池使用，在作用域外提交任务会导致异常与取消语义失效。

### 2.3 并发编程实践要点

**学习目标**：掌握迁移到虚拟线程时的注意事项。

**核心知识点**

- 避免在虚拟线程中大量使用 synchronized 包裹阻塞操作，可能造成载体线程被固定
- 优先使用 ReentrantLock 替代 synchronized，以获得更好的伸缩性
- ThreadLocal 在虚拟线程数量巨大时会带来内存压力，应谨慎使用或改用作用域值
- 不要池化虚拟线程，其设计目标就是低成本创建与销毁

**示例**

```java
// 推荐：使用显式锁而非 synchronized 包裹阻塞逻辑
private final ReentrantLock lock = new ReentrantLock();
lock.lock();
try { /* 业务逻辑 */ } finally { lock.unlock(); }
```

**易错点**：沿用在平台线程时代的线程池调优思路来使用虚拟线程，既无收益还会掩盖问题。

---

## 第三章 工程实践

本章关注 API 增强与升级迁移的落地问题。

### 3.1 集合与 Stream 增强

**学习目标**：掌握新增的集合与流式操作便利方法。

**核心知识点**

- List.of、Set.of、Map.of 提供简洁的不可变集合创建方式
- SequencedCollection 接口为有序集合统一了首尾元素访问方法
- Stream 新增 gather 等中间操作，扩展了流式处理能力
- 不可变集合不允许增删元素，误用会抛 UnsupportedOperationException

**示例**

```java
var list = List.of("a", "b", "c");
var first = list.getFirst();
var last  = list.getLast();
```

**易错点**：对 List.of 创建的结果调用 add，会直接抛出 UnsupportedOperationException。

### 3.2 常用新 API

**学习目标**：了解提升开发效率的新接口能力。

**核心知识点**

- HttpClient 支持同步与异步请求，并可结合虚拟线程简化高并发调用
- Optional 增加 stream 等方法，便于与流式处理衔接
- String 与 Files 增加了实用的便捷方法，减少样板代码
- java.time 系列的格式化与解析能力持续完善

**示例**

```java
var client = HttpClient.newHttpClient();
var req = HttpRequest.newBuilder(URI.create("http://localhost:8094/health")).build();
var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
System.out.println(resp.statusCode());
```

**易错点**：HttpClient 默认不设置超时，生产环境必须显式配置连接与请求超时。

### 3.3 升级迁移注意事项

**学习目标**：掌握从旧版本升级到 Java 21 的风险点。

**核心知识点**

- 依赖库需支持目标 JDK 版本，升级前应逐项确认兼容性
- 模块化相关限制在新版本中更严格，反射访问内部 API 可能失败
- 废弃 API 会在新版本中移除，编译期即可发现，部分行为变更只能在运行时暴露
- 升级应配合完整的回归测试，并保留可快速回滚的部署能力

**示例**

升级步骤：确认依赖兼容 → 编译通过 → 跑全量测试 → 灰度发布 → 观察指标

**易错点**：只确认应用代码能编译通过就上线，忽略了运行期行为变更与依赖兼容问题。

---

## 综合练习

### 单项选择题

**1. 关于 record 的特性，下列说法正确的是？**

- A. record 的字段可以在运行时修改
- B. record 自动生成 equals、hashCode 与 toString
- C. record 的访问器必须命名为 getName
- D. record 可以随意添加可变实例字段

**答案**：B

**解析**：record 自动生成构造方法、访问器、equals、hashCode 与 toString，字段隐式 final 不可变，访问器与字段同名。

**对应知识点**：record

**2. sealed 类的主要作用是？**

- A. 提升运行速度
- B. 明确限定允许继承的子类，便于编译器做穷尽检查
- C. 自动序列化
- D. 替代接口

**答案**：B

**解析**：sealed 通过 permits 限定子类范围，使编译期能够检查分支是否穷尽，提升类型安全。

**对应知识点**：密封类

**3. 虚拟线程相比平台线程的核心特点是？**

- A. 由操作系统内核调度
- B. 由 JVM 调度并复用载体线程，阻塞时让出载体线程
- C. 创建成本更高
- D. 只能用于 CPU 密集型任务

**答案**：B

**解析**：虚拟线程由 JVM 调度，多个虚拟线程复用少量载体线程，阻塞时让出载体线程，因此能以同步写法支撑高并发 IO。

**对应知识点**：虚拟线程

**4. 结构化并发的最大价值是？**

- A. 提升单线程性能
- B. 把一组子任务作为整体管理，失败时统一取消，避免线程泄漏
- C. 替代所有线程池
- D. 简化语法

**答案**：B

**解析**：结构化并发让父子任务生命周期绑定，任一子任务失败可统一取消其余任务，异常传播与调试都更清晰。

**对应知识点**：结构化并发

**5. 对 List.of 创建的集合执行 add 操作会？**

- A. 正常添加
- B. 抛出 UnsupportedOperationException
- C. 静默忽略
- D. 返回新集合

**答案**：B

**解析**：List.of 返回不可变集合，任何修改操作都会抛出 UnsupportedOperationException。

**对应知识点**：集合增强

### 判断题

**1. 虚拟线程数量巨大时，滥用 ThreadLocal 可能带来明显的内存压力。**

**答案**：正确

**解析**：正确。ThreadLocal 会为每个线程保存副本，虚拟线程数量可达百万级，容易造成内存膨胀，应改用作用域值等方案。

**对应知识点**：并发实践

**2. 虚拟线程应当像平台线程那样通过线程池进行池化复用。**

**答案**：错误

**解析**：错误。虚拟线程创建成本极低，设计目标就是按需创建与销毁，池化反而违背其设计意图。

**对应知识点**：并发实践

**3. 升级到 Java 21 时，只要应用代码能编译通过即可放心上线。**

**答案**：错误

**解析**：错误。还需确认依赖库兼容性与运行期行为变更，并配合完整的回归测试与回滚方案。

**对应知识点**：升级迁移
