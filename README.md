# 🌌 北辰 (Polaris) · 智能大模型对话与数据分析管理平台

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x%20%2F%204.x-brightgreen.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/LangChain4j-1.17.0-blue.svg" alt="LangChain4j">
  <img src="https://img.shields.io/badge/Vue-2.6.12-4fc08d.svg" alt="Vue">
  <img src="https://img.shields.io/badge/Element%20UI-2.15.14-409EFF.svg" alt="Element UI">
  <img src="https://img.shields.io/badge/ECharts-5.4.0-red.svg" alt="ECharts">
  <img src="https://img.shields.io/badge/JDK-17%2B-orange.svg" alt="JDK">
  <img src="https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg" alt="License">
</p>

---

> **“为政以德，譬如北辰，居其所而众星共之。”** ——《论语·为政》
>
> **「北辰」**，即北极星，是夜空中指引方向的璀璨恒星。在通用人工智能（AGI）与大模型爆发的浪潮中，**北辰 (Polaris)** 致力于成为企业级 AI 落地征程中的指路明灯。它以稳固、成熟的中后台管理为基石（居其所），让各种大模型、智能体、知识库和业务数据如众星拱卫般有机结合，赋能开发者和企业快速构建更具深度、更加优雅的智能业务系统。

---

## 🌌 项目简介

**北辰 (Polaris)** 是一个全新设计的高端人工智能对话、数据分析与报告可视化管理平台。项目后端依托先进的 Java 大模型集成框架 **LangChain4j** 构建，前端集成流式渲染与动态数据可视化引擎，打破了“大模型对话”与“传统企业业务”之间的壁垒，打造真正融入日常办公与数据协作的智能化生产力工具。

平台不仅提供开箱即用的系统管理功能（用户、角色、部门、菜单、权限、日志控制等），更深度重构了大模型流式响应、知识库管理（RAG 向量检索）、思维链思考过程展示以及智能数据报告分析。

---

## 🌟 核心智能星象（特色功能）

我们将北辰的核心 AI 能力与传统星宿命名相融合，为您呈现兼具科技感与文化底蕴的功能矩阵：

### 1. 🌌 天枢星 (Polaris-Pivot) - 多模型热插拔路由
* **多提供商接入**：原生兼容 **DeepSeek 官方 API**、**阿里云通义千问 (DashScope)**、**标准 OpenAI** 以及 **本地 Ollama 离线部署模型**。
* **热插拔与细粒度隔离**：支持在后台进行多模型参数（随机温度、最大 Token、专属 System Prompt）的可视化配置，即改即生效。
* **部门级共用/独享**：可按部门独立设置独享的模型配置，亦可设置全局共享的兜底模型，轻松满足不同业务部门对模型精度与成本的精细化考量。

### 2. 🧠 天璇星 (Polaris-Thinking) - 深度思维链思考流
* **流式拦截渲染**：后端基于 LangChain4j 最新版接口拦截思考流（`onPartialThinking` 回调），前端利用 SSE（Server-Sent Events）实现打字机般流畅的思考过程流式推送。
* **芯片呼吸动效**：前端特设科技感十足的 **CPU 芯片脉冲呼吸指示器**，搭配大方的折叠面板，点击可自由折叠与展开思考步骤，全面比肩主流大模型官网体验。

### 3. 📊 天玑星 (Polaris-Analysis) - 智能分析与图表生成
* **智能时机判定**：采用启发式权重算法，在聊天中智能识别大模型输出的“对比分析/数据报告”特征，日常闲聊、普通逻辑题清爽无打扰。
* **ECharts 可视化对比图表**：自动解析报告中的 Markdown 数据表格，动态抽离数值（自动过滤“元/kg/%”等干扰单位），一键渲染生成精美的 **对比柱状图** 与 **趋势折线图** 并支持实时无缝切换。
* **高质感排版设计**：重构传统的数据网格表，升级为带有 **渐变蓝色表头**、奇偶行自适应灰白交替、轻盈圆角投影的现代化报告排版。

### 4. 📎 天权星 (Polaris-Attachment) - 附件关联与免导解析
* **附件免导升级**：后端利用 Spring 容灾自愈机制，在项目启动时自动检查并安全增量修改消息表结构，自动补齐附件字段。
* **分字段存储设计**：上传的超长附件内容（如 Excel 几万字单元格明细）经解析后与用户提问正文实行分离存储，避免在历史记录加载时在用户对话气泡中堆积大块文字。
* **半透明磨砂卡片**：用户蓝色对话气泡内部展现干净提问，正上方以精致的 **半透明毛玻璃高质感卡片** 展现文件信息与“*已成功关联此对话解析*”标识，并支持一键下载。

### 🛡️ 5. 玉衡星 (Polaris-Base) - 坚如磐石的系统底座
* **经典的后台系统底座**：集成了用户管理、角色管理、部门管理、菜单管理、字典管理等全套功能，具备完善的前后端权限控制和代码生成能力。
* **开发自愈与平滑升级**：针对数据库结构调整，采用启动期校验自愈策略，无需开发手动执行复杂的 SQL 补丁，确保框架升级与更新时的顺畅过渡。

