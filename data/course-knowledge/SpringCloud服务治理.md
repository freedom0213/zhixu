# Spring Cloud 服务治理

> 本文件为「知序学堂」课程知识库资料，供课程 AI 助教检索与问答使用。

## 课程简介

系统掌握 Spring Cloud 的注册中心、配置中心与服务治理组件。

本课程讲解 Spring Cloud 的组件体系与版本对应关系，深入注册中心与配置中心的选型与落地，讲清服务调用、负载均衡与超时重试，最后覆盖熔断降级、链路追踪与安全鉴权等治理能力。

---

## 第一章 Spring Cloud 概览

本章建立整体组件地图，避免在众多子项目中迷路。

### 1.1 组件体系与版本

**学习目标**：理解 Spring Cloud 与 Spring Boot 的版本对应关系。

**核心知识点**

- Spring Cloud 是一组子项目的集合，每个子项目解决一类分布式问题
- Spring Cloud 版本与 Spring Boot 版本存在严格的对应关系，混用会导致启动失败
- Netflix 系列组件多数已进入维护状态，新项目建议选用 Spring Cloud Alibaba 或其他活跃方案
- spring-cloud-dependencies 通过 BOM 统一管理各子项目版本

**示例**

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>2023.0.1</version>
      <type>pom</type><scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

**易错点**：只升级 Spring Boot 而不调整 Spring Cloud 版本，会出现找不到类或配置项失效的问题。

### 1.2 注册中心选型

**学习目标**：能根据一致性要求选择注册中心。

**核心知识点**

- Eureka 遵循 AP 模型，优先保证可用性，节点间数据最终一致
- Nacos 同时支持 AP 与 CP 模式，可配置临时实例与持久实例，是当前主流选择
- Consul 与 ZooKeeper 更强一致，适合对注册数据一致性要求高的场景
- 注册中心需集群部署并做好健康检查，避免单点

