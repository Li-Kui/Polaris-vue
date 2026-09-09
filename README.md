# 🌌 北辰 (Polaris) · 企业级智能大模型管理、工作流编排与 AI 开放中台

<p align="center">
  <img src="polaris-ui-vue3/src/assets/logo/logo_full.png" alt="Polaris Logo" width="160" height="160">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/LangChain4j-1.17.0-blue.svg" alt="LangChain4j">
  <img src="https://img.shields.io/badge/Vue-3.5.26-4fc08d.svg" alt="Vue 3">
  <img src="https://img.shields.io/badge/Vite-6.4.1-646CFF.svg" alt="Vite">
  <img src="https://img.shields.io/badge/Element%20Plus-2.13.1-409EFF.svg" alt="Element Plus">
  <img src="https://img.shields.io/badge/Vue%20Flow-1.42.5-ff0072.svg" alt="Vue Flow">
  <img src="https://img.shields.io/badge/ECharts-5.6.0-red.svg" alt="ECharts">
  <img src="https://img.shields.io/badge/JDK-17%2B-orange.svg" alt="JDK 17+">
  <img src="https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg" alt="License">
</p>

---

## 🌐 在线演示与入口

- **管理后台体验**：[http://beichen-ai.tech](http://8.163.1.222:9000)
- **默认管理员账号**：`admin` / `admin123` （或 `test` / `123456`）
- **AI 开放中台控制台**：`/platform/login` 中台测试编码\账号\密码 test\test\123456
- **中台开发者文档中心**：`/platform/docs/` （内置交互式接口文档与多语言 SDK 示例）

---

> **“为政以德，譬如北辰，居其所而众星共之。”** ——《论语·为政》
>
> **「北辰」**，即北极星，是夜空中指引方向的璀璨恒星。在通用人工智能（AGI）与大模型爆发的浪潮中，**北辰 (Polaris)** 致力于成为企业级 AI 落地征程中的核心基础设施。它以稳固、成熟的中后台与多租户架构为基石（居其所），让各种大模型、智能体、知识库、外部系统连接器和工作流如众星拱卫般有机结合，赋能开发者和企业快速构建更具深度、更加优雅、生产就绪的智能业务系统。

---

## 🌌 项目简介

**北辰 (Polaris)** 是一个面向企业级场景深度打造的 **全栈人工智能大模型对话、通用工作流编排引擎与多租户 AI 开放中台**。

项目后端依托先进的 Java 生态大模型集成框架 **LangChain4j** 与 **Spring Boot 4.x** 构建，前端基于 **Vue 3 + Vite 6 + Element Plus + Vue Flow** 构建现代化大屏沉浸式工作台。北辰打破了“大模型单轮对话”与“传统企业复杂业务”之间的断层，具备 **“管理后台 + 租户中台 + 开放 API”** 的三位一体全景能力：

1. **企业运营底座（Admin Console）**：完备的 RBAC 权限管理、多模型路由配置、智能体工坊、知识库 RAG、敏感词与内容安全双轨风控、系统监控与代码生成。
2. **企业 AI 开放中台（Platform Console）**：独立的租户工作台、API Key 细粒度授权、Token 级配额与计量、外部数据源（HikariCP 隔离池）及 API 连接器。
3. **标准化开放能力（OpenAPI Engine）**：全兼容 **OpenAI 规范协议**（`/platform/v1/chat/completions`、`/platform/v1/models`），已有 LangChain、LlamaIndex、OpenAI SDK 代码仅需修改 Base URL 和 Key 即可无缝接入。
4. **工业级工作流引擎（Universal Workflow V2）**：具有不可变版本管理、节点原子测试、单调递增租约恢复、人工审批工作流、持久化等待与子流程调用的企业级工作流调度核心。

---

## 🌟 核心智能星象（八大核心特性矩阵）

我们将北辰的核心 AI 能力与传统星宿命名相融合，构筑兼具科技感与文化底蕴的功能矩阵：

### 1. 🌌 天枢星 (Polaris-Pivot) - 多模型热插拔路由
- **全主流提供商原生集成**：原生兼容 **DeepSeek**、**阿里云通义千问 (DashScope)**、**标准 OpenAI** 以及 **本地 Ollama 离线部署模型**。
- **热插拔与细粒度隔离**：支持在后台进行多模型参数（随机温度 Temperature、最大 Token、专属 System Prompt）的可视化配置，即改即生效。
- **部门与租户级隔离/共享**：支持按租户/部门独立配置独享的模型端点与参数，亦可设置全局共享的兜底模型，平衡业务效果与 Token 成本。

### 2. 🧠 天璇星 (Polaris-Thinking) - 深度思维链思考流与极速 SSE
- **流式思考回调拦截**：后端基于 LangChain4j 接口拦截思考流（`onPartialThinking` 回调），前端利用 SSE（Server-Sent Events）实现打字机般流畅的流式推送。
- **芯片呼吸动效与可折叠面板**：前端配备极具科技感的 **CPU 芯片脉冲呼吸指示器**，支持对复杂思考步骤自由折叠与展开，比肩顶级大模型交互体验。

### 3. 📊 天玑星 (Polaris-Analysis) - 启发式智能分析与 ECharts 报告引擎
- **智能时机判定**：采用启发式权重算法，在对话中自动识别大模型输出的“对比分析/数据报告”特征，日常闲聊清爽无扰。
- **动态图表生成与切换**：自动解析 Markdown 数据表格，动态抽离数值（过滤干扰单位），无缝一键渲染生成 **对比柱状图** 与 **趋势折线图**。
- **高质量研报排版与 PDF 导出**：渐变蓝表头、奇偶行自适应排版，并集成前端渲染导出引擎，支持一键将分析报告生成高清 PDF。

### 4. 📎 天权星 (Polaris-Attachment) - 多模态附件解析与分层隔离
- **全格式文档深度抽取**：集成 Apache PDFBox，支持 PDF、TXT、Markdown、Excel 等多格式文件的秒级文本抽离与分块。
- **分字段隔离存储设计**：超长附件内容与用户提问正文分离存储，避免在历史记录渲染时造成对话气泡文字堆积与卡顿。
- **毛玻璃高质感卡片**：用户对话气泡上方以精致的 **半透明磨砂毛玻璃卡片** 展现文件属性与解析状态，支持一键下载。

### 5. 🛡️ 玉衡星 (Polaris-Base & Safety) - 坚如磐石的底座与双轨安全风控
- **成熟中后台底座**：开箱即用的用户、角色、部门、岗位、菜单、字典、参数及全套操作审计。
- **敏感词与内容安全双轨风控**：
  - **本地极速引擎**：基于 `sensitive-word` + 拼音深度识别，自带受控种子库，拦截违规文本。
  - **云端深度审计**：集成阿里云内容安全（Green 2022），保障多租户交互与大模型生成内容的合规合法。
- **启动自愈策略**：数据库增量变更在启动期通过容灾自愈机制平滑补齐，无需繁琐的手工补丁。

### 6. ⚙️ 开阳星 (Polaris-Mizar) - 智能体装配与深色工作台
- **沉浸式双栏大屏工作台**：告别局促弹窗，提供完整的智能体装配空间，温度参数与高级策略横向伸展。
- **程序员 Dark-Mode 编辑器**：系统提示词（System Prompt）输入框升级为高对比度程序员深色代码风格编辑器。
- **声明式工具反射解耦 (`@AiAgentTool`)**：后端首创自定义 `@AiAgentTool` 注解，扩展工具只需类头标注即可自动注册至前端装备板，Controller 零改动。支持网络搜索、文档生成、图片生成、数据查询等工具。

### 7. ⛓️ 摇光星 (Polaris-Workflow) - 企业级通用工作流编排引擎 V2
- **三栏式 Vue Flow 大屏画布**：基于 `@vue-flow/core` 与 `dagre` 自动布局，中面板支持原生拖拽、液体避位动画与流光能量管道线。
- **版本不可变与强一致性**：引入 `schemaVersion`、`revision`、`versionNo`、`contentHash` 分层体系，执行实例与版本定义强绑定，杜绝运行时配置漂移。
- **完整丰富的节点生态**：
  - **AI 智能节点**：LLM 提示词节点、语义分类器 (`llm_classifier`)、智能体节点 (`agent`)、知识库检索 (`rag`)。
  - **逻辑控制节点**：确定性条件分支 (`condition`)、并行分支 (`parallel`)、多分支汇聚 (`ALL` / `ANY` / `N_OF_M`)、循环节点 (`loop`)。
  - **数据与外部连接**：HTTP 接口节点、只读数据库查询节点 (`datasource`)、数据结构转换节点 (`transform`)。
  - **人机协同与高级调度**：人工审批节点 V2 (`approval`，支持单人、会签、或签、多级审批与超时处理)、持久化等待 (`wait`)、产物存储 (`artifact`)、子工作流 (`sub_workflow`，支持多层嵌套与独立试运行)。
- **高可用执行与故障恢复机制**：
  - **执行持久化**：后台 Worker 异步持久化运行，与 SSE 观察通道彻底解耦，支持网络重连与断点续读。
  - **租约与防护**：单调递增 `fencingToken` 租约机制，结合数据库快照（Checkpoint）与事务 Outbox，提供节点级自动重试与状态自愈。

### 8. 👑 紫微星 (Polaris-Platform) - 标准 OpenAI 规范中台与多租户开放平台
- **标准 OpenAI 协议全兼容**：
  - 提供标准的 `/platform/v1/chat/completions` 与 `/platform/v1/models` 接口规范。
  - 无缝兼容官方 OpenAI Python SDK、Node.js SDK、LangChain、LlamaIndex 等外部生态调用。
- **严格多租户数据隔离体系**：租户间模型参数、知识库向量库、API Key、外部数据源连接器、对话历史完全隔离。
- **API Key 细粒度授权与滑动窗口限流**：支持配置调用权限 Scope、速率限制（RPM/TPM），保障业务调用安全。
- **精确 Token 计量与配额治理**：内置 Token 级消耗统计与租户用量扣减，提供多维度消耗大屏看板与配额告警。
- **安全的外部连接器体系**：
  - **数据源连接器**：隔离的 HikariCP 连接池，强制执行只读 SQL 策略与主机白名单。
  - **API 连接器**：内置 SSRF 防御、重定向限制、请求凭据动态混入与加密存储。
- **内置 VitePress 交互式开发文档平台**：前端开箱即用集成文档工程，提供从快速入门、流式对接、错误码到多语言 SDK 的完整指南。

---

## 🧭 系统架构与模块划分

北辰后端采用清晰的 Maven 多模块分层架构，各子系统职责明确：

```mermaid
flowchart TB
    subgraph Client ["🖥️ 前端与外部接入层"]
        WebAdmin["Vue 3 管理后台\n(polaris-ui-vue3)"]
        TenantConsole["Vue 3 租户中台控制台\n(Platform Console)"]
        OpenApiClients["外部第三方系统\n(OpenAI SDK / LangChain / cURL)"]
        DocsSite["VitePress 文档中心\n(polaris-docs)"]
    end

    subgraph Entry ["🚀 网关与启动入口"]
        AdminModule["polaris-admin\n(端口: 8081 / Web 接口 / 鉴权拦截 / 异常处理)"]
    end

    subgraph Business ["💼 业务应用与中台核心"]
        PlatformCore["polaris-platform-core\n(租户管理 / API Key 鉴权 / Token 计量 / 连接器)"]
        AiModule["polaris-ai\n(智能体 / 对话会话 / 知识库 / 工作流执行引擎 V2)"]
        SystemModule["polaris-system\n(用户 / 角色 / 部门 / 菜单 / 字典 / 岗位)"]
    end

    subgraph Engine ["⚙️ 底座引擎与基础设施"]
        AiCore["polaris-ai-core\n(LangChain4j 封装 / Qdrant 向量适配 / 敏感词风控 / PDFBox)"]
        FrameworkModule["polaris-framework\n(Spring Security / Druid 连接池 / 限流防重)"]
        QuartzModule["polaris-quartz\n(定时任务 / 工作流触发调度)"]
        GenModule["polaris-generator\n(代码生成引擎)"]
        CommonModule["polaris-common\n(通用常量 / 工具类 / 基础实体)"]
    end

    Client --> Entry
    Entry --> Business
    Business --> Engine
```

### 模块职责一览表

| 模块名称 | 职责说明 |
| :--- | :--- |
| **`polaris-admin`** | 系统启动入口，集成 Web 接口配置、Spring Security 安全拦截、Swagger/Knife4j 文档及全局异常处理 |
| **`polaris-platform-core`** | **中台核心模块**。提供多租户隔离、API Key 授权与限流、Token 配额与计量审计、数据源与 API 连接器、兼容 OpenAI 开放接口 |
| **`polaris-ai`** | **AI 业务应用模块**。智能体编排、模型配置、知识库管理、通用工作流引擎 V2（编译器、执行引擎、审批状态机、事件流） |
| **`polaris-ai-core`** | **AI 底座核心层**。LangChain4j 驱动集成、Qdrant 向量库适配、本地敏感词与阿里云内容安全、PDFBox 解析 |
| **`polaris-system`** | **系统管理模块**。包含用户、角色、部门、菜单、参数配置、字典及操作日志等基础业务 |
| **`polaris-framework`** | **框架核心配置**。Spring Security 鉴权机制、数据源监控、分布式限流切面、XSS 与防重复提交 |
| **`polaris-common`** | **通用工具库**。基础实体、常量、JSON/加密/反射工具类、自定义注解与全局异常类 |
| **`polaris-quartz`** | **定时任务模块**。基于 Quartz 提供定时作业调度，支持工作流定时触发器 |
| **`polaris-generator`** | **代码生成模块**。支持一键快速生成单表及树表的前后端 CRUD 代码 |
| **`polaris-docs`** | **中台开发者文档**。基于 VitePress 搭建，构建后自动输出至前端工程提供在线技术文档 |
| **`polaris-ui-vue3`** | **现代化前端工程**。基于 Vue 3 + Vite 6 + Element Plus + Vue Flow，双控制台设计，集成图表渲染与流式交互 |

---

## 🛠️ 技术选型全景

### ☕ 后端核心技术栈
| 技术 | 说明 | 版本 |
| :--- | :--- | :--- |
| **Java** | 运行环境开发语言 | 17+ |
| **Spring Boot** | 核心开发框架与容器底座 | 4.0.6 (Spring 6 + Jakarta EE) |
| **LangChain4j** | 大语言模型生态与链路框架 | 1.17.0 |
| **Qdrant Client** | 高性能向量数据库适配 | 1.17.0 |
| **Spring Security** | 系统权限认证与 API Key 鉴权 | 适配版 |
| **MyBatis / Druid** | 数据持久层与数据库连接池 | 4.0.1 / 1.2.28 |
| **HikariCP** | 外部工作流数据源隔离连接池 | Spring Boot 内置 |
| **Quartz** | 分布式定时任务调度 | 2.3.2 |
| **Sensitive-Word** | 本地高性能敏感词与拼音匹配过滤 | 0.29.5 / 0.4.0 |
| **Aliyun Green** | 阿里云内容安全审计开放接口 | 3.3.3 |
| **Apache PDFBox** | PDF 文档结构解析与文本抽离 | 2.0.31 |
| **Fastjson2** | 现代化 JSON 解析引擎 | 2.0.62 |

### 🎨 前端核心技术栈
| 技术 | 说明 | 版本 |
| :--- | :--- | :--- |
| **Vue.js** | 渐进式前端框架 | 3.5.26 |
| **Vite** | 极速前端构建工具 | 6.4.1 |
| **Element Plus** | 现代桌面端 UI 组件库 | 2.13.1 |
| **Pinia** | 新一代 Vue 状态管理库 | 3.0.4 |
| **Vue Router** | 官方路由管理库 | 4.6.4 |
| **@vue-flow/core** | 专业级节点与工作流可视化画布 | 1.42.5 |
| **Dagre** | 复杂有向图自动排版算法引擎 | 0.8.5 |
| **Apache ECharts** | 数据可视化图表库 | 5.6.0 |
| **VitePress** | 极简现代化静态文档站点生成器 | 1.6.4 |
| **html2pdf.js** | 浏览器端研报渲染与 PDF 导出 | 0.14.0 |
| **Sass** | CSS 预处理器 | 1.97.2 (Embedded) |

---

## ⚙️ 快速启动指南

### 1. 环境准备
在开始运行项目前，请确保您的本地开发环境满足以下要求：
- **JDK**：17 或更高版本
- **Maven**：3.8+
- **Node.js**：18.x 或 20.x（建议配合 npm 9+ 或 pnpm）
- **MySQL**：8.0+
- **Redis**：6.x 或更高版本（可选，支持会话缓存与限流）

### 2. 数据库初始化
在 MySQL 中创建数据库（推荐 utf8mb4 字符集），并依次执行项目根目录下 `sql/` 目录中的脚本：

```bash
# 1. 初始化底座系统表结构与数据（用户/菜单/角色等）
mysql -u root -p polaris < sql/ry_20260417.sql

# 2. 初始化 AI 大模型、智能体与知识库表
mysql -u root -p polaris < sql/ry_ai.sql

# 3. 初始化 AI 中台、租户、连接器与 API Key 表
mysql -u root -p polaris < sql/ry_platform.sql

# 4. 初始化企业通用工作流引擎 V2 相关表
mysql -u root -p polaris < sql/ai_workflow.sql

# 5. 初始化 Quartz 定时任务调度表
mysql -u root -p polaris < sql/quartz.sql
```

> [!NOTE]
> 系统在启动时具备自愈机制，会自动对消息历史等相关表结构进行兼容性增量检查，确保无缝升级。

### 3. 启动后端服务
1. 打开 `polaris-admin/src/main/resources/application-druid.yml`，修改您的 MySQL 数据库连接用户名与密码。
2. 在项目根目录执行 Maven 编译并启动主工程：

```bash
# 根目录下安装与打包依赖
mvn clean install -DskipTests

# 启动 polaris-admin 服务（端口默认为 8081）
cd polaris-admin
mvn spring-boot:run
```

或直接在 IDEA / Eclipse 中运行 `com.polaris.PolarisApplication` 主启动类。

### 4. 启动前端服务
前端工程集成了 VitePress 开发文档自动化构建流程：

```bash
# 进入前端工程目录
cd polaris-ui-vue3

# 安装依赖
npm install

# 启动本地开发服务（会自动构建最新平台文档并启动 Vite）
npm run dev
```

启动完成后，控制台将输出前端访问地址（默认：`http://localhost:80`）。

---

## 🔑 访问入口与使用说明

| 入口类型 | 访问路径 | 默认凭据 | 功能说明 |
| :--- | :--- | :--- | :--- |
| **管理运营后台** | `http://localhost/` | `admin` / `admin123` | 系统管理、全局模型配置、全局智能体、全流程工作流调度管理、安全风控日志 |
| **AI 开放中台** | `http://localhost/platform/login` | 租户管理员账号 | 租户控制台仪表盘、专属 API Key 管理、外部数据源连接池、租户工作流、Token 用量分析 |
| **中台开发者文档** | `http://localhost/platform/docs/` | 免登录直接访问 | 在线 API 参考、SDK 接入示例（Python/JS/Java/cURL）、SSE 流式调试说明 |

---

## 🚀 开放接口接入示例 (OpenAI 协议兼容)

北辰开放平台原生兼容 OpenAI 接口规范，开发者现有的程序无需修改代码架构，仅需替换请求端点与密钥：

### Python 接入 (OpenAI SDK)

```python
from openai import OpenAI

client = OpenAI(
    api_key="sk-polaris-xxxxxxxxxxxxxxxx", # 在中台“API 密钥管理”创建的 Key
    base_url="http://localhost:8081/platform/v1" # 北辰开放平台端点
)

response = client.chat.completions.create(
    model="deepseek-chat",
    messages=[
        {"role": "system", "content": "你是由北辰AI赋能的企业级助手。"},
        {"role": "user", "content": "请简要分析当下工业大模型的演进趋势。"}
    ],
    stream=True
)

for chunk in response:
    if chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end="", flush=True)
```

### cURL 命令行流式调用

```bash
curl -X POST http://localhost:8081/platform/v1/chat/completions \
  -H "Authorization: Bearer sk-polaris-xxxxxxxxxxxxxxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "你好，请自我介绍"}],
    "stream": true
  }'
```

---

## 📜 开源协议

本项目遵循 [Apache 2.0 开源协议](LICENSE)。允许自由用于商业及个人开发，二次开发及分发请保留原作者及版权声明。
