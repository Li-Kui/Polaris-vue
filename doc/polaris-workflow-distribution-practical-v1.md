# Polaris 工作流分发实用方案 v1.0

> **适用分支**：`dev`
> **目标**：将已发布的工作流通过 API 接口、独立页面、iframe 嵌入三种方式提供给第三方使用
> **设计原则**：基于现有代码做最小增量，不引入 DSL 引擎，不重建执行运行时

---

## 1. 现有基础

### 1.1 已具备的能力

| 能力 | 状态 | 关键代码 |
|------|------|---------|
| API Key 认证 + 限流 | ✅ | `ApiKeyAuthFilter` / `ApiKeyRateLimiter` |
| 按 workflowCode 启动执行 | ✅ | `PlatformWorkflowOpenApiController.start()` |
| 查询执行状态 | ✅ | `GET /platform/api/workflow-executions/{id}` |
| 读取事件列表 | ✅ | `GET /platform/api/workflow-executions/{id}/events` |
| SSE 实时推送 | ✅ | `AiWorkflowController.streamEvents()` |
| 取消执行 | ✅ | `POST .../cancel` |
| 幂等键 | ✅ | `Idempotency-Key` Header |
| Token 配额 | ✅ | `TokenQuotaService` |
| 图片处理工作台 | ✅ | `draw.vue`（1578 行，10 种图片能力） |
| AI 对话页面 | ✅ | `chat.vue` |
| 报告引擎 | ✅ | `PolarisReportEngine.vue` |
| Workflow 组件 | ✅ | 31 个 Vue 组件（输入表单、执行监控、审批等） |
| 不可变 Workflow Version | ✅ | `ai_workflow_version` |
| Resource Binding | ✅ | `ai_workflow_resource_binding` |
| Principal Snapshot | ✅ | `ai_workflow_execution.principal_snapshot` |

### 1.2 缺少的能力

| 缺少项 | 说明 |
|--------|------|
| 工作流级别权限控制 | API Key 不能控制「只能调哪些工作流」 |
| 分享令牌 | 没有面向第三方的页面访问凭据机制 |
| 独立运行页面 | 工作流页面必须登录后台才能使用 |
| iframe 嵌入支持 | 没有 CSP 配置和 postMessage 协议 |
| 页面模板选择 | 不同工作流没有不同的页面展示形态 |

---

## 2. 三种分发形态

### 2.1 API 接口调用

第三方后端使用 API Key 直接调用工作流。

```
第三方后端 → API Key → Polaris API → Workflow V2 → 返回结果
```

现有接口（已可用）：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/platform/api/workflows/{code}/executions` | 启动工作流 |
| GET | `/platform/api/workflow-executions/{id}` | 查询执行 |
| GET | `/platform/api/workflow-executions/{id}/events` | 获取事件 |
| POST | `/platform/api/workflow-executions/{id}/cancel` | 取消执行 |

需要增加：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/platform/api/workflow-executions/{id}/events/stream` | SSE 实时推送（当前仅后台有） |

### 2.2 独立页面

提供一个链接，第三方用户打开即可使用工作流。

```
管理员创建分享 → 生成链接 → 第三方打开 /app/{token} → 独立页面
```

### 2.3 iframe 嵌入

第三方在自己的页面中嵌入 iframe。

```html
<iframe src="https://polaris.example.com/app/{token}?embed=1"
        style="width:100%;height:600px;border:none;"></iframe>
```

---

## 3. 分享令牌

### 3.1 设计

分享令牌（Share Token）是连接管理员配置和第三方访问的核心凭据。

- 管理员在后台为某个工作流创建分享，系统生成一个令牌
- 令牌绑定租户、工作流、页面配置
- 令牌有过期时间，可以随时禁用
- 令牌用于页面访问和页面内的 API 调用，不替代 API Key（服务端直接调用仍用 API Key）

### 3.2 令牌与 API Key 的分工

```
API Key：
  → 面向第三方后端
  → 服务端对服务端
  → 权限粒度可以到工作流级别
  → 长期有效

分享令牌：
  → 面向第三方页面用户
  → 浏览器直接访问
  → 只绑定一个工作流 + 页面配置
  → 有过期时间
```

### 3.3 数据库表

```sql
CREATE TABLE IF NOT EXISTS `platform_workflow_share` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`         BIGINT       NOT NULL COMMENT '租户 ID',
  `share_code`        VARCHAR(64)  NOT NULL COMMENT '分享编码（URL 使用，高熵随机值的 SHA-256）',
  `share_name`        VARCHAR(128) NOT NULL COMMENT '分享名称',
  `workflow_definition_id` BIGINT  NOT NULL COMMENT '工作流定义 ID',
  `workflow_code`     VARCHAR(64)  NOT NULL COMMENT '工作流编码快照',

  -- 页面配置
  `page_type`         VARCHAR(16)  NOT NULL DEFAULT 'form' COMMENT '页面类型: form/chat/report/task/query/image/compare/gallery',
  `page_config_json`  JSON         DEFAULT NULL COMMENT '页面配置 JSON',

  -- 访问控制
  `allowed_origins`   JSON         DEFAULT NULL COMMENT '允许 iframe 嵌入的 Origin 列表',
  `rate_limit`        INT          DEFAULT 60 COMMENT '每分钟请求次数限制',
  `daily_quota`       INT          DEFAULT NULL COMMENT '每日执行次数上限，NULL 不限',
  `status`            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 正常 1 停用）',
  `expire_time`       DATETIME     DEFAULT NULL COMMENT '过期时间，NULL 永不过期',

  -- 审计
  `visit_count`       BIGINT       NOT NULL DEFAULT 0 COMMENT '访问次数',
  `execution_count`   BIGINT       NOT NULL DEFAULT 0 COMMENT '执行次数',
  `last_used_time`    DATETIME     DEFAULT NULL COMMENT '最后使用时间',
  `create_by`         VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_code` (`share_code`),
  KEY `idx_share_tenant` (`tenant_id`, `status`),
  KEY `idx_share_workflow` (`tenant_id`, `workflow_definition_id`),
  KEY `idx_share_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流分享令牌';
```

### 3.4 `page_config_json` 示例

**表单型**：

```json
{
  "title": "文案生成助手",
  "description": "输入产品信息，AI 自动生成营销文案",
  "submitButtonText": "开始生成",
  "showHistory": false,
  "theme": "light"
}
```

**对话型**：

```json
{
  "title": "订单查询助手",
  "welcomeMessage": "你好，我是订单查询助手，有什么可以帮你的？",
  "suggestedQuestions": ["查询最近订单", "申请退款", "修改收货地址"],
  "showHistory": true,
  "theme": "light"
}
```

**前后对比型**：

```json
{
  "title": "虚拟试戴",
  "description": "上传照片，一键试戴珠宝饰品",
  "compareMode": "slider",
  "uploadAccept": "image/jpeg,image/png",
  "maxUploadSize": 10485760,
  "showDownload": true,
  "sourceLabel": "原图",
  "resultLabel": "效果图"
}
```

**图片工作台型**：

```json
{
  "title": "商品图生成",
  "enabledModes": ["text_to_image", "image_to_image", "background_replacement"],
  "sizeOptions": ["512x512", "1024x1024"],
  "maxGenerateCount": 4,
  "showDownload": true
}
```

---

## 4. 八种页面模板

### 4.1 模板总览

| 编号 | pageType | 名称 | 适用场景 | 核心交互 |
|:---:|----------|------|---------|---------|
| 1 | `form` | 表单型 | 文案生成、翻译、摘要 | 填参数 → 运行 → 看结果 |
| 2 | `chat` | 对话型 | 客服助手、订单查询 | 多轮对话 + 流式输出 |
| 3 | `report` | 报告型 | 数据分析报告、周报 | 提交 → Markdown/文件报告 |
| 4 | `task` | 批量任务型 | 批量处理、审批流 | 异步提交 → 任务列表 → 进度追踪 |
| 5 | `query` | 数据查询型 | BI 查询、知识问答 | 输入条件 → 表格/图表展示 |
| 6 | `image` | 图片工作台 | 生图、重绘、编辑 | 上传 + 参数 + 结果网格 |
| 7 | `compare` | 前后对比 | 试戴、换装、风格转换 | 上传原图 → 滑块对比效果 |
| 8 | `gallery` | 画廊型 | 文生图、批量风格 | 输入提示词 → 多图结果展示 |

### 4.2 自动推荐逻辑

创建分享时，系统根据工作流特征自动推荐最合适的页面类型：

```text
判断条件（按优先级从高到低）：

