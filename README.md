# AI 零代码应用生成平台

> 基于 Spring Boot 3 + LangChain4j + Vue 3 的智能应用生成平台 — 用户输入自然语言描述，AI 自动完成智能路由、代码生成、项目构建，一键部署为可访问的 Web 应用。

---

## 一、项目简介

AI 零代码应用生成平台是一款面向非技术用户的智能 Web 应用生成工具。用户只需用自然语言描述需求，系统便会自动：分析需求并智能选择生成策略 → 调用 AI 大模型生成完整代码 → 自动构建 Vue 项目 → 一键部署上线。

**核心亮点：**

| 亮点 | 说明 |
|------|------|
| **AI 智能路由** | 低成本轻量模型自动分析需求复杂度，选择最优生成策略（HTML / 多文件 / Vue3），高成本推理模型专注代码生成，"好钢用在刀刃上" |
| **AI Agent 工具调用** | 为 LangChain4j 开发完整文件系统工具链（读写改删查），AI 自主操作项目文件搭建工程，实现真正的"Agent 编码" |
| **SSE 流式交互 + 工具调用透明化** | 自定义 TokenStream 流处理器，区分 AI 文本/思考/工具调用/工具结果四种事件，用户实时看到 AI 正在读取哪个文件、写入什么内容 |
| **输入/输出护轨（Guardrails）** | 输入护轨防 Prompt 注入 + 输出护轨校验代码质量 + 失败自动重试，构建完整的 AI 安全防护链 |
| **自研 AI 可观测性体系** | ChatModelListener + ThreadLocal + Micrometer + Prometheus + Grafana，自定义 4 类指标，预置 6 模块仪表盘，生产级监控能力 |
| **自研代码混淆引擎（科研成果）** | AST 分析 + GraphCodeBERT 语义相似度 + 注释感知 + 25 种扰动方法，三语言覆盖（Python/C/JS），科研成果工程化落地 |
| **可视化编辑** | iframe + postMessage 沙箱隔离预览，动态注入监听脚本，用户可直接点选页面元素进行可视化修改 |
| **多级优化体系** | Caffeine + Redis 多级缓存 / Redisson 令牌桶限流 / Java 21 虚拟线程 / ChatModel prototype 并发优化 / 时间游标深分页 |

---

## 二、技术架构

### 2.1 架构全景图

```
┌──────────────────────────────────────────────────────────────┐
│                    Vue 3 前端 (Vite 8)                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐ │
│  │ 应用画廊 │ │ AI 对话  │ │ 可视化编辑│ │ 管理后台         │ │
│  │ HomePage │ │AppChatPage│ │ iframe + │ │ Admin Pages      │ │
│  │          │ │ (SSE流式) │ │postMessage│ │                  │ │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘ │
└──────────────────────┬───────────────────────────────────────┘
                       │ HTTP/SSE
┌──────────────────────▼───────────────────────────────────────┐
│              Spring Boot 3 后端 (Java 21)                     │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐    │
│  │                    核心生成流程                         │    │
│  │  AiCodeGenRouter → AiCodeGenFacade → AI Service       │    │
│  │  (智能路由选型)    (门面编排)        (代码生成+工具调用) │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐    │
│  │ LangChain│ │ 输入/输出 │ │ Redisson │ │ Selenium     │    │
│  │ AI Service│ │ 护轨安全  │ │ 分布式限流│ │ 网页截图     │    │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘    │
└──────────────────────┬───────────────────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────────────────┐
│                    基础设施 & 可观测性                          │
│  ┌──────┐ ┌──────┐ ┌──────────┐ ┌────────┐ ┌──────────┐    │
│  │ MySQL│ │ Redis│ │Prometheus│ │Grafana │ │ COS 对象 │    │
│  └──────┘ └──────┘ └──────────┘ └────────┘ └──────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 实际代码生成流程

```
用户输入需求 (Prompt)
        │
        ▼