---

## 🧭 系统架构与模块说明

项目后端采用 Maven 多模块架构，结构层次分明，易于扩展和维护：

| 模块名称 | 对应包路径 | 职责说明 |
| :--- | :--- | :--- |
| **`polaris-admin`** | `com.polaris.web` | 系统启动入口，提供 Web 接口、安全配置及全局异常处理 |
| **`polaris-ai`** | `com.polaris.ai` | 大模型 AI 核心模块。整合 LangChain4j、大模型对接、思维链拦截及数据解析 |
| **`polaris-system`** | `com.polaris.system` | 核心系统业务模块。包含用户、角色、部门、菜单、参数配置等逻辑 |
| **`polaris-framework`** | `com.polaris.framework` | 框架核心配置。如 Spring Security 鉴权、数据源、限流及防重复提交等 |
| **`polaris-common`** | `com.polaris.common` | 通用工具类、异常类、实体基类及常量定义 |
| **`polaris-quartz`** | `com.polaris.quartz` | 定时任务调度管理模块 |
| **`polaris-generator`** | `com.polaris.generator` | 代码生成工具模块，支持一键生成前后端 CRUD 代码 |
| **`polaris-ui`** | *(前端工程)* | 基于 Vue 2 + Element UI 的前端工程，集成大模型对话、ECharts 报告展示等 |

### 📂 `polaris-ai` 模块内部包结构设计

为了让项目职责更内聚，并且与上述核心智能星宿在物理层面对齐，`polaris-ai` 模块内部采用了结构分明的子包规划：

```text
com.polaris.ai
  ├── pivot/                 # 🌌 天枢星 (多模型热插拔路由) - 动态模型工厂与热路由代理配置
  ├── chat/                  # 🧠 天璇星 (思考流) / 📊 天玑星 (智能分析) 主逻辑 - 核心流式对话服务与控制器
  ├── attachment/            # 📎 天权星 (附件关联与免导解析) - PDF、Word、Excel 高性能文本提取器
  ├── rag/                   # 📚 知识库与 RAG 核心 - 负责文档拆分、向量持久化、相关度检索关联器
  ├── tools/                 # 🛠️ AI 插件与工具 - 整合联网搜索与系统工具
  ├── domain/                # (保持不变) 数据实体定义 - 统一存放 AI Message、ModelConfig 等模型对象
  └── mapper/                # (保持不变) MyBatis Mapper 接口定义 - 系统底层数据库持久层契约
```

---

## 🛠️ 技术选型

### ☕ 后端核心技术栈
| 技术 | 说明 | 版本 |
| :--- | :--- | :--- |
| **Spring Boot** | 容器及核心基础设施 | 4.x / 2.x 兼容 |
| **LangChain4j** | 大语言模型集成开发框架 | 1.17.0 |
| **Spring Security** | 系统权限与会话安全控制 | 适配版 |
| **MyBatis / Druid** | 数据库连接与 ORM 持久层 | 4.0.1 / 1.2.28 |
| **Apache PDFBox** | PDF 文档解析与文本抽离 | 2.0.31 |

### 🎨 前端核心技术栈
| 技术 | 说明 | 版本 |
| :--- | :--- | :--- |
| **Vue.js** | 前端渐进式框架 | 2.6.12 |
| **Element UI** | 桌面端组件库 | 2.15.14 |
| **Apache ECharts** | 数据可视化图表库 | 5.4.0 |
| **SSE / Web 通信** | SSE (Server-Sent Events) 流式推送 | 原生支持 |
| **Markdown Parser** | 高性能 HTML/Markdown 解析渲染引擎 | 自研/集成 |

---

## ⚙️ 快速启动指南

### 1. 数据库准备
1. 运行根目录下 `sql/` 目录中的 SQL 脚本（以初始化基础表结构与 AI 相关数据表）。
2. 后端服务启动时，会自动对数据库中的 AI 消息表（如 `ai_message`）进行增量字段校验（如缺少 `file_url`, `file_name`, `file_content` 字段将自动 ALTER 补齐），无需手动干预。

### 2. 启动后端服务
请确保本地已配置好 JDK 17 和 Maven 环境，并已在 `application-druid.yml` 中配置好数据库连接。

```bash
# 1. 编译并验证 AI 模块
mvn clean compile -pl polaris-ai

# 2. 启动 polaris-admin 主程序 (运行主启动类，位于 polaris-admin 模块下)
# 默认服务端口为 8080
```

### 3. 启动前端服务
```bash
# 1. 进入前端模块目录
cd polaris-ui

# 2. 安装依赖项
npm install

# 3. 运行本地开发服务
npm run dev
```
打开浏览器访问本地端口，使用系统管理员账户登录后台，点击左侧 **“AI对话”**，在 **“模型管理”** 配置好您的 API 密钥及提供商端点后，即可开启全功能智能体验！

---

## 📜 开源协议

本项目遵循 [Apache 2.0 开源协议](LICENSE)。允许商业使用、修改与分发，但请保留原作者的版权声明。