1. 输入 schema 包含 image/file 类型字段 + 输出包含图片
   → 推荐「前后对比」或「图片工作台」

2. 工作流包含 agent 节点
   → 推荐「对话型」

3. 工作流包含 artifact 节点 / 输出 schema 有大文本字段
   → 推荐「报告型」

4. 工作流包含 approval 或 wait 节点
   → 推荐「批量任务型」

5. 工作流包含 database_query 节点
   → 推荐「数据查询型」

6. 其他
   → 推荐「表单型」（默认）
```

管理员可以接受推荐，也可以手动选择其他类型。

### 4.3 各模板的 UI 结构

#### 4.3.1 表单型（form）

```
┌──────────────────────────────────┐
│  [Logo]  标题                     │  ← RuntimeHeader
├──────────────────────────────────┤
│  字段 1：[__________]             │
│  字段 2：[下拉选择 ▼]             │  ← 根据 input schema 自动生成
│  字段 3：[__________]             │     复用 WorkflowExecutionInput
│                                  │
│  [▶ 提交]                        │
├──────────────────────────────────┤
│  ⏳ 执行中... 35%                 │  ← SSE 进度
├──────────────────────────────────┤
│  结果：                           │
│  ┌────────────────────────────┐  │  ← RuntimeResult
│  │ 输出内容（文本/Markdown）   │  │
│  └────────────────────────────┘  │
│  [📋 复制]  [↻ 重新运行]         │
└──────────────────────────────────┘
```

#### 4.3.2 对话型（chat）

```
┌──────────────────────────────────┐
│  [Logo]  标题                     │
├──────────────────────────────────┤
│  🤖 欢迎语...                    │
│  💡 推荐问题: [问题1] [问题2]     │
│                                  │
│        👤 用户消息                │
│  🤖 AI 流式回复...               │  ← SSE 流式渲染
│                                  │
│        👤 用户消息                │
│  🤖 AI 回复...                   │
├──────────────────────────────────┤
│  [请输入消息...            ] [↑]  │
└──────────────────────────────────┘
```

#### 4.3.3 前后对比型（compare）

```
┌──────────────────────────────────────────┐
│  [Logo]  虚拟试戴                         │
├──────────────────────────────────────────┤
│  📎 上传照片            💍 选择/参数      │
│  ┌──────────┐          ┌──────────┐     │
│  │  [人物照] │          │ ○ 选项A   │     │
│  └──────────┘          │ ● 选项B   │     │
│                        └──────────┘     │
│  [▶ 开始处理]                            │
├──────────────────────────────────────────┤
│  ┌─────────────────┬─────────────────┐  │
│  │     原图         │◄─ 滑块 ─►│效果图 │  │  ← ImageCompare
│  └─────────────────┴─────────────────┘  │
│  [📥 下载]  [↻ 重新生成]                  │
└──────────────────────────────────────────┘
```

#### 4.3.4 图片工作台型（image）

```
┌──────────┬──────────────────┬──────────────┐
│ 能力选择  │  参数区           │  结果区       │
│ ○ 文生图  │  📎 源图上传      │  ┌────┐┌────┐│
│ ● 图生图  │  提示词           │  │结果1││结果2││
│ ○ 换背景  │  尺寸/数量        │  └────┘└────┘│
│          │  [开始生成]       │  ┌────┐┌────┐│
│          │                  │  │结果3││结果4││
│          │                  │  └────┘└────┘│
└──────────┴──────────────────┴──────────────┘
```

#### 4.3.5 其他模板

报告型、任务型、查询型、画廊型的 UI 结构类似，参见第 2 节概述，在此不重复绘制。

---

## 5. 后端设计

### 5.1 模块归属

所有新增代码放在 `polaris-platform-core` 中，不新增模块：

```
polaris-platform-core/src/main/java/com/polaris/platform/
├── domain/
│   └── WorkflowShare.java                    ← 实体
├── mapper/
│   └── WorkflowShareMapper.java              ← Mapper
├── service/
│   ├── IWorkflowShareService.java            ← 接口
│   └── impl/WorkflowShareServiceImpl.java    ← 实现
├── controller/
│   ├── PlatformConsoleShareController.java   ← 管理后台 CRUD
│   └── WorkflowShareRuntimeController.java   ← 运行时 API（不需后台登录）
├── auth/
│   └── ShareTokenAuthFilter.java             ← 分享令牌认证过滤器
└── dto/
    ├── WorkflowShareCreateRequest.java
    ├── WorkflowShareManifest.java             ← 返回给页面的配置
    └── WorkflowShareExecutionView.java        ← 脱敏后的执行视图
