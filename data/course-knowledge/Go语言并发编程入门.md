# Go 语言并发编程入门

> 本文件为「知序学堂」课程知识库资料，供课程 AI 助教检索与问答使用。

## 课程简介

用 goroutine 与 channel 掌握 Go 的并发模型与工程实践。

本课程先快速过一遍 Go 的语法基础与错误处理风格，再深入 goroutine、channel 与 select 构成的并发模型，最后覆盖并发安全、worker pool 等常用模式以及性能分析工具的使用。

---

## 第一章 Go 语言基础

本章打好语法基础，为并发编程做准备。

### 1.1 语法速览与包管理

**学习目标**：掌握基本语法与模块化组织方式。

**核心知识点**

- 变量用 var 或 := 声明，未使用的变量与导入会导致编译失败
- 每个包由若干文件组成，主包通过 func main 作为程序入口
- go mod init 初始化模块，依赖在 go.mod 中记录版本
- go build、go run、go test 是日常最常用的命令

**示例**

```go
package main

import "fmt"

func main() {
    name := "Go"
    fmt.Println("hello", name)
}
```

**易错点**：习惯其他语言后容易保留未使用的变量，而 Go 会把它当作编译错误。

### 1.2 结构体与接口

**学习目标**：掌握 Go 的组合式设计方式。

**核心知识点**

- 结构体通过字段组合描述数据，支持嵌入实现类似继承的效果
- 接口是方法集合，Go 采用隐式实现，无需显式声明 implements
- 方法通过接收者与类型绑定，值接收者与指针接收者行为不同
- 小接口更易组合与实现，是 Go 的设计惯例

**示例**

```go
type Course struct {
    ID   int64
    Name string
}

type Describer interface {
    Describe() string
}

func (c Course) Describe() string { return c.Name }
```

**易错点**：值接收者无法修改原结构体字段，需要修改状态时应使用指针接收者。

### 1.3 错误处理

**学习目标**：掌握 Go 显式返回错误的处理风格。

**核心知识点**

- Go 通过返回 error 值表达错误，调用方必须显式判断
- 用 errors.New 创建简单错误，fmt.Errorf 配合 %w 包装错误
- errors.Is 判断错误链中是否包含目标错误，errors.As 提取具体错误类型
- panic 只用于不可恢复的程序错误，业务错误应通过 error 返回

**示例**

```go
func load(id int64) (*Course, error) {
    c, err := repo.Get(id)
    if err != nil {
        return nil, fmt.Errorf("load course %d: %w", id, err)
    }
    return c, nil
}
```

**易错点**：用 panic 处理业务错误，会导致上层难以恢复，应返回 error 交给调用方决策。

---

## 第二章 并发模型

本章讲清 Go 的并发原语及其组合方式。

### 2.1 goroutine

**学习目标**：理解 goroutine 的轻量特性与生命周期管理。

**核心知识点**

- 用 go 关键字启动 goroutine，由 Go 运行时调度到系统线程上执行
- goroutine 初始栈很小并可动态增长，创建成本远低于系统线程
- 主函数退出时所有 goroutine 会被直接终止，需用 WaitGroup 等待完成
- goroutine 泄漏指数创建的协程永久阻塞无法回收，是常见隐患

**示例**

```go
var wg sync.WaitGroup
for i := 0; i < 5; i++ {
    wg.Add(1)
    go func(n int) {
        defer wg.Done()
        fmt.Println("worker", n)
    }(i)
}
wg.Wait()
```

**易错点**：在循环中直接用闭包引用循环变量且未传参，旧版本下所有 goroutine 会打印同一个值。

### 2.2 channel 与通信

**学习目标**：掌握用 channel 在协程间传递数据。

**核心知识点**

- 无缓冲 channel 的发送与接收必须同时就绪，天然形成同步点
- 有缓冲 channel 在缓冲区未满时发送不阻塞，可用于解耦生产与消费速度
- 关闭 channel 表示不再发送，接收方可用逗号 ok 语法判断是否已关闭
- 不要从接收端关闭 channel，也不要在多个发送方场景下随意关闭

**示例**

```go
ch := make(chan int, 3)
ch <- 1
v, ok := <-ch
fmt.Println(v, ok)   // 1 true
close(ch)
_, ok = <-ch
fmt.Println(ok)      // false
```

**易错点**：向已关闭的 channel 发送数据会 panic，关闭应由唯一的发送方负责。

### 2.3 select 与超时控制

**学习目标**：掌握多路复用与超时的实现方式。

**核心知识点**

- select 同时等待多个 channel 操作，任一就绪即执行对应分支
- 多个分支同时就绪时随机选择，可用于公平分发
- 结合 time.After 实现超时，避免无限等待
- select 中的 default 分支使操作变为非阻塞

**示例**

```go
select {
case v := <-ch:
    fmt.Println("收到", v)
case <-time.After(2 * time.Second):
    fmt.Println("超时")
}
```

**易错点**：在循环中反复使用 time.After 会不断创建定时器，长时间运行可能造成资源累积。

