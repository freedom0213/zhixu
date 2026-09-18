# Spring Boot 快速入门

> 知序学堂演示课程资料（Demo）。本文件同时用于「课程目录」与「课程 AI 助教知识库」。

## 课程简介

本课程带你从零开始掌握 Spring Boot，理解「约定优于配置」的设计思想，
学会用最少的配置快速搭建一个可运行的 Web 应用。
内容涵盖项目创建、自动配置原理、常用注解、RESTful 接口开发、配置文件管理、
数据访问与打包部署。

**适合人群**：具备 Java 基础、了解 Maven 的学习者。

---

## 第一章 Spring Boot 基础

本章目标：理解 Spring Boot 解决了什么问题，掌握它的核心特性与项目结构。

### 1.1 为什么需要 Spring Boot

传统 Spring 开发存在三个痛点：

1. **配置繁琐**：大量 XML 或 Java 配置类，重复且容易出错。
2. **依赖冲突**：需要手动挑选并保证各依赖版本兼容。
3. **部署麻烦**：需要外部 Tomcat，打 war 包部署。

Spring Boot 的解决思路：

- **起步依赖（Starter）**：一个依赖引入一整组相关依赖，版本由父 POM 统一管理。
- **自动配置（AutoConfiguration）**：根据类路径中的 jar 自动完成常用配置。
- **内嵌容器**：内置 Tomcat/Jetty，应用可直接以 jar 形式独立运行。

一句话概括：**约定优于配置**。框架预设了合理默认值，只有需要偏离默认时才写配置。

### 1.2 核心特性

| 特性 | 说明 |
| --- | --- |
| 起步依赖 | spring-boot-starter-web、-data-jpa、-test 等 |
| 自动配置 | 按条件装配 Bean，减少手工配置 |
| 内嵌容器 | 默认 Tomcat，无需外部部署 |
| Actuator | 提供健康检查、指标监控等生产级端点 |
| 外部化配置 | application.yml/properties、环境变量、命令行参数 |
| 无代码生成 | 不使用代码生成，纯注解驱动 |

### 1.3 项目结构

标准 Maven 项目结构：

```
src/main/java          Java 源码
  └─ com.example.demo
      ├─ DemoApplication.java    启动类
      ├─ controller/            控制层
      ├─ service/               业务层
      ├─ mapper/ 或 repository/  数据访问层
      └─ entity/ 或 domain/      实体类
src/main/resources
  ├─ application.yml           主配置文件
  ├─ static/                   静态资源
  └─ templates/                模板文件
src/test/java          测试代码
pom.xml                依赖与构建配置
```

启动类是整个应用的入口，`@SpringBootApplication` 是三个注解的组合：

- `@SpringBootConfiguration`：标识为配置类。
- `@EnableAutoConfiguration`：启用自动配置。
- `@ComponentScan`：扫描当前包及子包下的组件。

**注意**：启动类应放在最外层包，否则组件扫描可能漏掉子包。

---

## 第二章 创建第一个 Spring Boot 应用

本章目标：动手创建并运行一个可访问的 Web 应用，理解自动配置与常用注解。

### 2.1 环境准备

- JDK 8 或更高版本
- Maven 3.6+
- IDE（IDEA 或 VS Code）

推荐通过 Spring Initializr 生成项目骨架，选择：
Spring Web、Lombok 两个起步依赖。

### 2.2 最小可运行应用

启动类：

```java
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

一个 REST 接口：

```java
@RestController
@RequestMapping("/hello")
public class HelloController {

    @GetMapping("/{name}")
    public String hello(@PathVariable String name) {
        return "Hello, " + name;
    }
}
```

启动后访问 `http://localhost:8080/hello/world`，返回 `Hello, world`。

### 2.3 常用注解

**Web 层**