```

### 5.2 ShareTokenAuthFilter

新增过滤器，拦截 `/platform/runtime/**` 路径：

```
请求进来
  ↓
从 URL path 提取 shareCode
  ↓
查询 platform_workflow_share 表
  ↓
检查：状态正常？未过期？未超限额？
  ↓
构造 ShareCallerContext（tenantId + shareId + workflowDefinitionId）
  ↓
设置到 CallerContextHolder
  ↓
放行到业务 Controller
```

与现有 `ApiKeyAuthFilter` 完全独立，两个 Filter 各管各的路径前缀。

### 5.3 运行时 API

`WorkflowShareRuntimeController` 路径：`/platform/runtime/shares/{shareCode}`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{shareCode}/manifest` | 获取页面配置（名称、页面类型、输入 schema、页面配置） |
| POST | `/{shareCode}/executions` | 启动工作流执行 |
| GET | `/{shareCode}/executions/{executionId}` | 查询执行状态（脱敏） |
| GET | `/{shareCode}/executions/{executionId}/events` | 获取事件列表 |
| GET | `/{shareCode}/executions/{executionId}/events/stream` | SSE 实时推送 |
| POST | `/{shareCode}/executions/{executionId}/cancel` | 取消执行 |
| POST | `/{shareCode}/upload` | 上传文件（图片类模板需要） |

所有接口内部复用现有 `WorkflowExecutionApplicationFacade`，不重新实现执行逻辑。

### 5.4 manifest 响应示例

```json
{
  "shareName": "虚拟试戴",
  "workflowCode": "virtual-tryon",
  "pageType": "compare",
  "inputSchema": {
    "type": "object",
    "properties": {
      "sourceImage": { "type": "string", "format": "uri", "title": "人物照片" },
      "productId": { "type": "string", "title": "商品", "enum": ["ring_01", "necklace_02"] }
    },
    "required": ["sourceImage", "productId"]
  },
  "pageConfig": {
    "title": "虚拟试戴",
    "compareMode": "slider",
    "sourceLabel": "原图",
    "resultLabel": "试戴效果",
    "showDownload": true
  }
}
```

### 5.5 executionView 脱敏

运行时 API 返回的执行视图，相比后台视图去掉内部字段：

| 字段 | 后台视图 | 分享运行时视图 |
|------|---------|--------------|
| executionId | ✅ | ✅ |
| status | ✅ | ✅ |
| outputJson | ✅ | ✅ |
| errorMessage | ✅ | ✅（脱敏） |
| createTime / finishTime | ✅ | ✅ |
| definitionId | ✅ | ❌ 不返回 |
| workflowVersionId | ✅ | ❌ 不返回 |
| budgetJson / usageJson | ✅ | ❌ 不返回 |
| inputJson | ✅ | ❌ 不返回 |
| principalSnapshot | ✅ | ❌ 不返回 |

### 5.6 API Key 增加工作流级权限

在现有 `platform_api_key` 表增加一个字段：

```sql
ALTER TABLE `platform_api_key`
ADD COLUMN `allowed_workflows` JSON DEFAULT NULL
    COMMENT '允许调用的工作流编码列表 JSON，NULL 表示不限';
```

`ApiKeyAuthFilter` 构造 context 时传入此字段，在 `PlatformWorkflowOpenApiController.start()` 中校验。

---

## 6. 前端设计

### 6.1 目录结构

```
polaris-ui-vue3/src/
├── views/workflowApp/
│   ├── RuntimePage.vue                 ← 独立运行页面入口
│   ├── management/
│   │   └── ShareManagement.vue         ← 管理后台-分享管理
│   └── templates/
│       ├── FormTemplate.vue            ← 表单型
│       ├── ChatTemplate.vue            ← 对话型
│       ├── ReportTemplate.vue          ← 报告型
│       ├── TaskTemplate.vue            ← 批量任务型
│       ├── QueryTemplate.vue           ← 数据查询型
│       ├── ImageTemplate.vue           ← 图片工作台
│       ├── CompareTemplate.vue         ← 前后对比
│       └── GalleryTemplate.vue         ← 画廊型
├── components/workflow-app/
│   ├── RuntimeHeader.vue               ← 通用顶栏
│   ├── RuntimeResult.vue               ← 通用结果展示
│   ├── RuntimeProgress.vue             ← 通用进度条
│   └── ImageCompare.vue                ← 图片滑块对比组件
├── api/workflowApp/
│   └── runtime.js                      ← 运行时 API
└── router/index.js                     ← 增加 /app/:shareCode 路由
```

### 6.2 路由配置

```javascript
// 独立运行页面，不使用后台 Layout
{
  path: '/app/:shareCode',
  component: () => import('@/views/workflowApp/RuntimePage.vue'),
  meta: { requiresAuth: false }  // 不需要后台登录
}
```

### 6.3 RuntimePage.vue 核心逻辑

```vue
<template>
  <div class="runtime-page" :class="[`theme-${manifest.pageConfig?.theme || 'light'}`]">
    <RuntimeHeader :title="manifest.pageConfig?.title || manifest.shareName" />

    <component
      :is="templateComponent"
      :manifest="manifest"
      :share-code="shareCode"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getShareManifest } from '@/api/workflowApp/runtime'

const route = useRoute()
const shareCode = route.params.shareCode
const manifest = ref({})

const templateMap = {
  form: () => import('./templates/FormTemplate.vue'),
  chat: () => import('./templates/ChatTemplate.vue'),
  report: () => import('./templates/ReportTemplate.vue'),
  task: () => import('./templates/TaskTemplate.vue'),
  query: () => import('./templates/QueryTemplate.vue'),
  image: () => import('./templates/ImageTemplate.vue'),
  compare: () => import('./templates/CompareTemplate.vue'),
  gallery: () => import('./templates/GalleryTemplate.vue'),
}

const templateComponent = computed(() =>
  templateMap[manifest.value.pageType] || templateMap.form
)

onMounted(async () => {
  manifest.value = await getShareManifest(shareCode)
})
</script>
```

### 6.4 各模板复用的现有组件

| 模板 | 复用 | 需要新写 |
|------|------|---------|
| FormTemplate | `WorkflowExecutionInput` + `WorkflowSchemaConfig` | 结果展示区 + 进度条 |
| ChatTemplate | `chat.vue` 的对话逻辑 | 适配 Workflow SSE 格式 |
| ReportTemplate | `PolarisReportEngine` | Artifact 下载 |
| TaskTemplate | `WorkflowExecutionMonitor` | 简化版卡片 |
| QueryTemplate | `WorkflowExecutionInput`（输入） + el-table | 表格渲染 |
| ImageTemplate | `draw.vue` 的核心逻辑 | 去后台依赖 |
| CompareTemplate | `draw.vue` 的上传组件 | `ImageCompare.vue`（~150 行） |
| GalleryTemplate | `draw.vue` 的结果网格 + `ElImageViewer` | 简化版输入区 |

### 6.5 管理后台-分享管理

入口放在工作流页面中，添加一个「分享」按钮：

```
工作流列表 → 某工作流 → [分享] → 打开分享管理弹窗
```

分享管理弹窗：

```
┌─────────────────────────────────────────────┐
│  分享管理 - 订单查询助手                       │
├─────────────────────────────────────────────┤
│                                             │
│  分享名称：[订单查询助手外部版]               │
│                                             │
│  页面类型：                                  │
│  ┌───────────────────────────────────────┐  │
│  │  💡 推荐：对话型                       │  │
│  │  （检测到 Agent 节点）                  │  │
│  │                                       │  │
│  │  通用                                  │  │
│  │  ● 表单  ○ 对话  ○ 报告  ○ 任务  ○ 查询│  │
│  │  图片                                  │  │
│  │  ○ 工作台  ○ 前后对比  ○ 画廊          │  │
│  └───────────────────────────────────────┘  │
│                                             │
│  页面设置：                                  │
│  ├── 标题：[订单查询助手]                    │
│  ├── 欢迎语：[你好！有什么可以帮你？]         │
│  └── 推荐问题：[查询最近订单] [+]             │
│                                             │
│  访问控制：                                  │
│  ├── 有效期：[不限 ▼] 或 [2025-12-31]       │
│  ├── 每分钟限流：[60]                        │
│  ├── 每日执行上限：[不限 ▼] 或 [1000]        │
│  └── iframe 来源限制：[+添加 Origin]          │
│                                             │
│  [创建分享]                                  │
├─────────────────────────────────────────────┤
│  已有分享：                                  │
│  ┌──────────┬──────┬────────┬────┬──────┐  │
│  │ 名称      │ 类型 │ 状态    │ 访问│ 操作  │ │
│  ├──────────┼──────┼────────┼────┼──────┤  │
│  │ 外部版    │ 对话 │ ✅ 正常 │ 328│ 复制  │  │
│  │          │      │        │    │ 停用  │  │
│  │          │      │        │    │ 删除  │  │
│  └──────────┴──────┴────────┴────┴──────┘  │
│                                             │
│  复制信息：                                  │
│  页面链接：https://xxx/app/tk_abc  [📋]     │
│  iframe：<iframe src="...">       [📋]     │
│  API：POST /platform/api/workflows/...  [📋]│
└─────────────────────────────────────────────┘
```

---

## 7. 安全设计

### 7.1 必须保证

| 项目 | 措施 |
|------|------|
| 令牌存储 | `share_code` 使用高熵随机值，数据库只存 SHA-256 摘要（复用 `ApiKeyDigestUtils`） |
| 租户隔离 | 所有查询都带 `tenant_id` 条件 |
| 输出脱敏 | 运行时 API 不返回 `definitionId`、`budgetJson`、`principalSnapshot` 等内部字段 |
| 错误脱敏 | 不返回 StackTrace、SQL、内部 IP、Connector 端点 |
| iframe 安全 | 配置了 `allowed_origins` 时服务端返回 `Content-Security-Policy: frame-ancestors` |
| 请求限流 | 每个分享令牌独立限流，复用 `ApiKeyRateLimiter` 的 Redis 实现 |
| 执行归属 | 运行时只能查看自己通过该分享创建的执行记录 |
| 过期检查 | 每次请求检查令牌是否过期/停用 |

### 7.2 运行时页面隔离

```
运行时页面（/app/*, /platform/runtime/*）：
  ✅ 使用分享令牌认证
  ✅ 独立 Layout（无侧边栏、无后台菜单）
  ❌ 不复用后台 JWT
  ❌ 不能访问后台管理 API
  ❌ 不暴露工作流编辑能力
```

### 7.3 CSP 动态生成

```java
// WorkflowShareRuntimeController 中
if (share.getAllowedOrigins() != null && !share.getAllowedOrigins().isEmpty()) {
    String ancestors = String.join(" ", share.getAllowedOrigins());
    response.setHeader("Content-Security-Policy",
        "frame-ancestors " + ancestors);
} else {
    response.setHeader("Content-Security-Policy",
        "frame-ancestors 'self'");
}
response.setHeader("X-Content-Type-Options", "nosniff");
response.setHeader("X-Frame-Options", "SAMEORIGIN"); // 被 CSP 覆盖但兼容旧浏览器
```

---

## 8. 数据库迁移

新增独立迁移文件 `sql/07_workflow_share.sql`：

```sql
-- 1. 工作流分享令牌表
CREATE TABLE IF NOT EXISTS `platform_workflow_share` (
  -- 见第 3.3 节
);

-- 2. API Key 增加工作流级权限
ALTER TABLE `platform_api_key`
ADD COLUMN `allowed_workflows` JSON DEFAULT NULL
    COMMENT '允许调用的工作流编码列表 JSON，NULL 表示不限';

-- 3. 分享执行记录关联（可选，用于运行时只查自己的执行）
CREATE TABLE IF NOT EXISTS `platform_workflow_share_execution` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `share_id`       BIGINT      NOT NULL COMMENT '分享 ID',
  `execution_id`   VARCHAR(64) NOT NULL COMMENT '执行 ID',
  `session_id`     VARCHAR(64) DEFAULT NULL COMMENT '页面会话标识',
  `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_execution` (`execution_id`),
  KEY `idx_share_exec_share` (`share_id`, `create_time`),
  KEY `idx_share_exec_session` (`share_id`, `session_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享执行关联';
```

---

## 9. 实施计划

### Phase 1：API 接口完善（2-3 天）

```
任务：
1. platform_api_key 增加 allowed_workflows 字段
2. PlatformApiKey.java / Mapper 增加字段
3. ApiKeyAuthFilter / ApiKeyCallerContext 传递 allowedWorkflows
4. PlatformWorkflowOpenApiController 增加工作流权限检查
5. PlatformWorkflowOpenApiController 增加 SSE 端点
6. 新增 WorkflowShareExecutionView（脱敏视图）

产物：
  第三方后端可以通过 API Key 安全调用指定工作流
```

### Phase 2：分享令牌 + 运行时后端（3-5 天）

```
任务：
1. 创建 platform_workflow_share 表
2. WorkflowShare 实体 / Mapper / Service
3. PlatformConsoleShareController（管理后台 CRUD）
4. ShareTokenAuthFilter（令牌认证）
5. WorkflowShareRuntimeController（运行时 API）
6. CSP 动态生成
7. 分享执行关联表

产物：
  管理员可以创建分享，运行时 API 可用
```

### Phase 3：表单型 + 对话型模板（5-7 天）

```
任务：
1. RuntimePage.vue（入口 + 模板切换）
2. RuntimeHeader / RuntimeResult / RuntimeProgress 组件
3. FormTemplate.vue（复用 WorkflowExecutionInput）
4. ChatTemplate.vue（复用 chat.vue 逻辑）
5. 路由配置（/app/:shareCode）
6. runtime.js（API 封装）
7. 管理后台分享管理弹窗

产物：
  表单型和对话型页面可用，管理员可以创建和管理分享
```

### Phase 4：图片类模板（3-5 天）

```
任务：
1. ImageCompare.vue（滑块对比组件）
2. CompareTemplate.vue（前后对比模板）
3. ImageTemplate.vue（复用 draw.vue 逻辑）
4. GalleryTemplate.vue（画廊模板）
5. 文件上传 API

产物：
  图片类工作流页面可用
```

### Phase 5：剩余模板 + 完善（3-5 天）

```
任务：
1. ReportTemplate.vue（报告型）
2. TaskTemplate.vue（任务型）
3. QueryTemplate.vue（查询型）
4. iframe 嵌入代码生成
5. 自动推荐页面类型逻辑
6. 访问统计

产物：
  全部 8 种模板可用，功能完整
```

### 总计：3-5 周

```
Phase 1   API 接口         2-3 天
Phase 2   分享令牌后端      3-5 天
Phase 3   表单+对话模板     5-7 天
Phase 4   图片类模板        3-5 天
Phase 5   其余模板+完善     3-5 天
─────────────────────────────────
合计                       16-25 天（约 3-5 周）
```

---

## 10. 与 v4 方案的关系

本方案是 v4 方案的**极简实用子集**。对应关系：

| v4 概念 | 本方案 | 后续演进时机 |
|---------|--------|------------|
| Consumer Client | 不需要 | 出现多个消费项目需要独立管控时 |
| Contract + Adapter | 不需要 | 工作流输入输出大改且老调用方不能升级时 |
| Release（版本管理） | 不需要 | 需要固定不同消费方到不同工作流版本时 |
| Grant | 简化为分享令牌 | 权限维度需要更细时 |
| Experience + Layout DSL | 简化为 pageType + 模板 | 8 种模板无法满足、定制需求频繁时 |
| Browser Session + Ticket | 简化为令牌直接访问 | 需要短期会话 + 即时撤权时 |
| Channel 表 | 不需要 | 需要对不同渠道独立配置策略时 |
| 两层幂等 | 复用现有幂等 | 出现幂等窗口冲突时 |
| Widget Registry | 不需要 | 模板扩展到需要动态组合时 |
| Webhook | 不需要 | 消费方需要结果推送时 |
| Asset Pipeline | 简化为直接上传 | 需要严格的文件安全处理时 |

本方案的代码结构**不阻碍**后续向 v4 演进。分享令牌可以平滑升级为 Grant，模板可以演进为 Experience DSL。

---

## 11. 方案自查——问题、优化与易操作性

### 11.1 需要修正的问题

#### 问题 1：`share_code` 不应该存 SHA-256

方案第 3.3 节说"`share_code` 使用高熵随机值的 SHA-256"，但 `share_code` 本身需要出现在 URL 路径中（`/app/{shareCode}`）。

如果数据库只存哈希，前端拿到的是原始随机值，服务端需要对每个请求做一次 SHA-256 计算再查库——这对 API Key 是合理的（Key 很长、敏感），但对分享码没必要。

**修正**：`share_code` 直接存明文随机值（如 `sk_share_Xa7bKm9pQr...`，32 字符足够），数据库存原值，URL 直接用。分享码不需要像 API Key 那样做单向哈希，因为它本质上就是一个**公开标识符**（谁有链接谁能访问），不是密钥。

#### 问题 2：`SecurityConfig` 需要显式放行 `/platform/runtime/**`

当前 Spring Security 配置：

```java
.requestMatchers("/platform/api/**").hasRole("PLATFORM_API")
```

新增的 `/platform/runtime/**` 路径不在现有放行规则中，会被默认拦截。

**修正**：在 `SecurityConfig.java` 中增加：

```java
.requestMatchers("/platform/runtime/**").permitAll()  // ShareTokenAuthFilter 自行认证
```

同时前端 `permission.js` 的 `whiteList` 需要加入 `/app` 前缀：

```javascript
const whiteList = ['/login', '/register', '/login-demo', '/app']
```

#### 问题 3：`ShareTokenAuthFilter` 从 URL 提取 shareCode 的可靠性

方案说"从 URL path 提取 shareCode"，但 `/platform/runtime/shares/{shareCode}/executions/{id}` 这种多层路径，用字符串截取容易出错。

**修正**：不在 Filter 中解析路径，而是在 Filter 中从 Header（如 `X-Share-Code`）或 URL 参数中获取。前端 `runtime.js` 统一在请求中带上。或者更简单——Filter 只负责路径匹配和基础安全，`shareCode` 的验证由 Controller 的 `@PathVariable` 获取后调用 Service 完成。

```java
// ShareTokenAuthFilter
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/platform/runtime/");
}

@Override
protected void doFilterInternal(...) {
    // 只做基础安全检查（CORS、rate limit header 等）
    // shareCode 验证交给 Controller
    filterChain.doFilter(request, response);
}
```

实际的令牌验证放在 Service 层，Controller 调用 `shareService.validateAndGet(shareCode)` 即可。这比在 Filter 里做路径解析更清晰。

#### 问题 4：对话型模板的多轮机制不明确

方案说对话型"多轮对话 + 流式输出"，但没有说清楚：

- 每轮对话是启动一个新的 Workflow Execution，还是向同一个 Execution 发消息？
- 多轮上下文如何传递？

**修正**：需要明确两种模式：

```text
模式 A：每轮独立执行（简单，推荐首期）
  - 每次用户发消息 → 新建一个 Execution
  - 前端自己维护聊天历史，每次把历史 + 新消息作为 input 传给 Workflow
  - 工作流的 input schema 设计为 messages 数组

模式 B：持续会话（需要 Workflow V2 支持外部事件）
  - 启动一个长运行 Execution
  - 用户消息通过 External Event 注入
  - 需要 WorkflowExternalEventApplicationFacade 支持
```

首期用模式 A，在 `page_config_json` 中增加 `"chatMode": "independent"` 配置。

#### 问题 5：文件上传 API 的安全控制缺失

方案第 5.3 节提到 `POST /{shareCode}/upload` 上传文件，但没有说明安全限制。

**修正**：上传 API 必须：
- 限制文件大小（默认 10MB，可在 `page_config_json` 中配置）
- 限制文件类型（图片类只允许 image/*）
- 限制每个分享每日上传总量
- 上传到隔离目录（按 tenant/share 分目录）
- 返回相对 URL，不暴露存储路径

#### 问题 6：`visit_count` 和 `execution_count` 的并发更新

方案在 `platform_workflow_share` 表中使用两个计数字段做统计，直接 `UPDATE count = count + 1` 在高并发下可能有热点行问题。

**修正**：首期流量不大时直接 `count + 1` 没问题。如果后续流量大，改为 Redis INCR 异步刷回即可。在文档中标注这一点。

#### 问题 7：`platform_workflow_share_execution` 表会持续增长

每次执行都插入一条关联记录，长期没有清理策略。

**修正**：增加保留策略说明。建议按分享的过期时间 + 30 天定期清理，或者对已终态的执行只保留最近 1000 条的关联。

### 11.2 优化建议

#### 优化 1：分享码使用短码提升体验

32 字符的随机值在 URL 里太长。可以用 NanoID 生成 12-16 字符的短码：

```
原始：/app/sk_share_Xa7bKm9pQr2Lw5Hn8Jv6Ft4Yd1...
优化：/app/Xa7bKm9pQr2L
```

12 字符（62 进制）的碰撞概率极低（~3.2×10^21 种可能）。

#### 优化 2：manifest 接口增加缓存

manifest 内容在分享配置不变的情况下是固定的。加上 `Cache-Control` 和 `ETag`：

```java
// manifest 响应增加
response.setHeader("Cache-Control", "public, max-age=300");  // 5 分钟
response.setHeader("ETag", "\"" + share.getUpdateTime().getTime() + "\"");
```

减少重复请求，提升页面加载速度。

#### 优化 3：分享管理从弹窗改为独立 Tab

方案中的分享管理放在弹窗里，但功能较多（创建 + 列表 + 复制链接 + 配置）。

**建议**：在工作流详情页增加一个「分享」Tab，而不是弹窗。空间更大，配置项也更好组织。

```text
工作流详情页的 Tab：
  [设计] [运行记录] [资源绑定] [分享]  ← 新增
```

#### 优化 4：预览功能

创建分享后，管理员需要能预览最终效果。增加「预览」按钮，直接在新 Tab 打开 `/app/{shareCode}?preview=1`。

#### 优化 5：对话型模板支持 SSE 需要适配

当前 Workflow 的 SSE 事件格式（`WorkflowExecutionEventView`）是节点级别的事件，不是逐 Token 的流式文本。对话型模板要实现类似 ChatGPT 的逐字输出，需要：

- 工作流中的 LLM/Agent 节点产出的 Token 需要通过 Event 传出
- 前端解析事件中的 `data` 字段，提取文本 delta

**建议**：先确认现有 `ai_workflow_event` 是否已包含 LLM 的 streaming token。如果没有，对话型首期可以降级为"提交后等结果"，不做逐字输出。

#### 优化 6：自动推荐需要获取工作流定义

方案说"根据工作流节点类型自动推荐页面类型"，但创建分享的 API 需要能读到工作流的节点列表和 input schema。

**建议**：增加一个内部接口 `getWorkflowMeta(definitionId)`，返回：
- 节点类型列表
- input schema
- output schema

管理后台前端调用此接口来做推荐展示。

### 11.3 易操作性分析

#### ✅ 对管理员友好

| 操作 | 步骤 | 评价 |
|------|------|------|
| 创建 API 分发 | 创建 API Key → 选择允许的工作流 → 复制 Key | ✅ 简单，2 步 |
| 创建页面分享 | 选工作流 → 选页面类型 → 填配置 → 创建 → 复制链接 | ✅ 简单，4 步 |
| 创建 iframe 嵌入 | 同上 + 复制 iframe 代码 | ✅ 一键复制 |
| 停用分享 | 点击「停用」按钮 | ✅ 即时生效 |
| 查看使用情况 | 分享列表里直接看访问数/执行数 | ✅ 直观 |

#### ✅ 对第三方使用者友好

| 使用方式 | 操作 | 评价 |
|---------|------|------|
| API 调用 | 拿到 Key + 看 API 文档 → 调用 | ✅ 标准 REST |
| 打开页面 | 点击链接 → 直接使用 | ✅ 零门槛 |
| iframe 嵌入 | 复制代码粘贴到自己页面 | ✅ 零开发 |

#### ⚠️ 需要注意的体验问题

| 问题 | 建议 |
|------|------|
| 分享链接过长 | 用短码（优化 1） |
| 页面加载慢 | manifest 缓存 + 模板懒加载（已做） |
| 令牌过期无提示 | 前端 manifest 请求返回 403 时展示友好页面 |
| iframe 跨域问题 | 必须配置正确的 `allowed_origins`，文档中需要给出排查指引 |
| 移动端适配 | 各模板需要响应式设计，首期至少保证表单型和对话型的移动端可用 |

---

## 12. 最终修改清单

以下是根据第 11 节自查后的调整：

| 序号 | 原方案 | 修正后 |
|:---:|--------|--------|
| 1 | share_code 存 SHA-256 | 直接存短码明文（12 字符 NanoID） |
| 2 | ShareTokenAuthFilter 解析 URL 路径 | Filter 只做路径匹配，验证逻辑放 Service 层 |
| 3 | 对话型多轮机制未说明 | 首期用"每轮独立执行"模式 |
| 4 | 上传 API 无安全限制 | 增加大小/类型/总量限制 |
| 5 | visit_count 直接 UPDATE | 首期 OK，标注高并发时改 Redis |
| 6 | 无数据清理策略 | share_execution 按过期 +30 天清理 |
| 7 | 分享管理用弹窗 | 改为工作流详情页的独立 Tab |
| 8 | 未提 SecurityConfig 放行 | 需要增加 `/platform/runtime/**` 放行 |
| 9 | 未提前端 whiteList | 需要增加 `/app` 前缀到 whiteList |
| 10 | 未提移动端适配 | 标注表单型和对话型首期需要响应式 |

---

## 13. 结构性优化（v1.1 修订）

以下优化经过审查后纳入方案，**替代**前文中对应的设计。

### 13.1 删除 `platform_workflow_share_execution` 表

**原设计**：第 8 节新增关联表记录分享与执行的映射。

**优化后**：不需要此表。现有 `ai_workflow_execution` 已有 `principal_type` + `principal_id` 字段和索引：

```sql
-- 现有字段和索引（无需修改）
`principal_type` varchar(32)   -- 新增枚举值 'SHARE'
`principal_id`   varchar(128)  -- 填 share.id 的字符串
KEY `idx_wf_execution_principal` (`tenant_id`, `principal_type`, `principal_id`, `create_time`)
```

分享创建的执行直接设置 `principal_type = 'SHARE'`，查询时：

```sql
SELECT * FROM ai_workflow_execution
WHERE tenant_id = ? AND principal_type = 'SHARE' AND principal_id = ?
ORDER BY create_time DESC
```

**结果**：删除 1 张表 + 对应 Mapper/Service + 每次执行的关联插入逻辑。

### 13.2 删除 `ShareTokenAuthFilter`

**原设计**：第 5.2 节新增 Filter 拦截 `/platform/runtime/**`。

**优化后**：不需要此 Filter。原因：

1. `/platform/runtime/**` 在 SecurityConfig 中设为 `permitAll()`
2. `shareCode` 就在 `@PathVariable` 中，Controller 直接拿
3. 验证逻辑放在 Service 层更清晰

Controller 中直接验证：

```java
@RestController
@RequestMapping("/platform/runtime/shares")
public class WorkflowShareRuntimeController {

    private final IWorkflowShareService shareService;
    private final WorkflowExecutionApplicationFacade workflowFacade;

    @PostMapping("/{shareCode}/executions")
    public ResultData<?> start(@PathVariable String shareCode,
                               @RequestBody PlatformWorkflowExecutionRequest request) {
        // Service 内完成：查表 → 检查状态/过期 → 限流 → 返回 share 对象
        WorkflowShare share = shareService.validateAndGet(shareCode);

        // 构造 CallerContext 并设置
        ShareCallerContext context = new ShareCallerContext(
            share.getTenantId(), share.getId(), share.getWorkflowCode());
        CallerContextHolder.set(context);

        try {
            return ResultData.ok(workflowFacade.startByCode(
                new WorkflowExecutionByCodeCommand(
                    share.getWorkflowCode(),
                    request.input(),
                    request.environment(),
                    null)));
        } finally {
            CallerContextHolder.clear();
        }
    }
}
```

**结果**：删除 1 个 Filter 类，不需要修改 SecurityConfig 的 Filter 链。

### 13.3 保留 8 个模板，分批实施

**原设计**：第 4 节规划 8 种模板。

**决策**：保留全部 8 种模板设计，不缩减。每种模板有独特的交互模式，硬用 form 兜底会导致体验降级：

| 场景 | 用 form 兜底的问题 |
|------|-------------------|
| 报告型 | Markdown 富文本 + PDF 下载体验远优于普通结果展示 |
| 任务型 | 需要任务列表 + 进度追踪，不是单次提交能替代的 |
| 查询型 | 搜索栏 + 表格分页的交互与表单完全不同 |
| 前后对比 | 滑块对比是图片类核心体验，不能降级为普通结果展示 |
| 画廊型 | 多图网格 + 放大预览，不是 form 能覆盖的 |

**但分批实施**，按优先级排列：

```
第 1 批（Phase 3，必须）：
  ├── form      表单型       ← 覆盖最多场景，最先做
  ├── chat      对话型       ← AI 平台核心场景
  └── image     图片工作台    ← 复用 draw.vue，成本低

第 2 批（Phase 4，紧随其后）：
  ├── compare   前后对比      ← 试戴/换装场景需要
  ├── gallery   画廊型       ← 文生图场景需要
  └── report    报告型       ← 复用 PolarisReportEngine

第 3 批（Phase 5，按需求优先级）：
  ├── task      批量任务型    ← 复用 WorkflowExecutionMonitor
  └── query     数据查询型    ← 表格+图表渲染
```

前端目录保持完整设计：

```
views/workflowApp/
├── RuntimePage.vue
└── templates/
    ├── FormTemplate.vue            ← 第 1 批
    ├── ChatTemplate.vue            ← 第 1 批
    ├── ImageTemplate.vue           ← 第 1 批
    ├── CompareTemplate.vue         ← 第 2 批
    ├── GalleryTemplate.vue         ← 第 2 批
    ├── ReportTemplate.vue          ← 第 2 批
    ├── TaskTemplate.vue            ← 第 3 批
    └── QueryTemplate.vue           ← 第 3 批
```

**未实现的模板自动 fallback 到 form**：RuntimePage 中，如果模板文件尚未开发，自动降级为 FormTemplate，保证任何分享链接都能用。

### 13.4 `page_type` 挂在工作流定义上

**原设计**：每次创建分享时选择页面类型。

**优化后**：在 `ai_workflow_definition` 上增加默认页面配置，创建分享时自动继承：

```sql
ALTER TABLE `ai_workflow_definition`
ADD COLUMN `default_page_type` VARCHAR(16) DEFAULT 'form'
    COMMENT '默认分享页面类型: form/chat/image',
ADD COLUMN `share_page_config_json` JSON DEFAULT NULL
    COMMENT '默认分享页面配置 JSON';
```

创建分享时自动填充，管理员可覆盖。好处：
- 工作流设计者设定一次，所有分享自动继承
- 创建分享变成**一步操作**

### 13.5 简化 `platform_workflow_share` 表

合并以上优化后的最终表结构：

```sql
CREATE TABLE IF NOT EXISTS `platform_workflow_share` (
  `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`              BIGINT       NOT NULL COMMENT '租户 ID',
  `share_code`             VARCHAR(16)  NOT NULL COMMENT '分享短码（NanoID 12-16 字符明文）',
  `share_name`             VARCHAR(128) NOT NULL COMMENT '分享名称',
  `workflow_definition_id` BIGINT       NOT NULL COMMENT '工作流定义 ID',
  `page_type`              VARCHAR(16)  DEFAULT NULL COMMENT '页面类型覆盖，NULL 继承工作流默认',
  `page_config_json`       JSON         DEFAULT NULL COMMENT '页面配置覆盖，NULL 继承工作流默认',
  `allowed_origins`        JSON         DEFAULT NULL COMMENT 'iframe 允许嵌入的 Origin 列表',
  `rate_limit`             INT          DEFAULT 60 COMMENT '每分钟请求次数限制',
  `status`                 CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 正常 1 停用）',
  `expire_time`            DATETIME     DEFAULT NULL COMMENT '过期时间，NULL 永不过期',
  `create_by`              VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  `update_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_code` (`share_code`),
  KEY `idx_share_tenant` (`tenant_id`, `status`),
  KEY `idx_share_workflow` (`tenant_id`, `workflow_definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流分享';
```

与原设计相比删除了 `workflow_code`（冗余）、`daily_quota`（首期不需要）、`visit_count/execution_count/last_used_time`（用 Redis 或查询统计）。

---

## 14. 实施细节补充——6 个关键实现问题

### 14.1 ShareCallerContext 怎么构造

新建 `ShareCallerContext`，实现 `CallerContext` 接口：

```java
package com.polaris.platform.auth;

import com.polaris.ai.core.context.CallerContext;

public class ShareCallerContext implements CallerContext {

    private final Long tenantId;
    private final Long shareId;
    private final String workflowCode;

    public ShareCallerContext(Long tenantId, Long shareId, String workflowCode) {
        this.tenantId = tenantId;
        this.shareId = shareId;
        this.workflowCode = workflowCode;
    }

    @Override public Long getUserId()    { return null; }
    @Override public String getTenantId() { return String.valueOf(tenantId); }
    @Override public String getUsername() { return "share:" + shareId; }
    @Override public Long getDeptId()    { return null; }
    @Override public boolean isSuperAdmin() { return false; }
    @Override public boolean isPlatformMode() { return true; }

    @Override
    public boolean hasPermission(String permission) {
        return "workflow:execute".equals(permission)
            || "workflow:read".equals(permission);
    }

    public Long getShareId()       { return shareId; }
    public String getWorkflowCode() { return workflowCode; }
}
```

同时在 `PlatformWorkflowPrincipalContextProvider` 中增加 `SHARE` 类型支持：

```java
// supports() 增加
|| "SHARE".equals(principalType)

// resolve() 增加
if ("SHARE".equals(principalType)) {
    long shareId = Long.parseLong(principalId);
    WorkflowShare share = shareService.selectById(shareId);
    if (share == null || !tenantId.equals(share.getTenantId())
            || !"0".equals(share.getStatus())) {
        return Optional.empty();
    }
    return Optional.of(new ShareCallerContext(
        tenantId, shareId, share.getWorkflowCode()));
}
```

### 14.2 manifest 的 inputSchema 从哪来

工作流的 input schema 存储路径：

```
ai_workflow_definition.current_published_version_id
  → ai_workflow_version（根据 version_id 查）
    → execution_plan_json（JSON 字段）
      → WorkflowExecutionPlan.inputs（JsonNode 类型，即 JSON Schema）
```

manifest 接口实现：

```java
@GetMapping("/{shareCode}/manifest")
public ResultData<WorkflowShareManifest> manifest(
        @PathVariable String shareCode, HttpServletResponse response) {

    WorkflowShare share = shareService.validateAndGet(shareCode);
    setCspHeaders(response, share.getAllowedOrigins());

    // 获取工作流定义
    WorkflowDefinition def = workflowDefinitionMapper
        .selectById(share.getWorkflowDefinitionId());

    // 从已发布版本的执行计划中提取 input schema
    JsonNode inputSchema = null;
    if (def.getCurrentPublishedVersionId() != null) {
        WorkflowVersion version = workflowVersionMapper
            .selectByVersionId(def.getCurrentPublishedVersionId());
        if (version != null) {
            WorkflowExecutionPlan plan = objectMapper.readValue(
                version.getExecutionPlanJson(), WorkflowExecutionPlan.class);
            inputSchema = plan.getInputs();
        }
    }

    // 决定 pageType：分享覆盖 > 工作流默认 > form
    String pageType = share.getPageType() != null
        ? share.getPageType()
        : (def.getDefaultPageType() != null
            ? def.getDefaultPageType() : "form");

    return ResultData.ok(new WorkflowShareManifest(
        share.getShareName(),
        def.getWorkflowCode(),
        pageType,
        inputSchema,
        mergePageConfig(share, def)
    ));
}
```

### 14.3 表单模板怎么渲染不同类型的输出

`RuntimeResult.vue` 根据 output 内容自动检测渲染方式：

```javascript
function detectOutputType(outputJson) {
  if (!outputJson) return 'empty'
  const obj = typeof outputJson === 'string' ? JSON.parse(outputJson) : outputJson
  const keys = Object.keys(obj)

  // 只有一个字段且是字符串 → 文本或 Markdown
  if (keys.length === 1 && typeof obj[keys[0]] === 'string') {
    const value = obj[keys[0]]
    if (value.includes('# ') || value.includes('**') || value.includes('```'))
      return 'markdown'
    if (value.startsWith('http') && /\.(png|jpg|jpeg|gif|webp)$/i.test(value))
      return 'image'
    return 'text'
  }

  // 数组 → 表格
  if (Array.isArray(obj) || (keys.length === 1 && Array.isArray(obj[keys[0]])))
    return 'table'

  // 其他 → JSON 树
  return 'json'
}
```

渲染组件：

```vue
<template>
  <div class="runtime-result">
    <div v-if="type === 'markdown'" v-html="renderMarkdown(content)" />
    <pre v-else-if="type === 'text'">{{ content }}</pre>
    <el-image v-else-if="type === 'image'" :src="content" fit="contain" />
    <el-table v-else-if="type === 'table'" :data="tableData" size="small">
      <el-table-column v-for="col in tableColumns" :key="col"
        :prop="col" :label="col" />
    </el-table>
    <pre v-else>{{ JSON.stringify(output, null, 2) }}</pre>
  </div>
</template>
```

### 14.4 对话模板的 SSE 事件与多轮机制

**现有事件流**（`WorkflowEventType`）：

```
NODE_STARTED → NODE_OUTPUT → NODE_SUCCEEDED → EXECUTION_SUCCEEDED
```

`NODE_OUTPUT` 的 `payloadJson` 包含节点输出数据。对话模板从中提取 AI 回复文本。

**SSE 处理逻辑**：

```javascript
function streamExecution(shareCode, executionId) {
  const es = new EventSource(
    `/platform/runtime/shares/${shareCode}/executions/${executionId}/events/stream`)

  es.onmessage = (event) => {
    const data = JSON.parse(event.data)
    if (data.eventType === 'NODE_OUTPUT') {
      const payload = JSON.parse(data.payloadJson)
      // 从 output 中提取文本（LLM/Agent 节点的输出字段）
      const text = payload.result || payload.output || payload.text
                || payload.content || JSON.stringify(payload)
      appendMessage('assistant', text)
    }
    if (data.eventType === 'EXECUTION_SUCCEEDED') es.close()
    if (data.eventType === 'EXECUTION_FAILED') {
      es.close()
      appendMessage('system', '处理失败，请重试')
    }
  }
}
```

**多轮机制（首期：每轮独立执行）**：

```javascript
async function sendMessage(userInput) {
  chatHistory.push({ role: 'user', content: userInput })
  // 把完整历史作为 input 传给工作流
  const res = await startExecution(shareCode, {
    input: { messages: chatHistory, currentMessage: userInput }
  })
  streamExecution(shareCode, res.executionId)
}
```

注意：首期 `NODE_OUTPUT` 是节点完成后一次性输出，不是逐 token 流式。后续可增加 `LLM_TOKEN_DELTA` 事件类型支持逐字打印。配置标记：

```json
{ "chatMode": "independent", "streamMode": "node_output" }
```

### 14.5 图片模板调什么 API

**统一通过 Workflow 执行 API**（推荐，保持架构一致）：

```
前端上传图片
  → POST /{shareCode}/upload → 返回图片 URL
  → POST /{shareCode}/executions
      input: { sourceImage: "url", prompt: "..." }
  → Workflow 内部调图片模型
  → 返回 output: { resultImage: "url" } 或 { resultImages: ["url1", "url2"] }
  → 前端展示
```

ImageTemplate 内部根据 `imageMode` 配置切换子布局：

```vue
<template>
  <ImageWorkspace v-if="mode === 'workspace'" ... />
  <ImageCompare v-else-if="mode === 'compare'" ... />
  <ImageGallery v-else ... />
</template>
```

### 14.6 文件上传的存储与认证

复用现有 `FileUploadUtils`，按分享维度隔离存储：

```java
@PostMapping("/{shareCode}/upload")
public ResultData<Map<String, String>> upload(
        @PathVariable String shareCode,
        @RequestParam MultipartFile file) {

    WorkflowShare share = shareService.validateAndGet(shareCode);

    // 安全校验
    if (file.getSize() > 10 * 1024 * 1024)           // 10MB 限制
        throw new IllegalArgumentException("文件大小超过限制");
    if (file.getContentType() == null
        || !file.getContentType().startsWith("image/"))  // 仅图片
        throw new IllegalArgumentException("仅允许上传图片文件");

    // 隔离路径：/{profile}/share/{tenantId}/{shareId}/
    String subDir = "share/" + share.getTenantId() + "/" + share.getId();
    String filePath = FileUploadUtils.upload(
        PolarisConfig.getUploadPath() + "/" + subDir, file);

    return ResultData.ok(Map.of("url", filePath));
}
```

上传接口在 `/platform/runtime/` 路径下，已被 `permitAll()` 覆盖。

---

## 15. 修订后的完整变更清单

### 15.1 数据库变更（`sql/07_workflow_share.sql`）

```sql
-- 1. 新增分享表（唯一新表）
CREATE TABLE IF NOT EXISTS `platform_workflow_share` ( ... );  -- 见 13.5

-- 2. 工作流定义增加默认页面配置
ALTER TABLE `ai_workflow_definition`
ADD COLUMN `default_page_type` VARCHAR(16) DEFAULT 'form'
    COMMENT '默认分享页面类型: form/chat/image',
ADD COLUMN `share_page_config_json` JSON DEFAULT NULL
    COMMENT '默认分享页面配置 JSON';

-- 3. API Key 增加工作流级权限
ALTER TABLE `platform_api_key`
ADD COLUMN `allowed_workflows` JSON DEFAULT NULL
    COMMENT '允许调用的工作流编码列表 JSON，NULL 表示不限';
```

### 15.2 后端文件清单（8 个新文件 + 3 个修改）

```
新增：
  domain/WorkflowShare.java
  mapper/WorkflowShareMapper.java（+ resources/mapper XML）
  service/IWorkflowShareService.java
  service/impl/WorkflowShareServiceImpl.java
  controller/PlatformConsoleShareController.java       ← 管理 CRUD
  controller/WorkflowShareRuntimeController.java       ← 运行时 API
  auth/ShareCallerContext.java
  dto/WorkflowShareManifest.java

修改：
  auth/PlatformWorkflowPrincipalContextProvider.java   ← 增加 SHARE 类型
  auth/ApiKeyCallerContext.java                        ← 增加 allowedWorkflows
  controller/openApi/PlatformWorkflowOpenApiController.java  ← 工作流权限检查 + SSE

配置：
  SecurityConfig.java  ← 增加一行 .requestMatchers("/platform/runtime/**").permitAll()
```

### 15.3 前端文件清单（15 个新文件 + 2 个修改）

```
新增：
  views/workflowApp/RuntimePage.vue
  views/workflowApp/SharePanel.vue                     ← 管理后台分享面板
  views/workflowApp/templates/FormTemplate.vue          ← 第 1 批
  views/workflowApp/templates/ChatTemplate.vue          ← 第 1 批
  views/workflowApp/templates/ImageTemplate.vue         ← 第 1 批
  views/workflowApp/templates/CompareTemplate.vue       ← 第 2 批
  views/workflowApp/templates/GalleryTemplate.vue       ← 第 2 批
  views/workflowApp/templates/ReportTemplate.vue        ← 第 2 批
  views/workflowApp/templates/TaskTemplate.vue          ← 第 3 批
  views/workflowApp/templates/QueryTemplate.vue         ← 第 3 批
  components/workflow-app/RuntimeHeader.vue
  components/workflow-app/RuntimeResult.vue
  components/workflow-app/RuntimeProgress.vue
  components/workflow-app/ImageCompare.vue
  api/workflowApp/runtime.js

修改：
  router/index.js    ← 增加 /app/:shareCode 路由
  permission.js      ← whiteList 增加 '/app'
```

### 15.4 修订后实施计划

```
Phase 1  后端基础（4-5 天）
  ├── DDL + WorkflowShare 实体/Mapper/Service
  ├── ShareCallerContext + PrincipalContextProvider 扩展
  ├── WorkflowShareRuntimeController（manifest + 执行 + SSE + 上传）
  ├── PlatformConsoleShareController（CRUD）
  ├── API Key allowed_workflows + 权限检查
  └── SecurityConfig 放行

Phase 2  前端基础 + 管理入口（3-4 天）
  ├── RuntimePage.vue + 路由 + permission 放行
  ├── RuntimeHeader / RuntimeResult / RuntimeProgress
  ├── runtime.js API 封装
  └── SharePanel.vue（管理后台分享入口）

Phase 3  第 1 批模板：form + chat + image（5-7 天）
  ├── FormTemplate.vue（复用 WorkflowExecutionInput）
  ├── ChatTemplate.vue（SSE 事件解析 + 聊天 UI）
  ├── ImageTemplate.vue（复用 draw.vue 核心逻辑）
  └── iframe CSP + 嵌入代码复制

Phase 4  第 2 批模板：compare + gallery + report（4-5 天）
  ├── ImageCompare.vue（滑块对比组件）
  ├── CompareTemplate.vue
  ├── GalleryTemplate.vue
  └── ReportTemplate.vue（复用 PolarisReportEngine）

Phase 5  第 3 批模板：task + query（3-4 天）
  ├── TaskTemplate.vue（复用 WorkflowExecutionMonitor）
  └── QueryTemplate.vue（表格 + 图表渲染）

合计：19-25 天（约 4-5 周）

其中 Phase 1-3 完成后即可对外使用（12-16 天，约 3 周）
Phase 4-5 按业务需求优先级安排
```

---

## 16. 最终检查——代码交叉验证发现的遗漏

### 16.1 🔴 `currentPrincipal()` 不识别 SHARE 类型（必须修改）

`WorkflowExecutionService.java` 第 668-689 行的 `currentPrincipal()` 方法通过 `username` 前缀判断调用者类型，只认 `"apikey:"` 前缀。

方案中 `ShareCallerContext.getUsername()` 返回 `"share:xxx"`，但该方法不处理此前缀，会走到 `isPlatformMode()` 分支，被误判为 `PLATFORM_USER`，且 `getUserId()` 返回 `null` 导致空指针。

**必须修改**（`polaris-ai` 模块）：

```java
// WorkflowExecutionService.currentPrincipal() 中增加
if (username != null && username.startsWith("apikey:")) {
    type = "API_KEY";
    id = username.substring("apikey:".length());
} else if (username != null && username.startsWith("share:")) {  // ← 新增
    type = "SHARE";
    id = username.substring("share:".length());
} else if (caller.isPlatformMode()) {
    type = "PLATFORM_USER";
    id = String.valueOf(caller.getUserId());
} else {
    type = "ADMIN";
    id = String.valueOf(caller.getUserId());
}
```

### 16.2 🔴 NanoID 短码生成方式

项目当前用 `UUID`（`PlatformApiKeyServiceImpl` 第 45 行：`"sk-" + UUID.randomUUID()`）。

分享短码两种选择：

| 方案 | 代码 | 碰撞空间 |
|------|------|---------|
| 引入 jnanoid 依赖 | `NanoIdUtils.randomNanoId(..., 12)` | 62^12 ≈ 3.2×10^21 |
| UUID 截取（推荐，无新依赖） | `UUID.randomUUID().toString().replace("-","").substring(0,12)` | 16^12 ≈ 2.8×10^14 |

建议用 UUID 截取方案，无需引入新依赖，碰撞概率在分享量级下完全可忽略，且写入前数据库唯一索引兜底。

### 16.3 🟡 CORS 配置需要补充

`/platform/runtime/**` 路径 `permitAll()` 解决了 Spring Security 问题，但**跨域请求**还需要 CORS 配置。当前 CORS 配置在 `ResourcesConfig.java`。

需要增加：

```java
.addMapping("/platform/runtime/**")
.allowedOriginPatterns("*")
.allowedMethods("GET", "POST", "OPTIONS")
.allowedHeaders("*")
```

否则 iframe 中的 API 请求会被浏览器拦截。

### 16.4 🟡 SSE `EventSource` 跨域限制

`EventSource` 不支持自定义 Header，shareCode 只能放在 URL 路径中（方案已如此设计）。但 CORS 预检请求需要覆盖 SSE 端点路径，Content-Type 为 `text/event-stream`。

### 16.5 🟢 工作流状态自动保护

`startByCode()` 第 112 行检查 `status = 'ACTIVE'`，所以：

> **停用工作流 = 自动停用该工作流的所有分享**（无需逐个操作）

这是好的设计，不需要额外处理。

### 16.6 🟢 `currentTenantId()` 类型兼容

`ShareCallerContext.getTenantId()` 返回 `String.valueOf(tenantId)`，`currentTenantId()` 用 `Long.parseLong()` 解析，类型链条正确。

---

## 17. 最终修改文件清单（修订版）

```
后端新增（8 个文件）：
  domain/WorkflowShare.java
  mapper/WorkflowShareMapper.java（+ resources/mapper XML）
  service/IWorkflowShareService.java
  service/impl/WorkflowShareServiceImpl.java
  controller/PlatformConsoleShareController.java
  controller/WorkflowShareRuntimeController.java
  auth/ShareCallerContext.java
  dto/WorkflowShareManifest.java

后端修改（5 个文件）：
  auth/PlatformWorkflowPrincipalContextProvider.java   ← 增加 SHARE 支持
  auth/ApiKeyCallerContext.java                        ← 增加 allowedWorkflows
  controller/openApi/PlatformWorkflowOpenApiController.java  ← 权限检查 + SSE
  WorkflowExecutionService.java（polaris-ai 模块）     ← currentPrincipal() 增加 share: ★
  SecurityConfig.java / ResourcesConfig.java           ← permitAll + CORS ★

SQL（1 个文件）：
  sql/07_workflow_share.sql（1 张新表 + 2 个 ALTER）

前端新增（15 个文件）：
  views/workflowApp/RuntimePage.vue
  views/workflowApp/SharePanel.vue
  views/workflowApp/templates/ × 8 个模板文件
  components/workflow-app/ × 4 个公共组件
  api/workflowApp/runtime.js

前端修改（2 个文件）：
  router/index.js    ← 增加 /app/:shareCode 路由
  permission.js      ← whiteList 增加 '/app'
```

---

## 18. 可执行性评估

| 检查项 | 状态 |
|--------|------|
| 数据库表结构完整 DDL | ✅ |
| 后端类职责和接口定义 | ✅ 有代码级示例 |
| CallerContext 集成路径 | ✅ 追溯到 currentPrincipal()，明确改动点 |
| SecurityConfig + CORS 改动 | ✅ |
| 前端路由和模板切换逻辑 | ✅ 有代码 |
| SSE 事件处理和多轮机制 | ✅ 有代码 |
| 文件上传存储方案 | ✅ |
| 各模板复用的现有组件 | ✅ 已列出 |
| 模板分批实施顺序 | ✅ |
| 所有需要修改的文件 | ✅ 已列出 |
| 跨模块依赖（polaris-ai） | ✅ 已识别并给出改动代码 |

**结论：方案已可执行，无需进一步细化。**
