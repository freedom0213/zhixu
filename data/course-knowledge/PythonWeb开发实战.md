# Python Web 开发实战

> 本文件为「知序学堂」课程知识库资料，供课程 AI 助教检索与问答使用。

## 课程简介

用 Python 从零构建可用的 Web 应用与 RESTful 接口。

本课程从 HTTP 与 Web 工作原理讲起，对比 Flask、Django、FastAPI 的适用场景；随后讲解路由、模板渲染与数据库 ORM，最后覆盖 RESTful 接口设计、认证校验与部署优化。

---

## 第一章 Web 基础与框架选型

本章建立 Web 开发的基本模型并确定技术选型。

### 1.1 HTTP 与 Web 工作原理

**学习目标**：理解请求响应模型与一次页面访问的完整流程。

**核心知识点**

- 浏览器发起 HTTP 请求，服务端返回响应，无状态是 HTTP 的基本特征
- 无状态通过 Cookie 与 Session 或 Token 来维持登录状态
- 常用方法 GET 用于查询、POST 用于提交、PUT 与 DELETE 用于更新删除
- 状态码 2xx 成功、3xx 重定向、4xx 客户端错误、5xx 服务端错误

**示例**

```http
GET /api/courses/1003 HTTP/1.1
Host: example.com
Authorization: Bearer <token>
```

**易错点**：把敏感参数放在 GET 的 URL 中，会被日志与浏览器历史记录留存。

### 1.2 框架选型

**学习目标**：能根据项目特征选择 Web 框架。

**核心知识点**

- Flask 轻量灵活，适合小型服务、原型与需要自由组装的场景
- Django 自带 ORM、Admin 与完整生态，适合内容型与后台管理类项目
- FastAPI 基于类型注解自动生成接口文档，异步性能好，适合 API 服务
- 选型应综合考虑团队熟悉度、生态成熟度与性能要求

**示例**

内部管理后台 → Django（自带 Admin 省时）
对外 API 服务 → FastAPI（文档与异步优势）
小型工具服务 → Flask（简单直接）

**易错点**：为了用 Django 的 Admin 而选择 Django，却不需要其重量级约定，反而增加学习成本。

### 1.3 第一个 Web 应用

**学习目标**：能跑起一个最小可用的接口服务。

**核心知识点**

- 最小应用包括创建应用实例、定义路由处理函数、启动开发服务器
- 开发服务器只用于调试，生产环境必须使用正式的 WSGI 或 ASGI 服务器
- 路由把 URL 模式映射到视图函数，支持路径参数提取
- 建议使用虚拟环境隔离依赖，并固定依赖版本

**示例**

```python
from flask import Flask
app = Flask(__name__)

@app.route('/api/hello')
def hello():
    return {'msg': 'hello'}

# 运行：flask --app app run
```

**易错点**：直接把开发服务器部署到生产，性能和稳定性都无法满足要求。

---

## 第二章 路由、模板与数据

本章解决请求处理、页面渲染与数据持久化。

### 2.1 路由与请求处理

**学习目标**：掌握路径参数、查询参数与请求体的获取方式。

**核心知识点**

- 路径参数用于定位资源，如 /courses/<int:id>
- 查询参数用于过滤与分页，如 /courses?page=1&size=10
- 请求体用于提交结构化数据，通常为 JSON 或表单
- 不同方法可绑定到同一路径，通过 methods 参数区分

**示例**

```python
@app.route('/api/courses/<int:cid>', methods=['GET'])
def get_course(cid):
    page = request.args.get('page', 1, type=int)
    return {'id': cid, 'page': page}
```

**易错点**：路径参数未做类型校验，传入非数字时会导致转换异常并返回 500。

### 2.2 模板渲染与静态资源

**学习目标**：掌握服务端渲染与静态文件管理。

**核心知识点**

- 服务端模板把数据渲染成 HTML 后返回，利于首屏速度与 SEO
- 前后端分离时后端只返回 JSON，页面渲染交由前端框架完成
- 模板中要使用自动转义防止 XSS，避免直接用原始 HTML 拼接用户输入
- 静态资源应设置缓存头并交由 CDN 分发

**示例**

```python
@app.route('/course/<int:cid>')
def course_page(cid):
    return render_template('course.html', course=load(cid))
```

**易错点**：使用「原始 HTML」过滤器直接渲染用户输入，会导致 XSS 漏洞。

### 2.3 数据库与 ORM

**学习目标**：掌握用 ORM 完成增删改查与事务控制。

**核心知识点**

- ORM 把表映射为类、行映射为对象，减少手写 SQL
- 模型类定义字段与约束，迁移工具负责同步表结构
- 事务用于保证多条写入的原子性，异常时应回滚
- ORM 生成的 SQL 可能出现 N+1 查询问题，需通过预加载优化

**示例**

```python
class Course(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(100), nullable=False)

db.session.add(Course(name='Java 集合'))
db.session.commit()
```

**易错点**：循环中逐条查询关联对象会产生 N+1 查询，数据量增大后接口响应急剧变慢。