| 注解 | 作用 |
| --- | --- |
| @RestController | @Controller + @ResponseBody，返回值直接序列化为 JSON |
| @RequestMapping | 映射请求路径，可指定 method |
| @GetMapping / @PostMapping | 简化写法，分别对应 GET / POST |
| @PathVariable | 取路径上的变量 |
| @RequestParam | 取查询参数 |
| @RequestBody | 把请求体反序列化为对象 |
| @ResponseBody | 返回值写入响应体 |

**依赖注入**

| 注解 | 作用 |
| --- | --- |
| @Component | 通用组件，交给 Spring 管理 |
| @Service | 业务层组件 |
| @Repository | 数据访问层组件 |
| @Controller | 控制层组件 |
| @Autowired | 按类型注入 |
| @Qualifier | 配合 @Autowired 按名称注入 |
| @Resource | 按名称注入（JDK 注解） |

**构造器注入优于字段注入**：构造器注入可以保证依赖不为 null，
便于单元测试，且能暴露循环依赖问题。

**配置相关**

| 注解 | 作用 |
| --- | --- |
| @Configuration | 声明配置类 |
| @Bean | 方法返回值注册为 Bean |
| @Value | 注入配置文件中的单个值 |
| @ConfigurationProperties | 批量绑定配置到对象 |
| @ConditionalOnXxx | 条件装配，自动配置的核心 |

### 2.4 自动配置原理

自动配置的入口是 `@EnableAutoConfiguration`，它通过 `@Import` 导入
`AutoConfigurationImportSelector`，后者读取类路径下
`META-INF/spring.factories`（Spring Boot 2.7 及以前）
或 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
（Spring Boot 3+）中声明的自动配置类。

每个自动配置类都带有条件注解，只有满足条件才生效，例如：

- `@ConditionalOnClass`：类路径存在指定类时生效。
- `@ConditionalOnMissingBean`：容器中没有该 Bean 时生效。
- `@ConditionalOnProperty`：配置项满足条件时生效。

这解释了「为什么引入了 starter 就自动配置好了」：条件是类路径存在对应 jar。

**自定义配置**：当默认行为不满足需求时，有两种覆盖方式：

1. 在配置文件中修改属性值（优先使用）。
2. 自己声明一个同类型 Bean，`@ConditionalOnMissingBean` 会让默认配置退让。

---

## 第三章 项目实战与常见问题

本章目标：掌握配置管理、数据访问、打包部署，并能排查常见启动错误。

### 3.1 配置文件管理

**格式选择**：推荐 YAML，层级清晰、支持复杂结构。

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo
    username: root
    password: 123456
```

**多环境配置**

按 `application-{profile}.yml` 拆分，主配置文件用 `spring.profiles.active` 激活：

```yaml
# application.yml
spring:
  profiles:
    active: dev
```

**配置读取优先级**（由高到低，常用项）：

1. 命令行参数
2. 操作系统环境变量
3. `application-{profile}.yml`
4. `application.yml`

**敏感信息处理**：不要把生产密码提交到 Git，
应使用环境变量、配置中心或 `spring-boot-configuration-processor` 配合密钥管理。

### 3.2 数据访问入门

**连接池**：Spring Boot 2.x 默认使用 HikariCP，性能优秀。

**MyBatis 集成要点**

1. 引入 `mybatis-spring-boot-starter`。
2. 在配置文件中指定 `mapper-locations` 与实体包路径。
3. Mapper 接口加 `@Mapper`，或在启动类加 `@MapperScan`。

**JPA 集成要点**

- 继承 `JpaRepository` 即可获得常用 CRUD 方法。
- 方法名遵循约定即可自动生成查询，如 `findByNameAndAge`。
- 复杂查询使用 `@Query` 编写 JPQL 或原生 SQL。

### 3.3 打包与部署

**打 jar 包**（推荐）

```bash
mvn clean package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

Spring Boot 的 jar 是「可执行 jar」，内嵌容器与依赖，可直接运行。

**跳过测试打包**

```bash
mvn clean package -DskipTests
```

**打 war 包**：需要外部 Tomcat 时才使用，需继承 `SpringBootServletInitializer`。