---

## 第三章 并发安全与实践

本章解决共享数据的竞争问题并给出常用并发模式。

### 3.1 sync 包与互斥锁

**学习目标**：掌握保护共享状态的常见手段。

**核心知识点**

- sync.Mutex 提供互斥锁，sync.RWMutex 在读多写少场景下提升并发度
- sync.Once 保证初始化逻辑只执行一次
- sync.Map 适合读多写少且键集合相对稳定的场景，不适合替代所有 map 使用
- 锁的粒度应尽量小，避免在持锁期间做 IO 或远程调用

**示例**

```go
var (mu sync.Mutex; count int)

func inc() {
    mu.Lock()
    defer mu.Unlock()
    count++
}
```

**易错点**：用 -race 之外的手段排查竞态往往徒劳，共享变量的读写必须加锁或用 channel 传递所有权。

### 3.2 常用并发模式

**学习目标**：掌握 worker pool 与流水线等实践模式。

**核心知识点**

- worker pool 用固定数量的协程消费任务队列，控制并发上限与资源占用
- 流水线把处理拆成多个阶段，阶段之间用 channel 连接
- 通过 context 传递取消信号与超时，实现全链路的统一退出
- 并发度应与下游承载能力匹配，盲目提高并发会压垮依赖服务

**示例**

```go
jobs := make(chan Job, 100)
var wg sync.WaitGroup
for i := 0; i < 8; i++ {
    wg.Add(1)
    go func() {
        defer wg.Done()
        for j := range jobs { process(j) }
    }()
}
```

**易错点**：无限制地为每个任务启动 goroutine，下游一旦变慢会迅速堆积并拖垮整个服务。

### 3.3 性能分析与调试

**学习目标**：掌握定位性能与并发问题的工具。

**核心知识点**

- go test -race 启用竞态检测，是最重要的并发排查手段
- pprof 通过 CPU 与内存采样分析热点函数与内存分配
- goroutine 数量持续增长通常意味着协程泄漏，可通过 pprof 的 goroutine 视图定位
- 基准测试用 go test -bench 量化优化效果，避免凭感觉调优

**示例**

```bash
go test -race ./...
go test -bench=. -benchmem
go tool pprof http://localhost:6060/debug/pprof/goroutine
```

**易错点**：没有基准测试就进行性能优化，改动是否有效全靠主观判断。

---

## 综合练习

### 单项选择题

**1. Go 中启动一个 goroutine 使用哪个关键字？**

- A. async
- B. go
- C. thread
- D. spawn

**答案**：B

**解析**：使用 go 关键字即可启动 goroutine，由 Go 运行时负责调度到系统线程执行。

**对应知识点**：goroutine

**2. 无缓冲 channel 的特点是？**

- A. 发送方可以随意发送不阻塞
- B. 发送与接收必须同时就绪，天然形成同步点
- C. 只能发送一次
- D. 不需要关闭

**答案**：B

**解析**：无缓冲 channel 没有缓冲区，发送与接收必须配对就绪，因此本身就是一种同步机制。

**对应知识点**：channel

**3. 向已关闭的 channel 发送数据会发生什么？**

- A. 静默忽略
- B. panic
- C. 返回错误值
- D. 自动重新打开

**答案**：B

**解析**：向已关闭的 channel 发送数据会触发 panic，因此关闭操作应由唯一的发送方负责。

**对应知识点**：channel

**4. 检测 Go 程序中数据竞态最有效的工具是？**

- A. go vet
- B. go test -race
- C. go fmt
- D. go mod tidy

**答案**：B

**解析**：go test -race 启用竞态检测器，能在运行期发现共享变量的非同步读写，是并发排查的核心手段。

**对应知识点**：并发安全

**5. 关于 Go 的错误处理，正确的做法是？**

- A. 用 panic 处理所有业务错误
- B. 通过返回 error 值让调用方显式处理
- C. 忽略错误以简化代码
- D. 只在 main 函数中处理错误

**答案**：B

**解析**：Go 通过返回 error 表达错误并要求调用方判断，panic 只用于不可恢复的程序级错误。

**对应知识点**：错误处理

### 判断题

**1. 主函数退出时，尚未执行完的 goroutine 会被直接终止。**

**答案**：正确

**解析**：正确。main 函数返回后程序立即结束，不会等待其他 goroutine，因此需要用 WaitGroup 等方式等待。

**对应知识点**：goroutine 生命周期

**2. 在循环中反复使用 time.After 实现超时，永远不会带来资源问题。**

**答案**：错误

**解析**：错误。每次 time.After 都会创建新的定时器，在长循环中可能造成资源累积，应改用可复用的 Timer。

**对应知识点**：超时控制

**3. 为每个任务无限制地启动 goroutine 是安全的，不会影响下游服务。**

**答案**：错误

**解析**：错误。无限并发会压垮下游依赖并造成内存堆积，应使用 worker pool 等方式控制并发上限。

**对应知识点**：并发模式