┌───────────────────┐
│  AiCodeGenRouter  │  AI 分析需求 → 输出 HTML / MULTI_FILE / VUE3
│  (智能路由)        │  低成本模型做分类，按复杂度选策略
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│  AiCodeGenFacade  │  门面编排：生成 → 解析 → 保存，支持取消、版本管理
│  (门面编排)        │  HTML/MultiFile: AI 直接生成代码
│                   │  Vue3: AI 调用文件工具链搭建工程
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│ StreamHandlerExc  │  SSE 流处理器：收集 AI 响应 → 保存对话历史
│  (流处理)         │  区分 AI 文本/思考/工具调用/工具结果四种事件
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│   部署 & 预览      │  Nginx 静态托管 / Vue 项目 npm build
│                   │  Selenium 自动截图生成封面
└───────────────────┘
```

> 关于代码生成流程：用户输入需求后，AI 智能路由自动分析并选择生成策略，门面层统一编排生成→解析→保存全流程，SSE 流处理器实时推送 AI 思考和工具调用过程。

---

## 三、技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **后端框架** | Spring Boot 3.5 + Java 21 | 虚拟线程、响应式编程 |
| **ORM** | MyBatis-Flex | LambdaQueryWrapper 动态查询 + 代码生成器 |
| **AI 框架** | LangChain4j 1.2.0 | AI Service、结构化输出、工具调用、输入/输出护轨 |
| **分布式** | Redisson 3.50.0 | 令牌桶限流 |
| **缓存** | Redis + Caffeine + Spring Cache | 分布式缓存 + 本地缓存多级体系 |
| **会话管理** | Spring Session + Redis | 分布式 Session 持久化 |
| **对象存储** | 腾讯云 COS | 封面图上传与管理 |
| **截图服务** | Selenium + WebDriverManager | 无头浏览器自动化截图 |
| **监控** | Prometheus + Grafana + Actuator | 自定义 AI 指标体系 |
| **前端框架** | Vue 3 + TypeScript + Vite 8 | 组合式 API |
| **UI 组件** | Ant Design Vue 4 | 企业级 UI 组件库 |
| **状态管理** | Pinia 3 | 全局状态管理 |
| **API 生成** | @umijs/openapi | 从 Swagger 文档自动生成请求代码 |
| **反向代理** | Nginx | 前后端代理 + 静态站点托管 |

---

## 四、核心功能

### 4.1 AI 智能路由

利用 LangChain4j **结构化输出**能力，构建路由大模型，在用户创建应用时自动分析需求复杂度，选择最优生成策略 — 简单页面走低成本 HTML 模式，复杂应用走 Vue 3 工程模式，实现**成本与性能的平衡**。

### 4.2 三种代码生成模式

| 模式 | 适用场景 | 技术特点 |
|------|----------|----------|
| **HTML 单页** | 简单应用 | AI 直接生成完整 HTML，一次解析保存 |
| **多文件项目** | 中等复杂应用 | AI 生成结构化多文件，支持 CSS/JS 分离 |
| **Vue 3 工程** | 复杂应用 | AI 调用文件系统工具链，逐步搭建完整工程，自动 npm 构建 |

### 4.3 AI 工具调用系统

为 LangChain4j 开发了完整的文件系统工具链（读、写、修改、删除、目录列表），通过抽象 `BaseTool` 基类 + `ToolManager` 集中注册管理。Vue 3 模式下 AI 可自主调用这些工具操作项目文件，真正实现"Agent 编码"。

工具调用过程通过自定义 TokenStream 流处理器实时透明化展示 — 用户能看到 AI 正在读取哪个文件、写入什么内容。

### 4.4 SSE 流式交互

- 基于 Reactor 响应式编程构建 SSE 接口，实时推送 AI 生成内容
- 自定义 TokenStream 流处理器，将 AI 思考过程、工具调用步骤透明化展示
- 区分四种事件类型：AI 响应、推理思考、工具请求、工具结果
- 改造全局异常处理器，限流等业务异常以 SSE 事件格式推送，前端 EventSource 可正确解析

### 4.5 可视化编辑

前端通过 **iframe + postMessage** 沙箱隔离预览生成的应用，动态注入监听脚本，用户可直接**点选页面元素**进行可视化编辑修改。

### 4.6 应用部署与预览

- 通过 Nginx `alias` + `try_files` 为每个生成的应用提供独立的静态托管
- Vue 3 工程自动完成 npm 构建后部署
- 生成的应用通过唯一 `deployKey` 提供独立访问 URL
- 支持版本回滚（前端编辑页可选历史版本一键回退）

### 4.7 自研代码混淆引擎（科研成果）

本项目核心创新之一，将课题组在代码混淆领域的研究成果工程化落地：

**技术体系：**

```
Python 源码 → AST 解析（含注释节点）→ 五种注释类型分类（File/Block/Null/Inline/docString）
                ↓
        GraphCodeBERT 语义相似度分析 → 锚点智能合并 → 全局命名映射
                ↓
        多策略混淆：变量重命名 / 常量表达式拆分 / for↔while 互转
                      / 不透明谓词注入 / continue 标志位替换 / 注释乱序