**启动参数**：运行时可通过命令行覆盖配置，例如
`java -jar app.jar --server.port=9090`。

### 3.4 常见问题排查

| 现象 | 常见原因 | 解决方向 |
| --- | --- | --- |
| 端口被占用 | 8080 已被其他进程占用 | 改 `server.port` 或结束占用进程 |
| 404 | 路径不匹配、类未被扫描 | 检查注解路径、启动类所在包 |
| 415 | Content-Type 与 @RequestBody 不匹配 | 请求头加 `application/json` |
| 400 参数绑定失败 | 参数名或类型不匹配 | 检查 @RequestParam 名称与类型 |
| 数据源初始化失败 | 数据库未启动或配置错误 | 检查 url、账号密码、驱动依赖 |
| 循环依赖 | 两个 Bean 互相注入 | 重构依赖关系，或用 setter 注入 |
| 配置不生效 | 配置写错层级或被覆盖 | 检查层级、profile 与优先级 |

**调试技巧**：

- 开启 `--debug` 启动参数，可看到自动配置的生效/未生效报告。
- Actuator 的 `/actuator/health` 可快速判断应用健康状态。

### 3.5 本章小结

- 配置文件优先使用 YAML，多环境用 profile 拆分。
- 敏感配置不要入库入 Git。
- 打 jar 包即可独立运行，无需外部容器。
- 排查问题先看启动日志，再确认配置与依赖是否匹配。

---

## 综合练习

### 选择题

**1. `@SpringBootApplication` 不包含以下哪个注解的功能？**
A. @SpringBootConfiguration　B. @EnableAutoConfiguration　C. @ComponentScan　D. @MapperScan
答案：D。前三者是它的组合注解，@MapperScan 需要单独添加。

**2. Spring Boot 实现自动配置的关键机制是？**
A. 反射扫描所有类
B. 条件注解 + META-INF 中的自动配置声明
C. 手写 XML 配置
D. 编译期代码生成
答案：B。

**3. 关于构造器注入相比字段注入的优势，下列说法错误的是？**
A. 能保证依赖不为 null
B. 便于单元测试
C. 能提前暴露循环依赖
D. 可以让代码写得更少，因此只应使用字段注入
答案：D。

**4. 引入 spring-boot-starter-web 后，默认内嵌的容器是？**
A. Jetty　B. Undertow　C. Tomcat　D. Netty
答案：C。

### 判断题

**5. Spring Boot 项目必须以 war 包形式部署到外部 Tomcat 才能运行。**
答案：错误。默认打成可执行 jar，内嵌容器可直接运行。

**6. 只要引入对应 starter，自动配置就一定生效。**
答案：错误。自动配置类带条件注解，需同时满足条件（如类路径存在、无同类 Bean 等）才生效。

### 简答题

**7. 简述 Spring Boot「约定优于配置」的含义。**
参考答案：框架为常见场景预设了合理的默认配置，开发者只需引入对应起步依赖即可获得可用配置；
只有需要偏离默认行为时才显式写配置，从而大幅减少配置量、降低出错概率。

**8. 什么是起步依赖？它解决了什么问题？**
参考答案：起步依赖是把某个场景所需的一组依赖打包为一个依赖项，并统一其版本。
它解决了手动挑选依赖、版本不兼容的问题，让开发者只需声明场景而非逐个声明依赖。

**9. 启动后访问接口返回 404，你会如何排查？**
参考答案：依次检查：请求路径与 @GetMapping 路径是否一致；
Controller 是否在启动类的同级或子包下（否则不被扫描）；
是否被 @RestController/@Controller 注解；
端口与上下文路径是否正确；最后查看启动日志有无异常。

**10. 自定义配置覆盖自动配置有哪两种方式？优先用哪种？**
参考答案：一是通过配置文件修改属性值；二是自行声明同类型 Bean。
优先使用配置文件方式，改动小、无需写代码；只有在默认行为无法通过属性调整时才声明自定义 Bean。