---

## 第三章 接口开发与部署

本章把服务打磨成可对外提供的产品。

### 3.1 RESTful 接口设计

**学习目标**：掌握资源化 URL 设计与统一响应结构。

**核心知识点**

- URL 用名词表示资源，动作用 HTTP 方法表达，避免出现 getCourseById 这类动词路径
- 统一响应结构包含 code、message、data 三部分，便于前端统一处理
- 列表接口应支持分页、排序与过滤参数
- 接口应保持幂等性设计，GET、PUT、DELETE 多次执行结果一致

**示例**

GET    /api/courses          列表
GET    /api/courses/1003     详情
POST   /api/courses          新增
PUT    /api/courses/1003     更新
DELETE /api/courses/1003     删除

**易错点**：把所有操作都写成 POST 并用 URL 表达动作，会让接口语义混乱、难以维护。

### 3.2 认证与参数校验

**学习目标**：掌握身份认证与请求校验的落地方式。

**核心知识点**

- 认证常用 Token 方案（JWT），服务端无需保存会话状态
- 授权决定能访问哪些资源，需在业务层做资源归属校验
- 参数校验应在入口统一完成，FastAPI 可基于类型注解自动校验
- 错误响应要给出明确的错误码与字段级提示，便于前端定位

**示例**

```python
from pydantic import BaseModel, Field

class CourseCreate(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    price: int = Field(ge=0)
```

**易错点**：只校验登录状态不校验资源归属，会导致用户能通过改 ID 访问他人数据。

### 3.3 部署与性能优化

**学习目标**：掌握上线部署与常见性能手段。

**核心知识点**

- 生产环境用 Gunicorn 或 Uvicorn 多进程运行，前置 Nginx 处理静态资源与反向代理
- 数据库连接应使用连接池，避免每次请求新建连接
- 耗时操作应异步化或放入任务队列，避免阻塞请求线程
- 通过日志与监控指标观察响应时间与错误率，定位性能瓶颈

**示例**

```bash
gunicorn -w 4 -k uvicorn.workers.UvicornWorker app:app -b 0.0.0.0:8000
```

**易错点**：工作进程数远超 CPU 核数，会因上下文切换频繁而降低整体吞吐。

---

## 综合练习

### 单项选择题

**1. HTTP 的基本特征是无状态，维持登录状态通常依靠？**

- A. Cookie 与 Session 或 Token
- B. URL 长度
- C. 状态码
- D. 请求方法

**答案**：A

**解析**：HTTP 本身无状态，需借助 Cookie 与 Session 或 Token 在多次请求间维持登录状态。

**对应知识点**：HTTP 基础

**2. 需要自动生成接口文档且异步性能较好，适合选择哪个框架？**

- A. Flask
- B. Django
- C. FastAPI
- D. Tornado

**答案**：C

**解析**：FastAPI 基于类型注解自动生成 OpenAPI 文档，并原生支持异步，适合 API 服务。

**对应知识点**：框架选型

**3. RESTful 接口设计中，获取课程详情的合理写法是？**

- A. POST /api/getCourseById
- B. GET /api/courses/1003
- C. GET /api/getCourse?id=1003
- D. POST /api/course/detail

**答案**：B

**解析**：RESTful 用名词表示资源、用方法表达动作，GET /api/courses/1003 语义清晰且符合规范。

**对应知识点**：接口设计

**4. ORM 使用中最容易被忽视的性能问题是？**

- A. SQL 语法错误
- B. N+1 查询
- C. 事务过长
- D. 字段类型错误

**答案**：B

**解析**：循环中逐个查询关联对象会形成 N+1 查询，数据量上升后接口响应会急剧变慢。

**对应知识点**：ORM 优化

**5. 生产环境部署 Python Web 应用时，正确的做法是？**

- A. 直接使用框架自带的开发服务器
- B. 使用 Gunicorn 或 Uvicorn 多进程运行并前置 Nginx
- C. 每个请求新建一个进程
- D. 关闭日志以提升性能

**答案**：B

**解析**：开发服务器仅用于调试，生产应使用 WSGI 或 ASGI 服务器多进程运行，并由 Nginx 处理静态资源与反向代理。

**对应知识点**：部署

### 判断题

**1. 只校验用户是否登录，不校验资源归属，就能防止越权访问他人数据。**

**答案**：错误

**解析**：错误。还需在业务层校验资源归属，否则用户可以通过修改 ID 访问他人数据。

**对应知识点**：权限校验

**2. 模板渲染时直接输出用户输入的原始 HTML，是安全且常见的做法。**

**答案**：错误

**解析**：错误。直接渲染未转义的用户输入会导致 XSS 漏洞，应使用自动转义并避免原始 HTML 输出。

**对应知识点**：模板安全

**3. 把耗时操作放入任务队列异步执行，可以避免阻塞 Web 请求线程。**

**答案**：正确

**解析**：正确。异步化或队列化能把长耗时任务移出请求处理路径，显著提升接口响应速度与并发能力。

**对应知识点**：性能优化