```

**核心技术创新：**

| 创新点 | 说明 |
|--------|------|
| **注释感知混淆** | 独创五种注释类型分类体系（File Comment / Block Comment / Null Comment / Inline Comment / docString），每种注释有独立的作用域计算和混淆策略，注释内容乱序处理 |
| **语义相似度锚点合并** | 基于 GraphCodeBERT 的代码语义向量化，将跨文件的同名变量/函数智能合并为同一混淆锚点，确保多文件项目混淆后一致性 |
| **AST 级别变换** | for→while 循环互转、if 分支不透明谓词注入、continue→标志位替换，变换后代码仍可正确编译运行 |
| **多语言覆盖** | Python（AST 分析 + 注释感知 + 锚定策略）、C（变量重命名 + AES 字符串加密）、JavaScript（Base64 / RC4 加密 + 控制流扁平化 + 死代码注入 + 自我防护） |
| **25 种基线扰动** | 包含死代码注入、代码复制、算术提取、条件提取、if-else 拆分等 25 种 AST 级别扰动方法 |

后端通过 ProcessBuilder 调用混淆脚本执行，前端提供可折叠面板、多语言切换、方案选择、一键复制等交互。该工具无需登录即可使用，体现了科研成果向实际产品的转化能力。

### 4.8 权限与多用户

- 用户注册 / 登录 / 三级角色管理（普通用户 / 管理员 / 超级管理员）
- 基于 Spring AOP + 自定义 `@AuthCheck` 注解的统一权限校验
- 分布式 Session（Spring Session + Redis），支持水平扩展

---

## 五、系统优化与安全

### 5.1 性能优化

| 优化项 | 方案 | 效果 |
|--------|------|------|
| **多级缓存** | Caffeine 本地缓存 + Redis 分布式缓存 + Spring Cache 注解 | AI Service 实例缓存，避免重复加载对话记忆 |
| **虚拟线程** | Java 21 Virtual Thread 执行封面截图等 I/O 任务 | 避免传统线程池资源开销 |
| **深分页优化** | 时间游标分页 + 复合索引 (appId, createTime) | 海量对话历史查询性能提升 |
| **精选查询** | priority 字段 + MySQL 索引 | 首页精选应用快速筛选 |
| **并发 AI 调用** | ChatModel Bean 改为 prototype 作用域 | 解决单例模式下的并发瓶颈 |

### 5.2 安全与稳定性 — AI 护轨体系

LangChain4j 提供了输入/输出护轨（Guardrails）机制，本项目在此基础上构建了完整的 AI 安全防护链：

| 防护层 | 技术方案 | 作用 |
|--------|----------|------|
| **输入护轨** | 自定义 `PromptSafetyInputGuardrail`，审查敏感词和 Prompt 注入模式 | 在 AI 调用前拦截恶意输入，防止越狱攻击 |
| **输出护轨** | 自定义输出校验 Guardrail，验证 AI 响应格式与质量 | 不合法输出自动触发重试，保障生成代码可用性 |
| **容错重试** | 护轨校验失败 → 自动重新调用 AI（最多 3 次） | 透明容错，用户无感知 |
| **接口限流** | Redisson RRateLimiter + Spring AOP 令牌桶限流，按用户/IP 维度 | 防止滥用和资源耗尽 |
| **全局异常处理** | @RestControllerAdvice + SSE 事件流错误推送 | 限流等业务异常以 SSE 格式推送，前端可正确解析 |
| **关联数据清理** | 删除应用时级联清除 COS 封面、代码目录、部署目录、对话历史 | 保障数据一致性

### 5.3 AI 可观测性体系

自研了一套完整的 AI 调用监控体系：

```
MonitorContextHolder (ThreadLocal)   → 携带 userId/appId
        ↓
ChatModelMetricsListener             → LangChain4j 回调：onRequest/onResponse/onError
        ↓
AiModelMetricsCollector              → Micrometer 注册 Prometheus 指标
        ↓