**示例**

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        ephemeral: true   # 临时实例走 AP
```

**易错点**：在服务实例频繁上下线的环境中使用强一致注册中心，会因选主与同步开销带来额外延迟。

### 1.3 配置中心

**学习目标**：掌握多环境配置隔离与动态刷新。

**核心知识点**

- 通过命名空间或分组隔离开发、测试、生产环境的配置
- 配置项按 dataId 组织，通常为「服务名-环境.yaml」的形式
- 动态刷新需配合 @RefreshScope 或在启动配置中开启自动刷新
- 数据库与第三方凭据应加密存储并限制访问权限

**示例**

```java
@RefreshScope
@RestController
public class ConfigController {
    @Value("${feature.enabled:false}")
    private boolean enabled;
}
```

**易错点**：以为改了配置中心的值就会立即生效，未加 @RefreshScope 的 Bean 仍会持有旧值。

---

## 第二章 服务调用与负载均衡

本章解决服务之间如何稳定高效地互相调用。

### 2.1 客户端负载均衡

**学习目标**：理解客户端负载均衡与服务端负载均衡的差别。

**核心知识点**

- 客户端负载均衡由调用方从实例列表中自行选择，减少一次网络跳转
- Spring Cloud LoadBalancer 是当前的默认实现，替代了已进入维护状态的 Ribbon
- 常见策略：轮询、随机、权重、最少连接数
- 可自定义负载均衡策略以支持灰度或按区域就近访问

**示例**

```java
@Bean
ReactorLoadBalancer<ServiceInstance> randomLoadBalancer(
        Environment env, LoadBalancerClientFactory factory) {
    String name = env.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
    return new RandomLoadBalancer(factory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
}
```

**易错点**：在实例性能差异较大的集群中使用纯轮询，会把等量流量分给弱节点，导致响应时间被拖高。

### 2.2 OpenFeign 实战

**学习目标**：掌握 Feign 的常用配置与坑点。

**核心知识点**

- 通过配置指定日志级别可在排查问题时输出请求与响应详情
- Feign 默认不会传递请求头，需要配置拦截器透传鉴权信息
- 文件上传等场景需要引入额外的编码器支持
- 接口定义应与提供方保持一致，字段增删要保证向后兼容

**示例**

```java
@Bean
public RequestInterceptor authInterceptor() {
    return template -> template.header("Authorization",
            currentToken());
}
```

**易错点**：忘记配置请求头拦截器，导致下游收不到鉴权信息而返回 401。

### 2.3 超时重试与灰度

**学习目标**：合理配置超时重试并实现简单的灰度分流。

**核心知识点**

- 连接超时应短（如 1 秒），读超时按业务耗时设定（如 3 秒）
- 重试只适合幂等接口，且需限制次数并加入退避，避免放大流量
- 重试叠加限流不当会引发流量风暴，必须谨慎启用
- 灰度可通过自定义负载均衡策略按请求头或用户标签分流

**示例**

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 1000
            readTimeout: 3000
```

**易错点**：对非幂等的下单接口开启自动重试，可能造成重复下单。

---

## 第三章 治理能力

本章补齐稳定性、可观测性与安全三方面能力。

### 3.1 熔断降级

**学习目标**：掌握熔断器的配置与降级实现。

**核心知识点**

- 熔断器三态：关闭放行、打开拒绝、半开试探恢复
- 触发条件通常基于滑动窗口内的失败率或慢调用比例
- 降级方法必须与业务方法签名一致，用于返回兜底数据
- 降级返回的数据应可识别，避免被当作真实数据使用

**示例**

```java
@CircuitBreaker(name = "courseService", fallbackMethod = "fallback")
public CourseVO getCourse(Long id) { return courseClient.getCourse(id); }

public CourseVO fallback(Long id, Throwable t) {
    return CourseVO.empty(id);
}
```

**易错点**：降级方法签名与被保护方法不一致，会导致 fallback 无法匹配而在运行时直接抛异常。

### 3.2 链路追踪与监控

**学习目标**：建立跨服务的问题定位能力。

**核心知识点**

- Micrometer Tracing 负责生成与传播 TraceId，可对接多种后端存储
- 把 TraceId 写入日志可以按请求维度检索全部日志
- 指标采集关注接口耗时、错误率、线程池与连接池状态
- 健康检查端点可用于容器编排的存活与就绪探针

**示例**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

**易错点**：只采集指标不做告警，问题往往由用户先发现而不是监控先发现。

### 3.3 安全与鉴权

**学习目标**：在微服务中建立统一的认证与授权方案。

**核心知识点**

- 常见做法是网关统一校验令牌，解析出用户身份后写入请求头下传
- 下游服务信任来自内网的头部信息，通过内网隔离防止请求被伪造
- 服务间调用可使用内部令牌或双向 TLS 做身份校验
- 敏感操作应有独立的权限校验，不能仅依赖网关层的登录判断

**示例**

网关校验 JWT 后注入请求头：
X-User-Id: 42
下游直接读取该头部获取当前用户

**易错点**：下游服务直接信任外部传入的用户标识头，攻击者可伪造身份越权访问。

---

## 综合练习

### 单项选择题

**1. Spring Cloud LoadBalancer 属于哪种负载均衡？**

- A. 服务端负载均衡
- B. 客户端负载均衡
- C. DNS 负载均衡
- D. 硬件负载均衡

**答案**：B

**解析**：Spring Cloud LoadBalancer 由调用方自行从实例列表中选择目标实例，属于客户端负载均衡。

**对应知识点**：负载均衡

**2. 关于注册中心的 CAP 取舍，Nacos 的特点是？**

- A. 只支持 CP 模式
- B. 只支持 AP 模式
- C. 同时支持 AP 与 CP，可按实例类型配置
- D. 不支持集群部署

**答案**：C

**解析**：Nacos 支持临时实例走 AP、持久实例走 CP，可按业务对一致性与可用性的要求灵活选择。

**对应知识点**：注册中心选型

**3. 修改配置中心的值后，若希望 Bean 中的配置立即生效，需要添加什么？**

- A. @RefreshScope
- B. @Lazy
- C. @Primary
- D. @DependsOn

**答案**：A

**解析**：@RefreshScope 让 Bean 在配置刷新时被重新创建，从而读到最新配置值。

**对应知识点**：配置中心

**4. Feign 调用下游服务时收不到鉴权信息，最可能的原因是？**

- A. 没有配置请求头拦截器
- B. 没有开启服务注册
- C. 读超时设置过短
- D. 没有使用负载均衡

**答案**：A

**解析**：Feign 默认不传递请求头，需要配置 RequestInterceptor 显式把令牌等信息透传到下游。

**对应知识点**：Feign 实战

**5. 熔断器打开状态下对请求的处理方式是？**

- A. 正常放行
- B. 直接拒绝并走降级
- C. 放行一半请求
- D. 转发到其他服务

**答案**：B

**解析**：打开状态直接拒绝请求并触发降级逻辑，避免继续调用已经故障的下游；半开状态才放行少量请求试探。

**对应知识点**：熔断降级

### 判断题

**1. 自动重试适用于所有接口，包括非幂等的下单接口。**

**答案**：错误

**解析**：错误。非幂等接口重试可能造成重复下单等严重后果，重试只应针对幂等操作并限制次数。

**对应知识点**：超时重试

**2. 把 TraceId 写入日志，可以按单次请求维度检索跨多个服务的全部日志。**

**答案**：正确

**解析**：正确。TraceId 沿调用链透传并写入日志后，可用它把一次请求在不同服务中的日志串联起来。

**对应知识点**：链路追踪

**3. 下游服务可以直接信任外部请求传入的用户标识请求头，以此判断当前用户身份。**

**答案**：错误

**解析**：错误。外部请求头可被伪造，应仅信任网关注入的头部，并通过内网隔离防止绕过网关。

**对应知识点**：安全鉴权