Prometheus + Grafana                 → 采集 + 预置 AI 模型监控看板
```

**自定义指标：**
- `ai_model_requests_total` — 请求计数（started / success / error）
- `ai_model_errors_total` — 错误分类统计
- `ai_model_tokens_total` — Token 消耗（input / output / total）
- `ai_model_response_duration_seconds` — 响应延迟分布

**Grafana 仪表盘**包含 6 大模块：概览统计、请求与性能趋势、Token 分析、模型对比、热门排行、异常分析。

### 5.4 设计模式应用

| 模式 | 应用场景 |
|------|----------|
| **门面模式** | `AiCodeGenFacade` 统一编排 AI 生成 → 解析 → 保存流程 |
| **策略模式 + 模板方法** | `CodeParser` / `CodeSaverTemplate` 抽象，HTML/MultiFile 不同实现 |
| **工厂模式** | `AiCodeGenServiceFactory` / `AiCodeGenRouterFactory` 按应用和类型创建实例 |
| **执行器模式** | `StreamHandlerExecutor` / `CodeParserExe` / `CodeSaverExe` 分发执行 |
| **观察者模式** | `ChatModelMetricsListener` 监听 AI 调用生命周期，驱动可观测性指标采集 |

---

## 六、项目结构

```
code-gen/
├── src/main/java/com/carl/codegen/
│   ├── ai/                    # AI 代码生成核心（AI Service、路由、护轨、工具调用）
│   ├── annotation/            # 自定义注解（@AuthCheck）
│   ├── aop/                   # AOP 切面（权限校验）
│   ├── config/                # 配置类（CORS、Redis、COS、AI模型、缓存等）
│   ├── constant/              # 常量定义
│   ├── controller/            # REST 控制器 + SSE 接口 + 工作流演示接口
│   ├── core/                  # 核心编排层（Facade、Builder、Parser、Saver、Handler）
│   ├── exception/             # 全局异常处理
│   ├── manager/               # 第三方服务管理（COS）
│   ├── mapper/                # MyBatis-Flex 数据访问层
│   ├── model/                 # 数据模型（Entity / DTO / VO / Enum）
│   ├── monitor/               # AI 可观测性（指标采集、监听器）
│   ├── ratelimit/             # 分布式限流（注解 + AOP + Redisson）
│   └── service/               # 业务服务层
├── src/main/resources/
│   ├── prompt/                # AI Prompt 模板
│   └── application.yml        # 主配置文件
├── code-gen-frontend/         # Vue 3 前端
│   └── src/
│       ├── api/               # 自动生成的 API 层
│       ├── assets/            # 静态资源（Logo、头像、背景图）
│       ├── components/        # 通用组件（AppCard、MarkdownRenderer 等）
│       ├── pages/             # 页面（Home、Chat、Admin 等）
│       ├── router/            # 路由 + 导航守卫
│       └── stores/            # Pinia 状态管理
├── obfuscator/                # 代码混淆工具集（Python/C/JS 多语言混淆）
├── image-generator/           # AI 图片生成工具（DashScope wan2.2-t2i-flash）
├── monitoring/                # 监控基础设施（Docker Compose）
│   ├── prometheus/            # Prometheus 配置
│   └── grafana/               # Grafana 预置仪表盘
├── sql/                       # 数据库初始化脚本
└── pom.xml
```

---

## 七、快速启动

### 环境要求

- JDK 21+ / Maven 3.8+
- Node.js 20+ / pnpm
- MySQL 8.0+
- Docker & Docker Compose

### 启动步骤

```bash
# 1. 初始化数据库
mysql -u root -p < sql/create_table.sql

# 2. 启动基础设施（Redis + Prometheus + Grafana）
cd monitoring && docker-compose up -d

# 3. 配置 application-local.yml 中的 API Key

# 4. 启动后端
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 5. 启动前端
cd code-gen-frontend && pnpm install && pnpm dev
```

### 访问入口

| 服务 | 地址 |
|------|------|
| 前端页面 | http://localhost:5173 |
| 后端 API | http://localhost:8080/api |
| Swagger 文档 | http://localhost:8080/api/doc.html |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |

---

## 八、复现项目步骤

### 1. 克隆项目

```bash
git clone git@github.com:Carl-Ash/Engineering-Internship-Comprehensive-Project-.git
cd code-gen
```

### 2. 环境准备

确保本地已安装以下环境：

- JDK 21+
- Maven 3.8+
- Node.js 20+ / pnpm
- MySQL 8.0+
- Docker & Docker Compose

### 3. 初始化数据库

```bash
mysql -u root -p < sql/create_table.sql
```

### 4. 启动基础设施

```bash
cd monitoring && docker-compose up -d
```

启动 Redis、Prometheus、Grafana 容器。

### 5. 配置 API Key

在 `src/main/resources/` 下创建 `application-local.yml`，配置大模型 API Key：

```yaml
langchain4j:
  openai:
    api-key: your-api-key
```

### 6. 启动后端

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 7. 启动前端

```bash
cd code-gen-frontend && pnpm install && pnpm dev
```

### 8. 访问系统

| 服务 | 地址 |
|------|------|
| 前端页面 | http://localhost:5173 |
| 后端 API | http://localhost:8080/api |
| Swagger 文档 | http://localhost:8080/api/doc.html |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
