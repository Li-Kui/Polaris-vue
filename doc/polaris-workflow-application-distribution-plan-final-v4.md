# Polaris 工作流应用化分发与多形态消费最终技术方案 v4.0

> **文档状态**：最终开发评审与实施基线  
> **适用代码基线**：Polaris-Vue `master`（用户已确认原 `dev` 已合并到 `master`）  
> **仓库**：https://gitee.com/Li_kui/polaris-vue  
> **适用范围**：AI 开放中台租户将已发布 Workflow 能力授权给本租户其他项目使用，包括 API、独立页面、iframe、Widget、H5/WebView 等形态  
> **核心业务前提**：消费方不是匿名公网用户，而是租户自己的 Consumer Client，例如无法升级或不方便大改的老 ERP、CRM、商城、客服系统、移动端等  
> **设计目标**：严格基于当前 Polaris-Vue `master` 的现有模块、数据库与 Workflow V2 能力做增量演进；工作流内部可以持续升级，而消费项目依赖稳定 Contract；同一个 Workflow App 可以通过不同 Channel、不同 Experience，以安全、可版本化、可回滚、可审计的方式被多个项目复用。

---

# 1. 背景与问题重新定义

Polaris 当前已经具备：

- 多租户 AI 开放中台；
- API Key；
- API Key 权限、限流、过期控制；
- Token 计量；
- 数据源和 API Connector；
- Universal Workflow V2；
- Workflow 不可变 Version；
- Resource Binding；
- Execution 持久化；
- Checkpoint；
- Outbox；
- Lease / fencingToken；
- SSE Event Store；
- Cancel / Retry / Recovery；
- 子工作流、Wait、Approval、Artifact 等节点。

本功能不应理解为：

> “给工作流生成一个匿名免登录链接”。

而应该理解为：

> **把一个不可变 Workflow Version 封装成稳定的 Workflow Application，通过 Consumer Contract 向租户自己的业务项目提供长期稳定能力，并允许不同项目使用不同 Release、Channel 和 Experience。**

典型场景：

```text
租户 A
├── 新管理后台
├── 老 ERP（难升级）
├── 老 CRM（只能 iframe）
├── 商城（需要右下角 Widget）
├── 移动 App（WebView/H5）
└── 服务端任务（只调 API）

            ↓

       Workflow App
            ↓
   Universal Workflow V2
```

核心价值不是“链接分享”，而是：

1. Workflow 内部实现可以升级；
2. 老项目接口不必跟着升级；
3. 不同项目可以固定不同 Release；
4. 不同项目可以使用不同页面布局；
5. 所有调用仍然保持租户隔离、资源隔离和成本治理；
6. API、页面、iframe、Widget 复用同一套运行时和授权模型。

---

# 2. 当前 master 基线与设计约束

## 2.1 Workflow Execution 已支持 API_KEY 主体

当前 `ai_workflow_execution` 已包含：

```text
principal_type
principal_id
idempotency_scope
idempotency_key
principal_snapshot
binding_snapshot
budget_json
usage_json
quota_scopes_json
```

并且 `principal_type` 已包含：

```text
ADMIN
PLATFORM_USER
API_KEY
SERVICE_ACCOUNT
```

因此本方案**不新增 EXTERNAL_APP Principal**。

项目分享调用统一复用：

```text
principalType = API_KEY
```

浏览器 Session 最终也映射回明确的：

```text
tenantId
clientId
apiKey / grant context
```

而不是成为另一个宽权限身份体系。

## 2.2 API Key 已有基础能力，但缺少 Client 维度

当前 `platform_api_key` 已有：

```text
tenant_id
api_key (SHA-256 digest)
key_prefix
permissions
rate_limit
status
expire_time
last_used_time
```

缺少的是：

```text
client_id
```

当前只能表达：

```text
这是 Tenant A 的 Key
```

不能完整表达：

```text
这是 Tenant A / legacy-crm 项目的 Key
```

因此本方案增加 `Consumer Client`，并让 API Key 归属于 Client。

## 2.3 Workflow Version 已不可变

当前 Workflow V2 已通过：

```text
version_id
version_no
schema_version
definition_json
execution_plan_json
content_hash
```

形成不可变版本。

因此 App Release 必须引用确定的：

```text
workflowVersionId
```

执行过程中绝不能重新读取：

```text
current_published_version_id
```

避免运行时漂移。

## 2.4 Resource Binding 已存在

当前已有：

```text
owner_type
owner_id
tenant_id
scope_type
scope_id
environment
resource_kind
resource_key
resource_id
binding_version
```

因此新体系不再创建一套平行 Resource ACL。

App Release 只保存：

> 发布时解析得到的资源能力快照和审计快照。

真正运行时仍复用 Workflow Resource Binding 与 API Key / Grant 授权。

---


## 2.5 当前 master 的代码与目录落位原则

本方案不是重新建设一套 AI 平台，而是基于当前仓库已有模块做增量扩展。

当前 `master` 已存在的核心模块包括：

```text
polaris-platform-core
polaris-ai
polaris-ai-core
polaris-framework
polaris-common
polaris-system
polaris-ui-vue3
sql
```

当前前端 `polaris-ui-vue3/src` 已有：

```text
api
assets
components
directive
layout
plugins
router
store
styles
utils
views
```

因此最终实施必须遵守：

| 能力 | 当前基线 | 最终动作 |
|---|---|---|
| Tenant | `platform_tenant` | 直接复用 |
| API Key | `platform_api_key` | 增量增加 `client_id`，保留现有认证/权限/限流语义 |
| Workflow Definition | `ai_workflow_definition` | 直接复用 |
| Workflow Version | `ai_workflow_version` | 直接复用，不复制 Version 表 |
| Workflow Execution | `ai_workflow_execution` | 直接复用，不建立 App Execution 状态机 |
| Resource Binding | `ai_workflow_resource_binding` | 直接复用 |
| Checkpoint / Event / Outbox / Lease / Retry | Workflow V2 | 直接复用 |
| Consumer Client | 当前缺失 | 新增 |
| Workflow App / Contract / Release / Grant | 当前缺失 | 新增应用化分发层 |
| Browser Ticket / Session | 当前缺失 | 新增，但仅用于浏览器 Channel |
| Experience / Layout DSL | 当前缺失 | 在 `polaris-ui-vue3` 增量实现 |

禁止为了本功能重新实现：

```text
Workflow Scheduler
Workflow Worker
Checkpoint
Retry
Outbox
Event Store
Token Accounting
Datasource Runtime
Connector Runtime
```

这些全部通过现有 Polaris 能力接入。

## 2.6 新增代码的推荐落位

新增 Java 代码应放在现有模块内部的新业务包中，而不是新建第二套微服务。

### polaris-platform-core

新增一个相对独立的 `workflowapp` 业务域（具体根 package 按仓库现有 package 命名保持一致）：

```text
workflowapp/
├── controller/
├── service/
├── domain/
├── mapper/
├── auth/
├── grant/
├── contract/
├── release/
├── channel/
├── experience/
├── asset/
├── event/
└── webhook/
```

负责 App 分发层，不负责 Workflow 调度。

### polaris-ai

只增加与现有 Workflow V2 的集成点：

```text
workflow application publish analysis
capability closure
sub-workflow version closure
contract/release adapter execution hook
public event projection source adapter
```

不得把 `Grant / Browser Session / Experience Designer` 塞进 Workflow Engine。

### polaris-framework

只允许沉淀真正通用的基础能力：

```text
Origin matcher
security header helper
HMAC helper
Redis compare-and-delete / atomic helper
通用限流 primitive
通用 sanitizer primitive
```

业务对象 `WorkflowApp / Grant / Experience` 不进入 framework。

### polaris-ui-vue3

沿用现有目录结构：

```text
src/api/workflowApp/
src/views/workflowApp/
src/components/workflow-app/
src/router/
src/store/
```

独立运行页面建议放：

```text
src/views/workflowApp/runtime/
```

Designer 和 Runtime 必须逻辑隔离，Runtime 路由不得复用管理后台 Layout 与后台 JWT。

## 2.7 数据库上线必须使用增量迁移

当前 `sql/ai_workflow.sql` 是完整初始化/重建脚本，包含历史表 `DROP TABLE IF EXISTS`。

因此生产环境实施本功能时：

> **严禁把 `sql/ai_workflow.sql` 当增量升级脚本在线上重新执行。**

最终建议新增独立的增量脚本，例如：

```text
sql/ai_workflow_app_v4_migration.sql
```

只允许：

```text
CREATE TABLE IF NOT EXISTS ...
ALTER TABLE platform_api_key ADD COLUMN client_id ...
CREATE INDEX ...
数据回填
```

不得包含：

```text
DROP TABLE
TRUNCATE
重建 ai_workflow_execution
重建 ai_workflow_version
```

如果项目现有“启动自愈/Schema Patch”机制已有正式入口，可以将同样的增量操作接入该机制，但必须保持：

```text
可重复执行
幂等
可回滚
不会删除已有 Workflow 数据
```

## 2.8 代码级边界结论

最终实现可以概括为：

```text
现有 Polaris
    │
    ├── Tenant / API Key / Quota / Connector     ← 复用/轻扩展
    ├── Universal Workflow V2                    ← 直接复用
    │
    └── Workflow Application Distribution        ← 新增
            ├── Client
            ├── App
            ├── Contract
            ├── Release
            ├── Grant
            ├── Channel
            └── Experience
```

新增层只解决：

> **“谁可以以什么契约、什么版本、什么渠道和什么页面形态消费现有 Workflow。”**

它不能成为第二套 Workflow Runtime。

# 3. 核心设计原则

## 3.1 一个 App，多种消费 Channel

不要创建：

```text
API App
Web App
iframe App
Widget App
```

而应该：

```text
Workflow App
├── API
├── DIRECT_WEB
├── EMBED
├── WIDGET
└── H5
```

所有 Channel：

- 共用 Workflow App；
- 共用 Release；
- 共用 Contract；
- 共用 Grant；
- 共用 Execution Runtime；
- 共用成本治理；
- 共用审计。

## 3.2 Workflow、Contract、Experience 三者分离

```text
Workflow
→ 内部怎么执行

Consumer Contract
→ 外部看到什么输入、输出、事件和错误

Experience
→ 页面如何展示和交互
```

三者不可混为一体。

## 3.3 分享地址不是安全凭据

任何页面 URL 都按“已经泄露”设计：

```text
知道 URL
≠
拥有执行权限
```

URL 只定位：

```text
appCode / experienceCode
```

真正权限来自：

```text
Tenant
↓
Consumer Client
↓
API Key
↓
App Grant
```

浏览器渠道进一步使用：

```text
One-Time Ticket
↓
Browser Session
```

## 3.4 API Key 永不进入浏览器

API Key 只允许存在：

```text
Consumer Client Backend
```

禁止：

- URL；
- LocalStorage；
- SessionStorage；
- HTML；
- JS Bundle；
- window 全局变量；
- iframe src；
- postMessage 长期凭据。

## 3.5 默认拒绝

以下全部：

```text
DENY BY DEFAULT
```

包括：

- Client 对 App 的访问；
- Channel；
- Origin；
- Contract 字段；
- Host Event；
- Widget；
- Workflow Resource；
- Asset；
- Callback URL；
- Connector；
- SubWorkflow；
- Agent Tool。

## 3.6 不建立第二套 Workflow 执行事实源

App Runtime 不复制：

- Execution 状态；
- NodeRun；
- Checkpoint；
- Retry；
- Lease；
- Outbox；
- Event Store。

只保存：

- App；
- Contract；
- Release；
- Consumer Client；
- Grant；
- Channel；
- Experience；
- Invocation Mapping；
- Asset；
- Audit。

## 3.7 发布时编译，运行时轻量执行

复杂工作：

- DAG 能力扫描；
- Contract 校验；
- Breaking Change 分析；
- SubWorkflow 递归；
- Tool 能力分析；
- Layout DSL 校验；
- Binding 校验；
- Security Policy Snapshot；

尽量放在 Publish 阶段。

运行时不重复遍历整张 Workflow DAG。

---

# 4. 核心领域模型

最终领域关系：

```text
Tenant
│
├── Consumer Client
│      │
│      ├── API Key A
│      ├── API Key B
│      └── API Key C
│
└── Workflow App
       │
       ├── Consumer Contract
       │      ├── V1
       │      └── V2
       │
       ├── App Release
       │      ├── R1 → Workflow V10 + Contract V1
       │      ├── R2 → Workflow V11 + Contract V1
       │      └── R3 → Workflow V15 + Contract V2
       │
       ├── Channel
       │      ├── API
       │      ├── DIRECT_WEB
       │      ├── EMBED
       │      ├── WIDGET
       │      └── H5
       │
       ├── Experience
       │      ├── web-full
       │      ├── crm-embed
       │      ├── mobile
       │      └── support-widget
       │
       └── Grant
              ├── legacy-erp → API → R1 PINNED
              ├── legacy-crm → EMBED → R2 PINNED → crm-embed
              └── mall-v2 → WIDGET/API → STABLE → support-widget
```

---

# 5. Consumer Client

## 5.1 定义

Consumer Client 表示租户名下一个独立消费项目或系统：

```text
legacy-erp
legacy-crm
mall-v2
customer-service
mobile-app
operation-console
```

不要直接叫 Project，避免未来消费主体并不是真正代码项目。

## 5.2 Client 与 API Key

身份链：

```text
API Key
  ↓
Client
  ↓
Tenant
```

一个 Client 可以拥有多个 Key：

```text
legacy-erp
├── key-A ACTIVE
└── key-B ACTIVE
```

支持无停机轮换：

```text
创建 B
↓
老项目更新配置
↓
确认 B 已使用
↓
撤销 A
```

## 5.3 Client 状态

建议：

```text
ACTIVE
DISABLED
```

Client Disabled：

- 所有 Key 停止产生新授权；
- 禁止新 Launch Ticket；
- 禁止新 Execution；
- 已运行 Execution 默认继续；
- 管理员可 Force Cancel。

---

# 6. Workflow App

Workflow App 是稳定能力入口，而不是一次分享链接。

建议 URL/API 使用稳定：

```text
appCode
```

例如：

```text
order-assistant
customer-copilot
product-recommendation
```

App 保存：

```text
tenantId
appCode
appName
description
status
stableReleaseId
latestReleaseId
```

不要让 App 自己只有一个 `currentReleaseId`，因为不同 Client 可能需要不同 Release。

---

# 7. Consumer Contract

## 7.1 Contract 是长期兼容边界

Workflow Version 表示：

> 内部实现版本。

Contract Version 表示：

> Consumer 可以依赖的公开接口版本。

例如：

```text
Workflow V10
Workflow V11
Workflow V12
Workflow V15

都可以继续实现：

Contract V1
```

这样老 ERP 不需要升级。

## 7.2 Contract 内容

建议包含：

```text
Input Schema
Output Schema
Event Schema
Error Schema
Asset Schema
```

Contract 只描述 Consumer 能看到和依赖的稳定协议，不保存针对某个 Workflow Version 的 Adapter。

Adapter 属于 Release，因为同一个 `Contract V1` 可以由多个不同 Workflow Version 实现，而每个内部版本对应的字段映射可能不同。

## 7.3 Input Contract

只允许业务输入：

```json
{
  "type": "object",
  "properties": {
    "userName": {"type": "string"},
    "question": {"type": "string"}
  },
  "required": ["question"]
}
```

绝不允许 Consumer 指定：

```text
tenantId
clientId
grantId
releaseId
workflowVersionId
connectorId
datasourceId
knowledgeBaseId
resourceId
objectKey
systemPrompt
```

## 7.4 Output Contract

内部 Workflow 即使产生：

```text
tokenUsage
debugInfo
sqlResult
prompt
resourceId
privateUrl
checkpoint
```

只要不在 Output Contract 中，就绝不能进入：

- API response；
- SSE；
- iframe postMessage；
- Widget；
- Webhook。

---

# 8. Contract Adapter

这是支持“老项目不可升级”的核心能力，但它的**归属必须是 App Release，而不是 Contract**。

原因：

```text
Contract V1
   ├── Release R1 → Workflow V10 → Adapter A
   ├── Release R2 → Workflow V12 → Adapter B
   └── Release R3 → Workflow V15 → Adapter C
```

外部 Contract 可以完全不变，但内部 Workflow 的 canonical input/output 已经发生变化。若把 Adapter 固化到 Contract，会导致更新内部 Workflow 时要么偷偷修改不可变 Contract，要么被迫创建没有必要的 Contract V2。

因此每个 Release 冻结：

```text
contractId
requestAdapter
responseAdapter
eventAdapter（如需要）
errorAdapter（如需要）
adapterHash
```

例如老 ERP 请求：

```json
{
  "userName": "张三",
  "question": "查订单"
}
```

新 Workflow 需要：

```json
{
  "user": {
    "name": "张三"
  },
  "message": "查订单"
}
```

则：

```text
Legacy Request
↓
Request Adapter
↓
Canonical Workflow Input
↓
Workflow V2
↓
Canonical Workflow Output
↓
Response Adapter
↓
Legacy Response
```

Adapter 不允许任意 JavaScript。

允许受限 DSL：

```text
JSON Pointer
JSONPath（受限）
字段 Rename
Nest / Flatten
Default Value
Enum Mapping
类型安全转换
模板字符串（受限）
```

禁止：

```text
eval
JavaScript
Groovy
SpEL 任意 Bean 调用
HTTP Request
SQL
文件访问
任意 Class Reflection
```

Adapter 必须：

- 发布时编译；
- 有最大执行步数；
- 有最大递归深度；
- 有最大输出大小；
- 无网络能力；
- 无资源访问能力。

---

# 9. Breaking Change 检查

发布新 Contract / Release 时必须检查：

```text
删除 input 字段
新增 required 字段
修改字段 type
缩小 enum
修改 format
删除 output 字段
修改 Event type
修改 Error code
修改 Asset contract
```

若影响当前 Contract：

```text
BREAKING
```

禁止直接进入：

```text
STABLE
```

应该：

```text
创建 Contract V2
```

对于非 Breaking：

```text
新增 optional input
新增 output 字段（旧 Consumer 不依赖）
扩大 enum（需检查旧客户端是否严格反序列化）
```

仍应输出 Compatibility Report，而不是盲目认为安全。

---

# 10. App Release

App Release 是不可变发布快照。

冻结：

```text
workflowVersionId
workflowContentHash
contractId
requestAdapter
responseAdapter
eventAdapter（可选）
errorAdapter（可选）
adapterHash
capabilitySnapshot
resourceBindingSnapshot（非敏感）
securityPolicySnapshot
compilerVersion
releaseContentHash
```

`releaseContentHash` 至少覆盖：

```text
workflowVersionId
workflowContentHash
contractId
所有 Adapter
Capability Snapshot
Policy Snapshot
Compiler Version
```

这样同一个 Release 的实际执行语义可以被完整追溯。

Release 不保存任何 Secret。

## 10.1 Release Policy

Grant 支持：

```text
PINNED
FOLLOW_STABLE
FOLLOW_LATEST
```

后续可扩展：

```text
CANARY
```

推荐：

- 老系统默认 `PINNED`；
- 正常生产系统默认 `FOLLOW_STABLE`；
- 测试项目才允许 `FOLLOW_LATEST`。

## 10.2 STABLE 切换必须原子

切换 `stableReleaseId` 前：

1. Compiler PASSED；
2. Contract Compatibility PASSED；
3. Experience Compatibility PASSED；
4. 资源可用性检查通过；
5. 进行单条数据库原子指针切换。

不得部分更新。

---

# 11. App Grant

Grant 表达：

> 哪个 Client，可以通过哪些 Channel，使用哪个 App，以什么 Release Policy、Contract、额度、Origin 和 Experience 运行。

核心属性：

```text
tenantId
appId
consumerClientId
status
releasePolicy
pinnedReleaseId
contractVersion
allowedChannels
originPolicy
rateLimit
quota
concurrency
tokenBudget
assetPolicy
webhookPolicy
publicEntryCode（浏览器 Channel 的公开定位符，不是授权凭据）
```

必须满足：

```text
Client.tenantId
=
App.tenantId
=
Grant.tenantId
```

默认不支持跨租户 Grant。

`publicEntryCode` 仅用于浏览器 Shell 在首个 HTTP 请求时定位 Grant/Channel，从而生成正确的 CSP `frame-ancestors`；它即使泄露也不能创建 Session、执行 Workflow 或读取结果。

iframe 推荐 URL：

```text
/embed/{appCode}/{publicEntryCode}
```

URL 中仍然不得放：

```text
API Key
Launch Ticket
Browser Session Token
长期签名
```

未来如果需要 Marketplace / 跨租户能力，必须单独设计，不复用本方案的同租户假设。

---

# 12. Channel 模型

正式支持：

```text
API
DIRECT_WEB
EMBED
WIDGET
H5
```

SDK 不作为独立 Runtime Channel。

Java / Go / Python / JS SDK 只是 API 的开发体验包装。

## 12.1 API

适合：

- ERP；
- CRM Backend；
- 定时任务；
- 服务间调用。

认证：

```text
API Key
↓
Client
↓
Grant
```

不需要：

- Browser Session；
- Ticket；
- Origin；
- CAPTCHA。

## 12.2 DIRECT_WEB

Polaris 自己提供完整页面。

适合：

- 老系统点击按钮后跳转；
- 独立 AI 工作台；
- 运营页面；
- 报告页。

URL 只是 App Shell：

```text
/apps/{appCode}
```

无 Browser Session 时不能获取完整 Manifest 和业务数据。

DIRECT_WEB 建议支持两种认证来源：

```text
CLIENT_LAUNCH
→ 业务项目 Backend 使用 API Key 签发一次性 Ticket

PLATFORM_SSO
→ 已登录 Polaris 中台用户，通过租户身份 + Client/Grant 映射进入
```

核心场景不提供：

```text
PUBLIC
PASSWORD_ONLY
```

避免又回到“知道链接即可访问”的匿名分享模型。

## 12.3 EMBED

通过 iframe 嵌入旧系统。

使用：

```text
API Key → Embed Ticket → Browser Session
```

必须绑定：

```text
clientId
appId
grantId
origin
```

## 12.4 WIDGET

例如：

```html
<script src="/polaris-widget.js"></script>
```

Widget JS 只负责：

- launcher；
- 容器；
- ticket 协议；
- resize；
- host event bridge。

真正业务 UI 默认仍放：

```text
Secure iframe
```

避免：

- 宿主 CSS 污染；
- Vue 版本冲突；
- 全局 JS 冲突；
- Widget 获得宿主页完整 DOM 权限。

## 12.5 H5 / WebView

安全模型接近 DIRECT_WEB：

```text
Native / H5 Backend
↓
API Key
↓
Launch Ticket
↓
Browser Session
```

Native Client Secret 不能硬编码在 App 包中充当后端 API Key。

---

# 13. 可信 Consumer Context 与数据权限

仅有 `userRef` 还不足以覆盖企业数据权限场景。

例如 CRM 页面调用 Workflow 时，可能还需要可信声明：

```text
employeeId
departmentId
organizationId
storeId
customerScope
roleCodes
```

这些字段如果放在普通 `$input`，浏览器就可以篡改，不能用于授权。

因此建议增加独立的：

```text
Consumer Context / Actor Claims
```

它和业务 Input 分离：

```text
Business Input
→ 用户可以填写，不能作为权限事实

Trusted Context
→ 由 API Key 所属 Backend 或 Platform SSO 产生，可用于数据权限
```

Server-to-Server API 可以在受限 Envelope 中提交 Context Claims；服务端根据 Grant 的 `contextSchema` 校验允许字段。

Browser 渠道则在签发 Ticket 时把 Context Claims 固化到 Ticket / Browser Session，浏览器后续不能覆盖。

最终写入：

```text
principal_snapshot
```

但仍不保存任何 Secret。

Datasource、Connector、RAG 或自定义业务节点如果需要行级/组织级权限，应读取服务端可信 Context，而不是 `$input.departmentId` 这类普通输入。

Grant 应可配置：

```text
contextSchema
allowedClaimKeys
requiredClaimKeys
```

避免某 Client 任意伪造平台内部安全字段。

---

# 14. 浏览器安全授权链

## 14.1 Launch / Embed Ticket

Consumer Client Backend：

```http
POST /platform/v1/apps/{appCode}/launch-tickets
Authorization: Bearer sk_xxx
```

服务器验证：

```text
API Key
↓
Tenant
↓
Client
↓
Grant
↓
Channel
↓
Origin
↓
Release
```

Ticket：

```text
TTL 30～60 秒
一次性
高熵随机值
```

绑定：

```text
tenantId
clientId
appId
grantId
channel
origin
userRef(optional)
nonce
expiresAt
```

Ticket Exchange 成功：

```text
立即删除 / 标记 used
```

## 14.2 userRef 安全

`userRef` 用于业务审计与会话恢复，例如：

```text
employee_10086
crm_user_8932
```

它必须由：

```text
Consumer Client Backend
```

写入 Ticket。

浏览器直接提交的 `userRef` 不可信。

`userRef` 不是权限依据，只能作为受信 Client 声明的业务身份映射。

## 14.3 Browser Session

绑定：

```text
tenantId
clientId
appId
grantId
channel
releaseId
contractId
experienceVersionId
origin
userRef
```

建议：

```text
Idle TTL：10～30 min
Absolute TTL：2～8 h
```

Direct Web 优先使用：

```text
HttpOnly + Secure Cookie
```

iframe 模式避免依赖第三方 Cookie，可使用：

```text
短期 Bearer Token
```

且仅保存在 iframe JS 内存。

## 14.4 页面 URL 不放凭据

禁止：

```text
?apiKey=
?session=
?sign=
?ticket=长期值
?password=
```

如果 Direct Web 需要跳转，优先：

```text
POST Ticket Exchange
↓
Set-Cookie
↓
303 Redirect
↓
干净 URL
```

---

# 15. 页面 Shell 与业务能力分离

`GET /apps/{appCode}` 只加载：

```text
静态 Shell
应用名称（可选）
Logo（可选）
正在验证授权
```

无 Session 不返回：

- 完整 Contract；
- Experience Manifest；
- Release ID；
- Workflow 信息；
- Resource 信息；
- Asset Policy 细节；
- Execution 数据。

真正业务 API 必须重新鉴权。

---

# 16. Experience：页面形态成为独立领域模型

页面不能固定成：

```text
FORM
CHAT
TRYON
REPORT
DASHBOARD
```

这些只作为 Designer 模板。

正式模型：

```text
Workflow App
├── Experience: web-full
├── Experience: crm-embed
├── Experience: mobile
└── Experience: support-widget
```

同一个 App Release 可以被多个 Experience 使用。

Experience 与 Workflow Release 分离：

```text
Workflow Release
→ 能力与 Contract

Experience
→ 页面结构与交互
```

因此换布局不必复制 Workflow App。

---

# 17. Experience Version

Experience 必须版本化。

```text
Experience
├── V1
├── V2
└── V3
```

Experience Version 发布后不可变。

冻结：

```text
layoutManifest
interactionManifest
themeManifest
widgetDependencies
compatibleContractRange
contentHash
compilerVersion
```

Grant 可以选择：

```text
PINNED Experience Version
FOLLOW_STABLE Experience
```

特别是老项目 iframe，建议同时 Pin：

```text
Release
+
Experience Version
```

避免页面布局突然变化。

---

# 18. 动态 Layout DSL

## 18.1 不使用固定 Slot

不再固定：

```text
Action
Canvas
Assistant
```

改成：

```text
Layout Tree
```

例如：

```text
Page
├── Header
├── SplitPane
│   ├── Sidebar
│   │   └── SchemaForm
│   └── Main
│       ├── ImageCompare
│       └── ResultGallery
└── Floating
    └── ChatStream
```

另一业务可以：

```text
Page
├── FilterBar
├── Grid
│   ├── KPI
│   ├── KPI
│   └── KPI
├── Grid
│   ├── LineChart
│   └── BarChart
└── MarkdownReport
```

Renderer 不需要知道业务属于什么类型。

## 18.2 Layout Primitive

建议第一阶段提供：

```text
Page
Container
Row
Column
Stack
Flex
Grid
SplitPane
Sidebar
Header
Footer
Card
Panel
Tabs
Tab
Accordion
Stepper
Drawer
Dialog
Overlay
Floating
Sticky
ScrollArea
```

这些只负责布局，不负责业务逻辑。

## 18.3 Widget Registry

输入：

```text
TextInput
TextArea
NumberInput
Select
MultiSelect
Radio
Checkbox
DatePicker
DateRange
Slider
SchemaForm
MediaDropzone
FileUpload
```

AI：

```text
ChatStream
ChatHistory
PromptInput
SuggestedQuestions
ExecutionProgress
StatusCard
```

媒体：

```text
ImageViewer
ImageCompare
ImageGallery
VideoPlayer
AudioPlayer
MediaPreview
```

数据：

```text
Table
DataGrid
Statistic
KeyValue
Timeline
LineChart
BarChart
PieChart
RadarChart
Gauge
```

文档：

```text
Markdown
CodeBlock
DocumentViewer
FileDownload
```

操作：

```text
Button
ButtonGroup
Toolbar
ActionList
ResultCard
```

Widget 必须来自固定 Registry。

禁止 Manifest 指定：

```text
Vue Component Path
JavaScript
HTML
Remote Module
CDN Script
任意 iframe URL
```

---

# 19. Layout Manifest 示例

```json
{
  "schemaVersion": "1.0",
  "root": {
    "type": "Page",
    "children": [
      {
        "type": "Grid",
        "props": {
          "columns": {
            "desktop": "360px 1fr 320px",
            "tablet": "320px 1fr",
            "mobile": "1fr"
          },
          "gap": 16
        },
        "children": [
          {
            "type": "Card",
            "children": [
              {
                "widget": "MediaDropzone",
                "bind": "$input.photo"
              }
            ]
          },
          {
            "type": "Card",
            "children": [
              {
                "widget": "ImageCompare",
                "props": {
                  "before": "$input.photo",
                  "after": "$output.resultImage"
                }
              }
            ]
          },
          {
            "type": "Card",
            "responsive": {
              "mobile": {
                "presentation": "drawer"
              }
            },
            "children": [
              {
                "widget": "ChatStream",
                "bind": "$conversation"
              }
            ]
          }
        ]
      }
    ]
  }
}
```

---

# 20. Layout DSL 必须有复杂度上限

动态布局本身会形成新的攻击面和性能风险。

必须限制：

```text
最大节点数
最大树深度
最大 children 数
最大 Tabs 数
最大 Grid 列数
最大 Manifest 字节数
最大表达式长度
最大 Action 数
```

建议起始值：

```text
Manifest <= 256 KB
Layout Node <= 300
Tree Depth <= 20
单节点 children <= 50
单事件 Action <= 20
```

发布时拒绝：

- 环引用；
- 非法 Binding；
- 不存在 Widget；
- 未声明 Widget Version；
- 超复杂表达式；
- 无界递归结构。

避免把 Layout DSL 演化成另一个不可控低代码执行平台。

---

# 21. 数据绑定与状态模型

允许绑定：

```text
$input
$output
$execution
$conversation
$ui
$context.public
```

其中：

```text
$ui
```

只允许 Experience 自己的临时展示状态，例如：

```text
currentTab
drawerOpen
selectedResult
```

禁止访问：

```text
$tenant.internal
$grant.secret
$connector
$datasource
$resource
$server
```

Binding 必须在发布时根据 Contract 静态检查。

---

# 22. 条件显示 DSL

允许：

```text
eq
neq
exists
notExists
gt
gte
lt
lte
in
notIn
and
or
not
```

示例：

```json
{
  "visibleWhen": {
    "path": "$execution.status",
    "operator": "eq",
    "value": "SUCCEEDED"
  }
}
```

禁止任何：

```text
eval
function
script
spel bean access
```

---

# 23. Interaction Manifest

页面交互通过受限 Action DSL：

```text
SET_VALUE
RESET_FORM
START_EXECUTION
CANCEL_EXECUTION
OPEN_DRAWER
CLOSE_DRAWER
OPEN_DIALOG
CLOSE_DIALOG
SWITCH_TAB
NAVIGATE_INTERNAL
DOWNLOAD_ASSET
COPY_TEXT
EMIT_HOST_EVENT
```

禁止：

```text
EXEC_SCRIPT
EVAL
HTTP_REQUEST
LOAD_SCRIPT
SQL
READ_COOKIE
READ_LOCAL_STORAGE arbitrary
```

例如：

```json
{
  "on": "click",
  "actions": [
    {
      "type": "SET_VALUE",
      "target": "$input.style",
      "value": "casual"
    },
    {
      "type": "START_EXECUTION"
    }
  ]
}
```

所有 Server Action 仍必须重新鉴权，不能相信前端 Manifest 已经检查。

---

# 24. Host Event

iframe / Widget 可以向宿主发业务事件，例如：

```text
OPEN_ORDER
ADD_PRODUCT
CLOSE_ASSISTANT
SELECT_CUSTOMER
```

Experience 发布时声明：

```json
{
  "allowedHostEvents": [
    "OPEN_ORDER",
    "CLOSE_ASSISTANT"
  ]
}
```

`postMessage` 必须校验：

```text
event.origin
event.source
protocol
channelId
message schema
requestId
```

禁止：

```javascript
postMessage(data, "*")
```

Host Event payload 仍只能来自 Consumer Contract 的公开字段。

---

# 25. Theme Manifest

Theme 与 Layout 分离。

允许有限 Design Token：

```text
primary
secondary
background
surface
textPrimary
textSecondary
border
radius
fontSize
spacing
shadow
```

禁止：

- 任意 `<style>`；
- `@import`；
- 自定义 JS；
- 任意 CSS URL；
- 可执行 CSS 表达式；
- 未校验的字体地址。

这样同一个 Layout 可以被不同 Client 复用不同品牌样式。

---

# 26. Experience 与 Contract 兼容性

Experience Version 必须声明：

```text
compatibleContractRange
```

发布 Release 或切换 Contract 时：

```text
Experience Bindings
↓
Contract Schema
↓
Compatibility Check
```

如果页面引用：

```text
$output.resultImage
```

新 Contract 删除该字段：

```text
BLOCKER
```

不能让页面运行时才白屏。

---

# 27. Experience Designer

管理后台不要要求用户手写 Manifest。

建议低代码设计器：

```text
┌───────────────────────────────────────────────┐
│ Components │          Canvas        │ Config │
│            │                        │        │
│ Layout     │  ┌─────────────────┐   │ Props  │
│ Grid       │  │ Header          │   │ Bind   │
│ Card       │  ├───────┬─────────┤   │ Event  │
│ Tabs       │  │ Form  │ Result  │   │ Show   │
│ Drawer     │  ├───────┴─────────┤   │ Resp.  │
│            │  │ Assistant       │   │ Theme  │
│ Widgets    │  └─────────────────┘   │        │
└───────────────────────────────────────────────┘
```

模板只用于创建初始布局：

```text
Blank
Form
Chat
Two Columns
Three Columns
Image Workspace
Dashboard
Report
Mobile Stepper
Copilot Widget
```

选择模板后可继续自由组合。

运行时不存在 `layout=CHAT` 这种业务枚举。

---

# 28. Preview 与发布检查

必须支持：

```text
Desktop
Tablet
Mobile
480px iframe
800px iframe
```

Mock 状态：

```text
Empty
Input Ready
Running
Waiting
Succeeded
Failed
Cancelled
```

发布时 Experience Compiler 检查：

- Layout Schema；
- Widget 存在；
- Widget Version；
- Binding Path；
- Contract 字段；
- Action 类型；
- Host Event；
- Responsive；
- Theme Token；
- Asset Reference；
- 节点复杂度；
- Manifest Size。

---

# 29. iframe / Widget 安全

## 29.1 CSP

服务端根据 Grant 动态返回：

```http
Content-Security-Policy:
frame-ancestors https://crm.example.com
```

不能只靠前端 JS。

## 29.2 postMessage

统一协议：

```json
{
  "protocol": "POLARIS_APP_V1",
  "channelId": "random",
  "requestId": "uuid",
  "type": "LAUNCH",
  "payload": {}
}
```

接收端检查：

```text
event.origin
event.source
channelId
protocol
message schema
```

## 29.3 Allowed Origin

Origin 精确匹配：

```text
scheme + host + port
```

禁止模糊：

```text
*.example.com
```

作为 MVP 默认行为。

若后续支持通配符，必须使用严格域名匹配库，禁止字符串 `endsWith`。

---

# 30. Web 页面安全 Header

## 30.1 Runtime Origin 与管理后台 Origin 隔离

生产环境强烈建议将动态 Runtime 页面和管理后台放到不同 Origin，例如：

```text
console.example.com   → Polaris 管理后台
app.example.com       → DIRECT_WEB / EMBED / WIDGET / H5 Runtime
api.example.com       → 可选 OpenAPI Gateway
```

至少保证：

- 管理后台 Cookie 不发送到 Runtime Origin；
- Runtime Cookie 不发送到 Console Origin；
- Runtime XSS 即使发生，也不能直接继承管理后台同源权限；
- `connect-src` 只允许指定 API Origin；
- Runtime 禁止注册覆盖管理后台范围的 Service Worker；
- iframe / Widget 优先使用 Runtime 专用 Origin。

如果部署条件暂时只能共用域名，至少使用不同子域而不是单纯不同 path。

## 30.2 安全 Header

建议：

```http
X-Content-Type-Options: nosniff
Referrer-Policy: no-referrer
X-Robots-Tag: noindex, nofollow, noarchive
Cache-Control: no-store
Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()
```

需要相机/麦克风的 Experience 单独声明并审核。

Direct Web 建议 CSP：

```text
default-src 'self'
script-src 'self'
style-src 'self'
img-src 'self' blob: https:
connect-src 'self'
object-src 'none'
base-uri 'none'
form-action 'self'
frame-ancestors 'self'
```

Embed 的 `frame-ancestors` 由 Grant 生成。

---

# 31. XSS 与 AI 输出安全

全部视为不可信：

- LLM Markdown；
- Workflow 字符串输出；
- App description；
- Contract description；
- Chart label；
- 文件名；
- Host Event 内容；
- Connector 返回文本。

Markdown：

```text
Markdown Parser
↓
Sanitizer
↓
Strict Allowlist
↓
Renderer
```

禁止直接：

```vue
<div v-html="rawOutput" />
```

URL protocol 默认只允许：

```text
https:
http:
```

特定 Widget 可允许：

```text
blob:
```

禁止：

```text
javascript:
data:text/html
file:
```

外链建议增加：

```text
rel="noopener noreferrer"
```

对于业务敏感页面可以增加“外部链接确认”。

---

# 32. Asset 安全

## 32.1 上传

推荐：

```text
POST asset/init
↓
assetId + Signed Upload URL
↓
Object Storage
↓
POST asset/complete
↓
Metadata Check
Magic Number
MIME Check
Decode
Pixel Limit
EXIF Cleanup
Optional Malware Scan
Re-encode
↓
READY
```

## 32.2 Ownership

Server-to-Server Asset：

```text
tenantId
clientId
appId
grantId
```

Browser Asset 额外绑定：

```text
browserSessionId
```

## 32.3 主动内容

默认禁止直接 inline 展示：

```text
SVG
HTML
XML
```

SVG 可能包含脚本和外部引用。

若业务必须支持 SVG：

- 服务端严格 sanitizer；
- 禁止 script；
- 禁止 foreignObject；
- 禁止外部 URL；
- 最好栅格化后展示。

PDF / Office 等复杂文档：

- 下载优先 `Content-Disposition: attachment`；
- 预览使用隔离 Viewer；
- 不允许把不可信文件直接作为同源 HTML 打开。

## 32.4 输出文件

只返回：

```text
assetId
```

下载时：

```text
Session / API Key
↓
Grant
↓
Asset Ownership
↓
Signed Download URL
```

Signed URL 建议：

```text
5～15 min
```

---

# 33. Publish Compiler

发布流程：

```text
Workflow Version
       │
       ▼
Application Publish Compiler
       │
       ├── Contract Compile
       ├── Release Adapter Compile
       ├── DAG Capability Scan
       ├── Resource Permission Check
       ├── SubWorkflow Recursive Closure
       ├── Agent Tool Recursive Scan
       ├── Output Leak Check
       ├── Event Projection Check
       ├── Breaking Change Check
       ├── Security Policy Snapshot
       └── Experience Compatibility Check
       │
       ▼
Validation Report
       │
       ├── BLOCKER
       ├── WARNING
       └── PASSED
```

BLOCKER 禁止 Release。

---

# 34. Capability Snapshot

保留 Capability Manifest，但定位为：

> Release 的能力审计与变更 Diff 快照，而不是另一套平行权限系统。

例如：

```json
{
  "llm": true,
  "knowledgeBases": ["kb_public_xxx"],
  "connectors": ["conn_xxx"],
  "datasources": ["ds_report_readonly"],
  "agentTools": ["tool_xxx"],
  "subWorkflows": ["wf_xxx"],
  "maxLoopIterations": 10,
  "maxSubWorkflowDepth": 3
}
```

运行时主授权仍是：

```text
Tenant ownership
+
Client
+
API Key permissions
+
Grant
+
Workflow Resource Binding
```

---

# 35. SubWorkflow 与 Agent Tool 闭包

发布时不能只检查顶层 Workflow。

必须递归：

```text
Workflow
↓
SubWorkflow
↓
SubWorkflow 的 Connector / Datasource / Agent Tool
```

需要形成：

```text
Capability Closure
```

特别注意：

> SubWorkflow 节点必须最终绑定确定版本或确定的不可变调用语义。

如果执行时通过 `workflowCode -> latest` 动态解析，则会破坏 App Release 不可变性。

因此 Release Compiler 应确保：

```text
subWorkflowVersionId
```

被固定进最终执行计划或可验证快照。

---

# 36. Approval / Wait 节点重新定义

因为消费方不是匿名用户，`Approval` 不应再一律 FORBIDDEN。

建议：

```text
Wait
→ ALLOW，限制最大等待/总生命周期

Approval
→ REVIEW
```

默认行为：

- API / 页面只能看到 `WAITING_APPROVAL`；
- 审批动作仍在 Polaris 管理/中台完成；
- 如果未来允许 Consumer 页面审批，必须额外增加：
  - `workflow:approve` scope；
  - 审批人身份映射；
  - 独立审批 Grant；
  - 强审计；
  - 不允许仅凭普通 App Browser Session 审批。

---

# 37. Runtime 授权链

API：

```text
API Key
↓
Resolve Tenant + Client
↓
API Key Scope
↓
Resolve App
↓
Grant Check
↓
Resolve Release
↓
Contract Validation
↓
Release Adapter
↓
Workflow Resource Permission
↓
Workflow Execution
```

Browser：

```text
Browser Session
↓
Resolve Tenant + Client + Grant
↓
Channel Check
↓
Grant Revision Check
↓
Resolve Release / Experience
↓
Contract Validation
↓
Workflow Execution
```

不得信任请求中的：

```text
tenantId
clientId
appId internal id
grantId
releaseId
workflowVersionId
connectorId
datasourceId
knowledgeBaseId
objectKey
```

---

# 38. Grant Revision 与即时撤权

只靠 Browser Session TTL 会产生撤权窗口：

```text
Grant 已禁用
但 Session 还能继续 30 分钟
```

建议 Grant 增加：

```text
revision
```

Session 保存：

```text
grantRevision
```

每次高权限操作至少检查：

```text
Grant.status
Grant.revision
```

可以使用 1～5 秒短缓存降低数据库压力。

当：

```text
Grant Disable
Origin 变化
Channel 变化
Release Pin 变化
Quota Policy 高风险变化
```

递增：

```text
revision
```

旧 Session 立即失效或要求重新 Exchange。

---

# 39. 执行 API

建议路径：

```text
/platform/v1/apps/{appCode}/...
```

## 39.1 Sync Invoke

```http
POST /platform/v1/apps/{appCode}/invoke
Authorization: Bearer sk_xxx
Idempotency-Key: xxx
```

适合短任务。

服务端最多同步等待配置时间，例如：

```text
20～30 秒
```

若未完成：

```http
202 Accepted
```

返回：

```json
{
  "executionId": "exec_xxx",
  "status": "RUNNING"
}
```

HTTP 断开不能自动取消 Workflow。

## 39.2 Async Execution

```http
POST /platform/v1/apps/{appCode}/executions
```

返回：

```json
{
  "executionId": "exec_xxx",
  "status": "QUEUED"
}
```

## 39.3 Status

```http
GET /platform/v1/apps/{appCode}/executions/{executionId}
```

必须检查 Invocation Ownership。

## 39.4 SSE

```http
GET /platform/v1/apps/{appCode}/executions/{executionId}/events
```

API 模式使用 Authorization Header。

Browser 模式使用 Browser Session。

## 39.5 Cancel

```http
POST /platform/v1/apps/{appCode}/executions/{executionId}/cancel
```

仍调用现有 Workflow V2 Cancel。

---

# 40. 幂等设计

当前 `ai_workflow_execution` 已经存在：

```text
UNIQUE(idempotency_scope, idempotency_key)
```

该唯一约束跟随 Execution 历史长期存在，因此不能把 Consumer 提供的外部 `Idempotency-Key` 原样透传进去，再声称外部 Key 只保留 24h/7d；否则外部幂等记录虽然过期，Workflow 表里的唯一键仍会永久占用该 Key。

因此最终采用“两层幂等”：

```text
Consumer Idempotency
        ↓
ai_workflow_app_idempotency
        ↓
生成 invocationId / internalExecutionKey
        ↓
Workflow V2 Idempotency
```

### 外部幂等 Scope

```text
tenant:{tenantId}:client:{clientId}:app:{appId}:contract:{contractId}
```

保存：

```text
externalIdempotencyKey
requestFingerprint
invocationId
internalExecutionKey
executionId
expireAt
```

`requestFingerprint`：

```text
SHA-256(canonical request body + 影响语义的必要 header/context)
```

逻辑：

```text
Key 不存在
→ 原子占位
→ 生成 invocationId
→ internalExecutionKey = "appinv:" + invocationId
→ 启动 Workflow

Key 存在 + fingerprint 相同
→ 返回原 invocation / execution

Key 存在 + fingerprint 不同
→ 409 IDEMPOTENCY_KEY_REUSED
```

Workflow V2 内部使用：

```text
idempotency_scope = app-internal:{tenantId}:{appId}:{clientId}
idempotency_key   = appinv:{invocationId}
```

如果 App Gateway 在启动 Workflow 前后发生网络超时，重试必须复用同一个 `internalExecutionKey`，从而借助现有 Workflow 唯一约束避免重复创建 Execution。

外部幂等窗口可以是：

```text
24h / 7d / configurable
```

清理 `ai_workflow_app_idempotency` 后，未来 Consumer 即使再次使用相同外部 Key，也会得到新的 `invocationId` 和新的内部 Key，不会与历史 Workflow Execution 的永久唯一约束冲突。

---

# 41. Invocation Mapping 与 Ownership

不再绑定 `Share Session` 作为长期所有权依据。

保存：

```text
tenantId
clientId
apiKeyId(optional)
appId
grantId
releaseId
contractId
experienceVersionId(optional)
executionId
conversationId
userRef(optional)
idempotencyKey
requestFingerprint
channel
```

Ownership 校验至少：

```text
executionId
+
tenantId
+
clientId
+
appId
+
grantId
```

浏览器 Session 只是当前访问凭据，不是 Execution 永久所有者。

---

# 42. 长任务与 Browser Session 过期

这是页面模式非常容易漏掉的问题。

例如：

```text
Workflow 执行 2 小时
Browser Session 30 分钟过期
```

不能导致：

- Workflow 被误取消；
- 用户永远无法再次查看结果。

正确设计：

```text
Execution Ownership
=
Tenant + Client + App + Grant + userRef(optional)
```

重新进入页面时：

```text
Client Backend
↓
生成新 Ticket
↓
新 Browser Session
↓
根据 Ownership 重新 attach Execution
```

是否允许 attach 历史 Execution 由 Grant Policy 决定。

可以配置：

```text
NONE
CURRENT_USER
CURRENT_CLIENT
```

默认建议：

```text
CURRENT_USER（存在 userRef 时）
否则 CURRENT_CLIENT + explicit executionId
```

---

# 43. Conversation 所有权

Chat 场景不能只靠：

```text
conversationId
```

必须绑定：

```text
tenantId
clientId
appId
grantId
userRef(optional)
```

服务器生成 Conversation ID。

客户端不能提交任意他人的 Conversation ID 并接管上下文。

---

# 44. Event Projection

内部 Workflow Event 永远不能直接暴露。

```text
Workflow Event
↓
Consumer Event Projector
↓
API / SSE / Browser / Webhook
```

标准公共事件：

```text
connected
progress
chat_delta
widget_update
output_patch
waiting
completed
cancelled
error
heartbeat
```

内部禁止泄露：

```text
Prompt
SQL
Connector Endpoint
Tool Args
Resource ID
Secret
StackTrace
Checkpoint
Outbox
Internal Node Config
Model Raw Error
```

Event Contract 也必须版本化。

---

# 45. Webhook / Callback

异步 API 常常需要结果推送，因此增加“结果交付策略”，但它不是新 Channel。

Grant 可以配置：

```text
Webhook Endpoint
```

必须预注册，不能让每次请求传任意 callback URL。

防止 SSRF：

- 仅 HTTPS；
- 禁止 localhost；
- 禁止内网地址；
- DNS resolve 后再次检查 IP；
- 跟随 Redirect 时重新校验；
- 限制 redirect 次数；
- 可选域名 allowlist。

签名：

```text
X-Polaris-Timestamp
X-Polaris-Event-Id
X-Polaris-Signature
```

HMAC 覆盖：

```text
timestamp + eventId + rawBody
```

Consumer 必须检查：

- 时间窗口；
- Event ID 去重；
- HMAC；
- Body 完整性。

Webhook 需：

```text
指数退避
最大重试次数
DLQ / Failed Delivery Audit
```

Webhook Secret 必须支持双密钥轮换。

Webhook Secret 不能明文放入 `webhook_policy_json`。Grant 只保存：

```text
webhookSecretRef / secretVersion
```

实际 Secret 由平台安全凭据组件读取。

---

# 46. Rate Limit / Quota / Budget

不能只按 IP。

建议维度：

如果使用 IP 作为辅助维度，应用只能信任由受控 Nginx/Gateway 规范化后的真实客户端地址；不得直接信任客户端自带的 `X-Forwarded-For`。

```text
Tenant
Client
API Key
App
Grant
UserRef
Browser Session
Conversation
Execution Concurrency
Token
Asset Bytes
Webhook Delivery
```

示例：

```text
Tenant daily token
Client RPM
Grant daily execution
API Key RPM
Single execution token budget
Client concurrent execution
Asset daily bytes
```

限流配置优先级必须明确，例如：

```text
最终值 = min(Tenant, Client, API Key, Grant, App)
```

不要出现多个模块各算各的、结果不一致。

---

# 47. Redis 锁与并发

如果 Conversation 只能同时运行一个 Execution：

```text
wfapp:lock:{tenantId}:{clientId}:{appId}:{conversationId}
```

Value：

```text
executionId
```

必须：

- TTL；
- 心跳续租；
- Compare-And-Delete；
- 禁止无条件 DEL。

锁只是并发协调，不是 Execution 状态事实源。

Redis 故障时需要明确 fail-open / fail-closed 策略：

- 认证 Ticket：fail-closed；
- 高成本执行限流：建议 fail-closed 或降级额度；
- 非关键页面缓存：可 fail-open 到数据库。

---

# 48. 错误模型

统一稳定 Error Contract：

```json
{
  "code": "APP_EXECUTION_FAILED",
  "message": "执行失败，请稍后重试",
  "traceId": "tr_xxx"
}
```

禁止返回：

```text
StackTrace
SQL
内部 IP
Bean 名
Java 类名
Connector Endpoint
Prompt
Model API 原始错误
```

建议区分：

```text
APP_ACCESS_DENIED
APP_GRANT_DISABLED
APP_RELEASE_UNAVAILABLE
CONTRACT_VALIDATION_FAILED
IDEMPOTENCY_KEY_REUSED
EXECUTION_RATE_LIMITED
EXECUTION_BUDGET_EXCEEDED
ASSET_INVALID
EXECUTION_NOT_FOUND
```

但不要让错误码暴露“某个别人的 execution 存在”。

IDOR 场景可统一返回 404。

---

# 49. 数据库设计

> MySQL 8.0+。以下为建议结构，字段命名可按项目现有规范适配。

## 49.1 Consumer Client

```sql
CREATE TABLE `platform_consumer_client` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `client_code` VARCHAR(64) NOT NULL,
  `client_name` VARCHAR(128) NOT NULL,
  `client_type` VARCHAR(32) NOT NULL DEFAULT 'APPLICATION',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_code` (`tenant_id`, `client_code`),
  KEY `idx_client_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 49.2 API Key 增加 Client

迁移第一阶段：

```sql
ALTER TABLE `platform_api_key`
ADD COLUMN `client_id` BIGINT DEFAULT NULL COMMENT 'Consumer Client ID' AFTER `tenant_id`,
ADD KEY `idx_client` (`tenant_id`, `client_id`, `status`);
```

不要第一步直接 NOT NULL，避免现有 Key 全部失效。

完整迁移见后文。

## 49.3 Workflow App

```sql
CREATE TABLE `ai_workflow_app` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `workflow_definition_id` BIGINT NOT NULL COMMENT '绑定现有 ai_workflow_definition.id',
  `workflow_code` VARCHAR(64) NOT NULL COMMENT '工作流编码快照/展示',
  `app_code` VARCHAR(64) NOT NULL,
  `app_name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(1000) DEFAULT NULL,
  `stable_release_id` BIGINT DEFAULT NULL,
  `latest_release_id` BIGINT DEFAULT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  `lock_version` INT NOT NULL DEFAULT 0,
  `create_by` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_code` (`tenant_id`, `app_code`),
  KEY `idx_app_workflow` (`tenant_id`, `workflow_definition_id`, `workflow_code`),
  KEY `idx_app_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 49.4 Contract

```sql
CREATE TABLE `ai_workflow_app_contract` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `app_id` BIGINT NOT NULL,
  `contract_version` INT NOT NULL,
  `input_schema_json` JSON NOT NULL,
  `output_schema_json` JSON NOT NULL,
  `event_schema_json` JSON DEFAULT NULL,
  `error_schema_json` JSON DEFAULT NULL,
  `content_hash` CHAR(64) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
  `create_by` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_contract` (`app_id`, `contract_version`),
  UNIQUE KEY `uk_app_contract_hash` (`app_id`, `content_hash`),
  KEY `idx_contract_tenant_app` (`tenant_id`, `app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 49.5 Release

```sql
CREATE TABLE `ai_workflow_app_release` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `app_id` BIGINT NOT NULL,
  `release_no` INT NOT NULL,
  `workflow_version_id` VARCHAR(64) NOT NULL,
  `workflow_content_hash` CHAR(64) NOT NULL,
  `contract_id` BIGINT NOT NULL,
  `request_adapter_json` JSON DEFAULT NULL,
  `response_adapter_json` JSON DEFAULT NULL,
  `event_adapter_json` JSON DEFAULT NULL,
  `error_adapter_json` JSON DEFAULT NULL,
  `adapter_hash` CHAR(64) NOT NULL,
  `capability_snapshot_json` JSON NOT NULL,
  `policy_snapshot_json` JSON NOT NULL,
  `compiler_version` VARCHAR(32) NOT NULL,
  `release_content_hash` CHAR(64) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'RELEASED',
  `create_by` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_release` (`app_id`, `release_no`),
  UNIQUE KEY `uk_app_release_hash` (`app_id`, `release_content_hash`),
  KEY `idx_release_workflow_version` (`workflow_version_id`),
  KEY `idx_release_contract` (`contract_id`),
  KEY `idx_release_tenant_app` (`tenant_id`, `app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 49.6 Channel

```sql
CREATE TABLE `ai_workflow_app_channel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `app_id` BIGINT NOT NULL,
  `channel_type` VARCHAR(24) NOT NULL,
  `config_json` JSON DEFAULT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_channel` (`app_id`, `channel_type`),
  KEY `idx_channel_tenant_app` (`tenant_id`, `app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 49.7 Experience

```sql
CREATE TABLE `ai_workflow_app_experience` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `app_id` BIGINT NOT NULL,
  `experience_code` VARCHAR(64) NOT NULL,
  `experience_name` VARCHAR(128) NOT NULL,
  `stable_version_id` BIGINT DEFAULT NULL,
  `latest_version_id` BIGINT DEFAULT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_experience_code` (`app_id`, `experience_code`),
  KEY `idx_experience_tenant_app` (`tenant_id`, `app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 49.8 Experience Version

```sql
CREATE TABLE `ai_workflow_app_experience_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `experience_id` BIGINT NOT NULL,
  `version_no` INT NOT NULL,
  `layout_manifest_json` JSON NOT NULL,
  `interaction_manifest_json` JSON DEFAULT NULL,
  `theme_manifest_json` JSON DEFAULT NULL,
  `widget_dependencies_json` JSON DEFAULT NULL,
  `compatible_contract_json` JSON NOT NULL,
  `content_hash` CHAR(64) NOT NULL,
  `compiler_version` VARCHAR(32) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
  `create_by` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_experience_version` (`experience_id`, `version_no`),
  UNIQUE KEY `uk_experience_hash` (`experience_id`, `content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 49.9 Grant

```sql
CREATE TABLE `ai_workflow_app_grant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `app_id` BIGINT NOT NULL,
  `client_id` BIGINT NOT NULL,
  `public_entry_code` VARCHAR(64) NOT NULL COMMENT '浏览器Channel公开定位符，不作为认证凭据',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  `revision` BIGINT NOT NULL DEFAULT 1,
  `release_policy` VARCHAR(24) NOT NULL DEFAULT 'PINNED',
  `pinned_release_id` BIGINT DEFAULT NULL,
  `contract_id` BIGINT DEFAULT NULL,
  `allowed_channels_json` JSON NOT NULL,
  `channel_experience_json` JSON DEFAULT NULL,
  `allowed_origins_json` JSON DEFAULT NULL,
  `rate_policy_json` JSON DEFAULT NULL,
  `quota_policy_json` JSON DEFAULT NULL,
  `budget_policy_json` JSON DEFAULT NULL,
  `asset_policy_json` JSON DEFAULT NULL,
  `history_policy_json` JSON DEFAULT NULL,
  `webhook_policy_json` JSON DEFAULT NULL,
  `create_by` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_grant_client_app` (`client_id`, `app_id`),
  UNIQUE KEY `uk_grant_public_entry` (`public_entry_code`),
  KEY `idx_grant_tenant_client` (`tenant_id`, `client_id`, `status`),
  KEY `idx_grant_app` (`tenant_id`, `app_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 49.10 Invocation

```sql
CREATE TABLE `ai_workflow_app_invocation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `client_id` BIGINT NOT NULL,
  `api_key_id` BIGINT DEFAULT NULL,
  `app_id` BIGINT NOT NULL,
  `grant_id` BIGINT NOT NULL,
  `release_id` BIGINT NOT NULL,
  `contract_id` BIGINT NOT NULL,
  `experience_version_id` BIGINT DEFAULT NULL,
  `channel_type` VARCHAR(24) NOT NULL,
  `user_ref` VARCHAR(128) DEFAULT NULL,
  `conversation_id` VARCHAR(64) DEFAULT NULL,
  `execution_id` VARCHAR(64) NOT NULL,
  `idempotency_key` VARCHAR(128) DEFAULT NULL,
  `request_fingerprint` CHAR(64) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invocation_execution` (`execution_id`),
  KEY `idx_invocation_idempotency`
      (`tenant_id`, `client_id`, `app_id`, `contract_id`, `idempotency_key`),
  KEY `idx_invocation_client` (`tenant_id`, `client_id`, `create_time`),
  KEY `idx_invocation_grant` (`grant_id`, `create_time`),
  KEY `idx_invocation_conversation` (`app_id`, `client_id`, `conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Invocation 是审计记录，通常长期保留，因此**不要依赖 Invocation 唯一索引实现有限时间幂等窗口**。幂等状态单独建表：

```sql
CREATE TABLE `ai_workflow_app_idempotency` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scope_key` VARCHAR(256) NOT NULL,
  `external_idempotency_key` VARCHAR(128) NOT NULL,
  `request_fingerprint` CHAR(64) NOT NULL,
  `invocation_id` BIGINT DEFAULT NULL,
  `internal_execution_key` VARCHAR(128) NOT NULL,
  `execution_id` VARCHAR(64) DEFAULT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `expire_at` DATETIME NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_idempotency` (`scope_key`, `external_idempotency_key`),
  UNIQUE KEY `uk_app_internal_execution_key` (`internal_execution_key`),
  KEY `idx_app_idempotency_expire` (`expire_at`),
  CONSTRAINT `chk_app_idempotency_status` CHECK (`status` IN ('PENDING','STARTED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

过期清理该表不会影响 Invocation 审计历史。

## 49.11 Asset

```sql
CREATE TABLE `ai_workflow_app_asset` (
  `asset_id` VARCHAR(64) NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `client_id` BIGINT NOT NULL,
  `app_id` BIGINT NOT NULL,
  `grant_id` BIGINT NOT NULL,
  `browser_session_id` VARCHAR(64) DEFAULT NULL,
  `object_key` VARCHAR(512) NOT NULL,
  `original_name` VARCHAR(255) DEFAULT NULL,
  `mime_type` VARCHAR(128) NOT NULL,
  `size_bytes` BIGINT NOT NULL,
  `width` INT DEFAULT NULL,
  `height` INT DEFAULT NULL,
  `sha256` CHAR(64) DEFAULT NULL,
  `status` VARCHAR(16) NOT NULL,
  `reject_reason` VARCHAR(255) DEFAULT NULL,
  `expire_at` DATETIME NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`asset_id`),
  KEY `idx_asset_owner` (`tenant_id`, `client_id`, `app_id`, `status`),
  KEY `idx_asset_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---


## 49.12 数据一致性与租户不变量

新表大量冗余保存 `tenant_id` 是有意设计，用于每次查询都能带租户条件，而不是先按全局 ID 查询再二次判断。

所有 Service 写操作必须强制校验：

```text
Client.tenantId
=
App.tenantId
=
Grant.tenantId
=
Release.tenantId
=
Contract.tenantId
=
Invocation.tenantId
```

同时：

```text
Release.appId == App.id
Contract.appId == App.id
Grant.appId == App.id
Grant.clientId == Client.id
Experience.appId == App.id
```

不要信任请求参数里的 `tenantId/clientId` 来建立这些关系。

对于历史审计对象：

```text
API Key
Client
App
Grant
Release
Contract
Experience Version
```

优先使用 `DISABLED / REVOKED / RETIRED / ARCHIVED`，不要因为业务删除操作直接物理删除被 Invocation 引用的记录。

## 49.13 最终增量迁移文件建议

建议单独提交：

```text
sql/ai_workflow_app_v4_migration.sql
```

内容按顺序：

```text
1. CREATE platform_consumer_client
2. ALTER platform_api_key ADD client_id NULL
3. CREATE App/Contract/Release/Channel/Experience/Grant 表
4. CREATE Invocation/Idempotency/Asset 表
5. 为历史 Tenant 创建 legacy-default Client
6. 回填 platform_api_key.client_id
7. 创建必要索引
8. 数据校验报告
```

第一版上线**不要**立即把 `platform_api_key.client_id` 改成 NOT NULL；在兼容期结束、所有 Key 回填完成后再通过第二个增量版本收紧。

迁移脚本必须先在现有生产数据副本上做：

```text
执行时间评估
锁表评估
索引创建评估
回滚演练
行数/租户数/Key 数量前后核对
```

# 50. Redis Key

```text
wfapp:launch:{ticketId}
wfapp:browser:{sessionId}
wfapp:grant:{grantId}:rev
wfapp:rate:tenant:{tenantId}:{window}
wfapp:rate:client:{clientId}:{window}
wfapp:rate:key:{apiKeyId}:{window}
wfapp:rate:grant:{grantId}:{window}
wfapp:quota:daily:{grantId}:{yyyyMMdd}
wfapp:lock:{tenantId}:{clientId}:{appId}:{conversationId}
wfapp:manifest:release:{releaseId}
wfapp:experience:{experienceVersionId}
wfapp:webhook:dedup:{eventId}
```

Ticket / Session 必须 TTL。

缓存数据要区分：

```text
事实源：DB / Workflow Store
缓存：Redis
```

不能因为 Redis 丢失而造成权限扩大。

---

# 51. 前端架构

建议：

```text
polaris-ui-vue3/src/views/apps/
├── runtime/
│   ├── AppRuntime.vue
│   ├── ChannelBootstrap.ts
│   ├── useBrowserSession.ts
│   ├── useExecution.ts
│   ├── useExecutionStream.ts
│   ├── useAsset.ts
│   └── useHostBridge.ts
│
├── renderer/
│   ├── ExperienceRenderer.vue
│   ├── LayoutNode.vue
│   ├── WidgetHost.vue
│   ├── binding.ts
│   ├── conditions.ts
│   └── actions.ts
│
├── layout/
│   ├── GridLayout.vue
│   ├── FlexLayout.vue
│   ├── SplitPane.vue
│   ├── TabsLayout.vue
│   ├── DrawerLayout.vue
│   └── ...
│
├── widgets/
│   ├── SchemaFormWidget.vue
│   ├── ChatStreamWidget.vue
│   ├── MediaDropzoneWidget.vue
│   ├── ImageCompareWidget.vue
│   ├── DataGridWidget.vue
│   ├── ChartWidget.vue
│   ├── MarkdownWidget.vue
│   └── ...
│
├── registry/
│   ├── layoutRegistry.ts
│   ├── widgetRegistry.ts
│   └── actionRegistry.ts
│
├── security/
│   ├── sanitize.ts
│   ├── messageSchema.ts
│   └── urlPolicy.ts
│
└── designer/
    ├── ExperienceDesigner.vue
    ├── ComponentPalette.vue
    ├── Canvas.vue
    ├── PropertyPanel.vue
    └── Preview.vue
```

独立 Runtime 路由不得：

- 挂后台 Layout；
- 请求后台菜单；
- 复用后台 JWT；
- 暴露 Workflow 编辑能力。

---


## 51.1 与当前 Vue3 目录对齐

当前 `polaris-ui-vue3/src` 已经采用 `api / components / router / store / views` 等目录，因此不新增另一套前端工程。

推荐实际落位：

```text
src/api/workflowApp/
  app.js
  contract.js
  release.js
  grant.js
  experience.js
  runtime.js

src/views/workflowApp/
  management/
  designer/
  runtime/

src/components/workflow-app/
  renderer/
  layout/
  widgets/
  security/

src/router/
  增加 Runtime 公共路由
```

Runtime 页面：

- 不经过后台菜单权限加载；
- 不挂管理后台 Layout；
- 不读取后台用户 Store 作为业务授权来源；
- 使用独立 Browser Session；
- 如果使用 Platform SSO，只通过服务端换取 Runtime Session，不把后台 JWT 直接复用到 Runtime API。

# 52. 后端模块归属与现有代码实施映射

## 52.1 总体映射

| 最终能力 | 当前代码基线 | 动作 |
|---|---|---|
| Tenant 解析 | `platform_tenant` / 现有中台认证 | 复用 |
| API Key 验证 | `platform_api_key` / 现有 API Key 调用链 | 扩展 `client_id`，不重写认证协议 |
| Workflow Version | `ai_workflow_version` | 复用 |
| Workflow Execution | `ai_workflow_execution` | 复用；写入 API_KEY Principal / snapshot |
| Workflow Resource | `ai_workflow_resource_binding` | 复用 |
| Event / Checkpoint / Retry / Cancel | Universal Workflow V2 | 复用 |
| App/Contract/Release/Grant | 无 | `polaris-platform-core` 新增 |
| Publish Compiler | 已有 Workflow 编译能力基础 | `polaris-ai` 增量扩展 |
| Public Event Projector | 现有 Workflow Event Source | Platform 层新增投影，不复制 Event Store |
| Experience Runtime | Vue3 现有工程 | `polaris-ui-vue3` 新增 |

## 52.2 调用现有 Workflow 的唯一正确方式

App Gateway 完成授权后，构造现有 Workflow V2 所需调用上下文：

```text
principalType = API_KEY
principalId   = API Channel 使用 apiKeyId；Browser Channel 使用服务端派生的稳定 browser-principal-id，并在 principalSnapshot 中保留 sourceApiKeyId/clientId/grantId
workflowVersionId = Release.workflowVersionId
input = Release Request Adapter 生成的 canonical input
principalSnapshot = tenant/client/app/grant/release/contract/channel/actor claims
bindingSnapshot = 现有 Resource Binding 解析结果
budgetJson = Grant + Release 计算后的预算
quotaScopes = tenant/client/grant/workflow 等范围
```

Browser Channel 不要求把原 API Key 带进浏览器；Browser Session 只是由已验证 Client/Grant 派生出来的短期凭据，服务端在启动 Workflow 时重新构造上述 Principal Snapshot。

然后进入现有 Workflow Start/Execution 链路。

不得：

```text
直接 insert ai_workflow_execution 绕过现有 Service
复制一份 ai_workflow_app_execution_status
由 App Gateway 自己调度节点
由页面 Runtime 直接调用节点执行器
```

## 52.3 polaris-platform-core

建议承担：

- Consumer Client；
- API Key Client 化；
- Workflow App；
- Contract；
- Release；
- Grant；
- Channel；
- Experience 元数据；
- Launch Ticket；
- Browser Session；
- Public/API Gateway；
- Rate / Quota；
- Asset Gateway；
- Invocation Mapping；
- Consumer Event Projection；
- Webhook；
- Audit。

## 52.4 polaris-ai

承担：

- Workflow Version 分析；
- Publish Compiler 的 Workflow 部分；
- Capability Closure；
- SubWorkflow 递归；
- Workflow 执行复用；
- Resource Binding Check；
- Workflow Event Source；
- Release Adapter 到 canonical input/output 的集成点。

## 52.5 polaris-framework

只放通用基础设施：

- Origin Matcher；
- Security Headers；
- HMAC；
- Rate Limit 基础；
- XSS / Sanitizer 基础；
- Redis atomic helper；
- Idempotency helper。

不要把 App 业务塞进 framework。

---

# 53. Cache 策略

不可变对象：

```text
Release
Contract
Experience Version
```

可以长缓存：

```text
cache key = id + contentHash
```

可变指针：

```text
App.stableReleaseId
Experience.stableVersionId
Grant
```

使用：

- 短 TTL；
- 更新主动失效；
- revision 防陈旧授权。

Browser Manifest 返回可使用 ETag，但敏感授权页面默认：

```text
Cache-Control: private, no-store
```

不要让 CDN 缓存带用户上下文的 Manifest。

---

# 54. SSE / 长连接性能

继续复用现有 Workflow Event Store。

Public Gateway 只做：

- ownership；
- projection；
- flush。

要求：

```text
heartbeat 15～30s
Last-Event-ID
completed / failed / cancelled 后关闭
Nginx response buffering off
合理 idle timeout
```

禁止每条 SSE 独占 Workflow Worker 线程。

浏览器重连必须重新鉴权。

`Last-Event-ID` 只表示 cursor，不是授权凭据。

---

# 55. 状态与禁用语义

## Client DISABLED

- 禁止新 Ticket；
- 禁止新 Execution；
- Key 视为不可用。

## API Key DISABLED / EXPIRED

- 立即禁止新 API 调用；
- 立即禁止签发新 Ticket；
- 已签发 Browser Session 是否立即失效由 Grant Revision / Key Revocation Policy 决定；
- 推荐安全场景下递增相关 Grant/Auth revision，使其尽快失效。

## App DISABLED

- 禁止新 Execution；
- 禁止新 Browser Session；
- 已运行默认允许完成；
- 可 Force Cancel。

## Grant DISABLED

只影响指定 Client 与 App 的关系。

## Release REVOKED

- 禁止新 Execution；
- 历史 Execution 仍可审计；
- 已运行默认继续；
- 严重安全事件可 Force Cancel。

## Experience Version REVOKED

- 禁止新页面 Session 使用；
- 已打开页面下一次 Manifest/Action 请求可要求刷新；
- 如果是 XSS 等安全漏洞，必须支持强制 Session 失效。

---

# 56. 运行中快照语义

一个 Execution 创建后应冻结：

```text
releaseId
workflowVersionId
contractId
principalSnapshot
bindingSnapshot
budgetSnapshot
grantId
grantRevision at start
channel
```

后续：

```text
Grant 改 Release
App Stable 切换
Experience 更新
```

不能让已运行 Execution 中途漂移。

但安全紧急撤销可以通过：

```text
Force Cancel
```

显式终止，而不是偷偷修改执行上下文。

---

# 57. 数据生命周期与不可变记录

建议默认：

| 数据 | 默认策略 |
|---|---|
| Launch Ticket | 30～60 秒 |
| Browser Session | Idle 10～30 分钟，Absolute 2～8 小时 |
| Idempotency Record | 24 小时～7 天，可配置 |
| Input Temp Asset | 24 小时或按业务配置 |
| Signed Download URL | 5～15 分钟 |
| Invocation Mapping | 按审计策略长期保留 |
| Execution | 复用 Workflow V2 生命周期 |
| Contract / Release | 被引用时不得硬删除 |
| Experience Version | 被 Grant/历史调用引用时不得硬删除 |
| Public Audit | 按租户合规策略 |

Contract、Release、Experience Version 属于历史可追溯对象，推荐：

```text
RETIRED / REVOKED / ARCHIVED
```

而不是物理删除。

删除 App 时也必须先检查历史 Invocation / Execution 引用，避免审计链断裂。

---

# 58. 审计

每次 Invocation 记录：

```text
traceId
tenantId
clientId
apiKeyId
apiKeyPrefix/keyNameSnapshot（不保存 Secret）
appId
grantId
grantRevision
releaseId
contractId
experienceVersionId
channel
userRef
conversationId
executionId
idempotencyKey
requestFingerprint
origin
ipHash
userAgentHash
rateLimitResult
quotaResult
estimatedBudget
actualUsage
assetBytes
result
latency
```

不要普通日志打印：

- API Key 原文；
- Ticket；
- Browser Token；
- Prompt；
- 敏感输入；
- Connector Secret。

IP 如无业务必要，不长期存原文，可：

```text
hash(ip + serverPepper)
```

---

# 59. Metrics

建议：

```text
wf_app_invocation_total
wf_app_invocation_failed_total
wf_app_invocation_latency
wf_app_active_execution
wf_app_active_sse
wf_app_token_usage
wf_app_asset_bytes
wf_app_rate_limited_total
wf_app_ticket_issued_total
wf_app_ticket_exchange_failed_total
wf_app_webhook_delivery_failed_total
wf_app_contract_validation_failed_total
wf_app_experience_render_failed_total
```

告警：

- Client Token 突增；
- 单 Grant 失败率突增；
- Ticket 重放；
- Origin 拒绝突增；
- Asset reject 突增；
- Webhook 失败率；
- SSE 断连；
- Experience Render Error；
- Contract Adapter Error。

---

# 60. API Key Client 化迁移策略

不能直接给 `platform_api_key.client_id` 加 NOT NULL。

建议：

## Step 1

创建：

```text
platform_consumer_client
```

## Step 2

为每个已有 Tenant 创建：

```text
legacy-default
```

Client。

## Step 3

`platform_api_key` 增加 nullable：

```text
client_id
```

## Step 4

把现有 Key 回填到：

```text
legacy-default
```

## Step 5

代码兼容一段时间：

```text
client_id != null
→ 正常 Client 模型

client_id == null
→ 仅 legacy fallback，并记录 warning
```

## Step 6

新建 Key 强制选择 Client。

## Step 7

确认历史数据全部迁移后再考虑：

```text
NOT NULL
```

这样不会因为上线新功能导致所有老 API Key 瞬间失效。

---

# 61. API Key Secret 安全

当前 Key 使用 SHA-256 digest 存储。

如果 API Key 本身使用至少 256-bit CSPRNG 随机值，则不可逆 Digest 方式可以继续使用。

要求：

- 原始 Key 只在创建时展示一次；
- 服务端日志不记录；
- 前缀单独用于识别；
- 比较使用 digest；
- 支持多 Key 轮换；
- 支持过期时间；
- 支持撤销。

不要把人类可记忆短密码当 API Key。

---

# 62. Nginx / Gateway 注意事项

针对 SSE：

```text
proxy_buffering off
合理 read_timeout
```

针对上传：

- 不要只靠 Nginx `client_max_body_size`；
- 应用仍检查 Asset Policy；
- signed upload 也检查声明 size 与实际 object metadata。

针对页面：

- Runtime 路由与 Admin 路由隔离；
- Security Header 不要被上游覆盖；
- CORS 不用 `*` + credentials；
- iframe CSP 动态生成时必须防 Header Injection。

---

# 63. 深度风险审查：新增必须关注的问题

## 63.1 “动态页面”可能演化成第二套低代码平台

风险：

```text
Layout DSL
+ Action DSL
+ Binding DSL
+ Theme DSL
```

不断加能力后，最终可能拥有：

```text
脚本
网络
循环
复杂表达式
```

从而变成另一个不可审计执行引擎。

控制原则：

> UI DSL 永远只负责展示与调用预定义 Runtime Action，不获得任意计算、网络或服务端资源权限。

## 63.2 Release 与 Experience 双版本可能产生组合爆炸

例如：

```text
Release R1/R2/R3
×
Experience E1/E2/E3
×
Contract C1/C2
```

不能允许任意组合。

必须由 Compiler 生成：

```text
Compatibility Matrix
```

例如：

| Experience | Contract V1 | Contract V2 |
|---|---|---|
| crm-embed V3 | YES | NO |
| web-full V5 | YES | YES |

Grant 只能选择 PASSED 组合。

## 63.3 Stable 指针自动升级可能伤害老项目

老项目默认：

```text
PINNED
```

不能因为 App 发布了新 Release 就自动升级。

管理 UI 必须明显标注：

```text
当前 Client 是否跟随 Stable
```

## 63.4 Key 撤销与 Browser Session 之间可能存在时间差

必须定义：

- 是否 Key revoke 立即杀 Browser Session；
- 是否只禁止新 Ticket；
- 安全事件如何一键 revoke Client 全部 Session。

建议增加：

```text
clientAuthRevision
```

作为后续增强，比扫描 Redis 全 Session 更可靠。

## 63.5 Sync API 超时不能等同 Workflow 超时

HTTP 30 秒超时：

```text
≠
Workflow 失败
```

必须返回 `202 + executionId`，避免重复启动。

## 63.6 Webhook 会形成新的 SSRF 出口

Callback URL 必须预注册。

绝不能允许：

```json
{
  "callbackUrl": "http://169.254.169.254/..."
}
```

随请求动态传入。

## 63.7 Adapter 会成为兼容债务

Adapter 不是无限兼容垃圾场。

建议：

- 每个 Contract 有明确生命周期；
- 统计 Contract 使用 Client；
- 无 Client 使用的旧 Contract 才可归档；
- 管理端显示 Legacy Contract 占用情况。

## 63.8 Event Contract 不能被忽略

很多系统不是只依赖最终 Output，而是依赖：

```text
chat_delta
progress
widget_update
```

这些 Event 同样属于 Contract。

改变事件字段也可能 Breaking。

## 63.9 文件内容不仅是 MIME 问题

SVG / PDF / HTML / Office 等可能包含主动内容或外部引用。

Asset Pipeline 需要按文件类型有独立 Policy，不能只统一“病毒扫描”。

## 63.10 Origin 不是用户身份

`Origin` 只说明页面来自哪里，不能说明当前用户是谁。

不要用：

```text
Origin == trusted
```

替代 Ticket / Session。

## 63.11 API Key Scope 与 Grant 权限可能漂移

不要让两个权限系统都维护一份完整资源列表。

推荐职责：

```text
API Key Scope
→ 允许调用哪类平台能力

Grant
→ Client 是否可以消费这个 App，以及 Channel / Release / Quota

Workflow Resource Binding
→ Workflow 实际可以使用什么内部资源
```

三者职责分开。

## 63.12 运行中 Resource Secret 轮换

Release 不应保存 Secret。

`binding_snapshot` 只保存非敏感解析结果 / version reference。

实际 Secret 由安全资源组件在节点执行时读取。

否则 Release 永久存储会导致 Secret 无法轮换。

## 63.13 Resource 被管理员禁用后的语义

即使 Release 不可变，底层 Connector / Datasource 被安全禁用时，新节点执行仍应该失败关闭，而不是因为 Release Snapshot 绕过禁用。

不可变的是：

```text
“绑定到哪个资源”
```

不是：

```text
“无条件绕过资源当前安全状态”
```

## 63.14 多地域 / 多实例的一致性

Grant Disable、Release Stable 切换、Experience Stable 切换需要：

- DB 原子更新；
- Redis Cache Invalidate；
- 多实例广播或短 TTL；
- revision 二次保护。

否则某些实例可能继续使用旧权限。

## 63.15 Designer 需要 Schema Version

Layout / Interaction / Theme Manifest 都必须带：

```text
schemaVersion
```

Renderer 要支持有限版本窗口。

不能未来升级前端后让所有历史 Experience 无法解析。

## 63.16 Widget 升级也可能 Breaking

Widget Registry 不能只按：

```text
ChatStream
```

最好支持：

```text
ChatStream@1
ChatStream@2
```

Experience Version 保存 Widget dependency version。

否则前端升级 Widget 后历史 Experience UI 可能悄悄变化。

## 63.17 可访问性与国际化不能事后补

动态 Layout 必须从一开始支持：

- keyboard；
- focus trap；
- aria label；
- error association；
- locale；
- timezone；
- number/date format。

否则 Designer 生成的页面难以达到生产质量。

## 63.18 宿主页与 iframe 的用户退出同步

CRM 用户退出后，Polaris iframe 不应一直保持长期可用。

建议：

- Browser Session 短 TTL；
- Parent 可发送 `HOST_LOGOUT`；
- 后端 Client 可调用 revoke session；
- 高安全场景使用 user session binding nonce。

## 63.19 Replay 不只发生在 Ticket

还需要防：

- Webhook replay；
- Idempotent request replay；
- Host Event replay；
- Asset complete replay；
- Cancel replay。

不同场景使用：

```text
nonce / eventId / idempotency / state machine
```

而不是一个通用“sign”。

## 63.20 计费主体必须明确

消费方是租户自己的项目，因此默认成本归：

```text
Tenant
```

但必须能细分：

```text
Client
Grant
App
Release
```

否则某个老项目异常无法准确定位成本。

---

## Schema / Regex DoS

JSON Schema、条件 DSL、Binding 中如果允许复杂正则，可能产生 ReDoS。

要求：

- pattern 最大长度；
- 发布时预编译；
- 优先使用线性时间正则实现或限制高风险结构；
- 单次 Validation 有 CPU/时间预算；
- 拒绝递归/巨大 Schema。

## 外部媒体 URL 可能形成隐私侧信道

Markdown 或 Widget 如果允许任意远程图片：

```text
<img src="https://tracker.example/...">
```

可能泄露访问者 IP、访问时间等信息。

高安全 Experience 建议：

- 禁止任意 remote image；或
- 仅 allowlist domain；或
- 通过平台图片代理抓取并重新托管。

`Referrer-Policy: no-referrer` 只能减少 Referer 泄露，不能隐藏客户端 IP。

## Connector 仍需独立 SSRF / Egress Policy

即使 Consumer 是同租户项目，Workflow 内的 HTTP Connector 仍可能被重定向到内网或 metadata endpoint。

运行时必须：

- Connector endpoint 来自服务端资源配置；
- Redirect 每跳重新校验；
- 禁止私网/metadata 地址（除非明确受控内网 Connector）；
- Agent 不能通过 Prompt 动态创造任意 URL Tool 权限。

---

# 64. 管理后台产品设计

建议信息架构：

```text
AI 开放中台
├── Consumer Clients
│   ├── Client 信息
│   ├── API Keys
│   └── 使用统计
│
├── Workflow Apps
│   ├── App Overview
│   ├── Contracts
│   ├── Releases
│   ├── Channels
│   ├── Experiences
│   ├── Grants
│   ├── Invocations
│   └── Audit
│
└── Experience Designer
```

## 发布流程

### Step 1：App

```text
App Name
App Code
Workflow
```

### Step 2：Contract

```text
Input
Output
Events
Errors
Compatibility
```

Contract 只描述外部协议，不在这里保存 Adapter。

### Step 3：Release

```text
Workflow Version
Request / Response / Event / Error Adapter
Capability Report
Resource Report
Security Report
Release Content Hash
```

### Step 4：Experience（可选）

```text
Template
Layout Designer
Responsive Preview
Interaction
Theme
```

### Step 5：Grant

```text
Consumer Client
Allowed Channels
Release Policy
Experience Mapping
Origin
Rate
Quota
Budget
Asset
Webhook
```

将“发布 App”和“授权给 Client”分成两个动作。

避免一发布就自动被所有项目使用。

---

# 65. 典型业务时序

## 65.1 老 ERP API

```text
ERP Backend
   │
   │ API Key
   ▼
Platform Gateway
   │
   ├─ Resolve Client
   ├─ Resolve Grant
   ├─ Resolve PINNED Release
   ├─ Validate Contract
   ├─ Request Adapter
   ▼
Workflow V2
   │
   ▼
Output Projection
   │
Response Adapter
   │
   ▼
ERP
```

## 65.2 CRM iframe

```text
CRM Backend
   │ API Key
   ▼
Create Embed Ticket
   │
   ▼
CRM Browser
   │ postMessage(ticket)
   ▼
Polaris iframe
   │
   ├─ origin check
   ├─ ticket exchange
   ├─ browser session
   ├─ resolve Experience
   └─ render Layout DSL
   │
   ▼
Workflow V2
```

## 65.3 Widget

```text
Host Page
   │
polaris-widget.js
   │
   ├─ launcher
   └─ secure iframe
          │
          ▼
    Experience Runtime
```

## 65.4 长任务恢复

```text
Browser Session A
↓
Execution started
↓
Session A expires
↓
Workflow continues
↓
Client Backend issues new Ticket
↓
Browser Session B
↓
Ownership check
↓
Reattach execution / SSE resume
```

---

# 66. 实施路线

## Phase 0：基线确认

确认：

- API Key Authentication 调用链；
- Workflow Start API；
- principal_snapshot 生成点；
- Resource Binding 解析点；
- Token 计量；
- Event Store / SSE；
- Cancel；
- Object Storage；
- SubWorkflow version resolution；
- Approval / Wait 状态。

产物：

```text
integration-map.md
principal-flow.md
resource-binding-map.md
```

## Phase 1：Consumer Client + API Key 迁移

实现：

- Consumer Client；
- API Key client_id；
- legacy-default migration；
- Key rotation；
- Client statistics。

## Phase 2：App + Contract + Release

实现：

- Workflow App；
- Contract；
- Release Adapter；
- Release；
- Compiler；
- Breaking Change Report；
- Stable / Latest。

## Phase 3：Grant + API Channel

优先先解决真正的老项目复用：

- Grant；
- PINNED / STABLE；
- API invoke；
- Sync / Async；
- Idempotency；
- Invocation Mapping；
- Quota / Budget。

此阶段就可以生产服务端集成。

## Phase 4：Event + Webhook

- Event Projection；
- SSE；
- Callback Endpoint；
- HMAC；
- Retry / DLQ。

## Phase 5：Asset

- Init / Upload / Complete；
- Ownership；
- Decode；
- Re-encode；
- Signed Download；
- SVG/PDF Policy。

## Phase 6：Browser Security Runtime

- Launch Ticket；
- Browser Session；
- Grant Revision；
- CSP；
- Origin；
- CSRF；
- Direct Web Shell。

## Phase 7：Experience Runtime

先实现 DSL Runtime，不先做复杂 Designer：

- Layout Registry；
- Widget Registry；
- Binding；
- Condition；
- Action；
- Theme；
- Responsive。

## Phase 8：EMBED / WIDGET / H5

- iframe；
- postMessage；
- Widget loader；
- Host Event；
- Resize；
- WebView。

## Phase 9：Experience Designer

- drag/drop；
- Property Panel；
- Binding Selector；
- Interaction Editor；
- Responsive Preview；
- Compatibility Checker。

## Phase 10：安全、迁移与压测

- IDOR；
- XSS；
- SSRF；
- CSRF；
- Ticket Replay；
- Webhook Replay；
- Origin Bypass；
- Asset Attack；
- Idempotency Conflict；
- SSE Resume；
- Redis Fault；
- Multi-instance cache consistency；
- Grant revoke propagation；
- Long-running execution reattach；
- Layout Manifest fuzzing。

---

# 67. 生产验收标准

## API / Contract

```text
[ ] 老项目固定 Contract V1，Workflow 升级后仍可使用
[ ] PINNED Client 不受 Stable Release 切换影响
[ ] FOLLOW_STABLE Client 只升级到兼容 Release
[ ] Breaking Contract 无法直接替换 Stable
[ ] Request Adapter / Response Adapter 行为可审计
[ ] 同 Idempotency-Key + 不同 Body 返回冲突
```

## Client / Grant

```text
[ ] Client A Key 无法调用 Client B Grant
[ ] 同租户不同 Client 数据隔离
[ ] Grant Disabled 后不能新执行
[ ] Client Disabled 后全部新调用停止
[ ] API Key Rotate 无停机
[ ] 旧 Key 可平滑迁移到 legacy-default Client
```

## 页面

```text
[ ] 只有页面 URL 不能获得执行权限
[ ] API Key 不进入浏览器
[ ] Ticket 30～60 秒过期
[ ] Ticket 只能兑换一次
[ ] Origin 不匹配拒绝
[ ] Grant Revision 变化使旧授权失效
[ ] Browser Session 过期后长任务仍继续
[ ] 新 Session 可按策略 reattach 原 Execution
```

## Experience

```text
[ ] 页面布局不依赖固定 FORM/CHAT/TRYON 枚举
[ ] Layout Tree 可以组合不同业务页面
[ ] Renderer 不执行任意 JS
[ ] 非 Registry Widget 无法发布
[ ] 超复杂 Manifest 无法发布
[ ] Contract 字段删除导致 Experience BLOCKER
[ ] Experience Version 可回滚
[ ] Widget Version 依赖明确
[ ] Desktop / Tablet / Mobile / narrow iframe 正常
```

## iframe / Widget

```text
[ ] 非 Allowed Origin 无法 frame
[ ] forged postMessage 被拒绝
[ ] event.source 不匹配被拒绝
[ ] channelId 不匹配被拒绝
[ ] Host Event 必须在 allowlist
[ ] Widget 默认通过隔离 iframe 展示业务 UI
```

## Asset

```text
[ ] 修改 assetId 无法跨 Client 使用
[ ] 任意 URL 无法伪装 Asset
[ ] 超大像素图片拒绝
[ ] 非法 MIME / Magic Number 拒绝
[ ] SVG 默认不能直接执行主动内容
[ ] PDF 等不可信文档不作为同源 HTML 直接执行
[ ] Signed URL 短期过期
```

## Execution / Event

```text
[ ] SSE 重新连接后可续读
[ ] Last-Event-ID 不可作为授权
[ ] Public Event 不泄露 Prompt / SQL / StackTrace
[ ] HTTP sync timeout 不自动取消 Workflow
[ ] Release 切换不影响已运行 Execution
[ ] Force Cancel 可处理严重安全撤销
```

## Webhook

```text
[ ] Callback URL 不可按请求任意指定
[ ] localhost / 内网 / metadata IP 被拒绝
[ ] Redirect 后重新 SSRF 检查
[ ] HMAC 验证成功
[ ] Event ID replay 被拒绝
[ ] Retry 有上限且可审计
```

## 稳定性

```text
[ ] Redis 故障认证 fail-closed
[ ] 多实例 Grant Disable 能快速传播
[ ] Stable Release 切换原子
[ ] Experience Stable 切换原子
[ ] Cache 陈旧不会导致权限扩大
[ ] 长任务 Browser Session 过期后不丢执行
```

---

# 68. 最终架构

```text
                          Tenant
                            │
             ┌──────────────┴───────────────┐
             │                              │
             ▼                              ▼
      Consumer Client                 Workflow Designer
             │                              │
         API Keys                            ▼
             │                       Workflow Version
             │                              │
             │                              ▼
             │                       Workflow Application
             │                              │
             │                 ┌────────────┼────────────┐
             │                 ▼            ▼            ▼
             │              Contract      Release     Experience
             │                 │            │            │
             └──────────────┐  │            │            │
                            ▼  ▼            ▼            ▼
                              App Grant
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
             API             DIRECT_WEB        EMBED/WIDGET/H5
              │                  │                  │
           API Key           One-Time Ticket   One-Time Ticket
              │                  │                  │
              │            Browser Session    Browser Session
              │                  │                  │
              └──────────────────┼──────────────────┘
                                 ▼
                         Contract Runtime
                                 │
                         Request Adapter
                                 │
                                 ▼
                     Universal Workflow V2
                                 │
                     Output/Event Projection
                                 │
                         Response Adapter
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
             API            Experience UI       Webhook
```

---

# 69. 最终落地原则

整个功能最终应被理解为：

> **“把租户自己的 Workflow Version 编译成稳定 Contract 的 Workflow Application，再通过 Client Grant 分发给自己的不同项目；API 和页面只是不同消费 Channel，页面通过安全、版本化的 Experience DSL 动态呈现。”**

实施时守住以下原则：

1. **Workflow 执行事实源只复用，不复制。**
2. **API Key 使用现有 API_KEY Principal，不新增匿名宽权限 Principal。**
3. **Tenant 下面先识别 Consumer Client，再谈授权。**
4. **App 发布与授权给 Client 是两个动作。**
5. **老项目优先 PINNED Release，避免被自动升级。**
6. **Contract 是长期兼容边界，Workflow Version 不是。**
7. **Request / Response / Event / Error 都属于 Contract。**
8. **Adapter 使用安全 DSL，不执行任意脚本。**
9. **页面 URL 不是权限，API Key 永不进入浏览器。**
10. **浏览器渠道统一通过一次性 Ticket + 短期 Session。**
11. **Execution Ownership 不绑定短期 Browser Session。**
12. **Channel 与 Experience 分离。**
13. **Experience 与 Workflow Release 分离并独立版本化。**
14. **FORM / CHAT / TRYON / REPORT / DASHBOARD 只是模板，不是运行时布局枚举。**
15. **页面使用 Layout Tree + Widget Registry + 受限 Action DSL。**
16. **UI DSL 永远不能演化成任意脚本/网络执行平台。**
17. **Release / Experience / Contract 的兼容关系必须发布时验证。**
18. **客户端永远不能决定 tenant/resource/release/objectKey 等安全字段。**
19. **任何输出、事件、Asset 都必须经过公开 Contract/Ownership Projection。**
20. **Grant、Client、Key、Release、Experience 都必须可禁用、可撤权、可审计。**
21. **撤权使用 revision / 短缓存保证多实例快速收敛。**
22. **Webhook、Asset、iframe、Widget 都视为独立攻击面，不因为“同租户”降低安全要求。**
23. **已运行 Execution 使用创建时快照，不因配置更新静默漂移。**
24. **严重安全事件用显式 Force Cancel，而不是修改运行中上下文。**
25. **所有复杂编译尽量在发布阶段完成，Runtime 保持轻量和可预测。**
26. **Contract 只定义外部协议，Adapter 必须冻结在 Release。**
27. **外部有限窗口幂等由 App Gateway 管理，Workflow 内部使用派生的稳定执行幂等键。**
28. **新增数据库结构必须走增量迁移，绝不在线上重新执行带 DROP 的 Workflow 初始化脚本。**
29. **Runtime 页面与管理后台优先使用独立 Origin，避免共享同源安全边界。**
30. **所有新增代码必须落在现有模块边界内；Application Distribution 是适配层，不是第二套 Workflow Engine。**

在这套模型下，未来增加：

- 数字人；
- 音视频生成；
- 企业知识助手；
- 数据 Copilot；
- 审批查询；
- AI 客服；
- 大屏；
- 移动端；
- 新 Widget；

都不需要重新设计工作流分享安全模型，只需要增加 Contract、Widget 或 Experience 能力。


---

# 附录 A：最终实施前代码核对清单

在正式编码 PR 开始前，开发者必须在当前 `master` 上完成以下定位，并把实际类名/方法名写入任务卡，而不是重新实现：

```text
[ ] 现有 API Key Authentication Filter/Interceptor/Service
[ ] platform_api_key Entity / Mapper / Service
[ ] Token usage / quota 计量入口
[ ] Workflow 发布 Version 的 Service
[ ] Workflow 启动 Execution 的统一 Service
[ ] principal_snapshot 的构造位置
[ ] Resource Binding Resolver
[ ] Workflow Event 查询/SSE 入口
[ ] Workflow Cancel Service
[ ] Workflow Artifact/Object Storage 组件
[ ] SubWorkflow 解析不可变版本的位置
[ ] Approval/Wait 的恢复入口
```

如果实际代码已经存在等价能力：

> **优先适配，不新增同功能 Service。**

如果实际代码接口不足：

> 在现有 Service 上增加最小扩展点，而不是从 Controller 绕过现有调用链。

# 附录 B：最终最小可上线范围（MVP）

第一期不要一次性实现完整 Designer。

建议生产 MVP：

```text
1. Consumer Client
2. API Key client_id 兼容迁移
3. Workflow App
4. Contract
5. Release + Release Adapter
6. Grant
7. API Channel（Sync/Async/SSE）
8. Invocation / 两层 Idempotency
9. 基础 Audit / Quota / Budget
10. DIRECT_WEB + Launch Ticket（如确有页面需求）
11. EMBED + publicEntryCode + CSP（如老项目需要 iframe）
12. Experience Runtime 使用手工/模板生成 Manifest
```

第二期再做：

```text
Visual Experience Designer
WIDGET
H5 深度适配
高级 Theme
复杂 Host Event
Canary Release
```

这样可以最早解决核心目标：

> **不可升级的老项目可以安全、稳定地消费持续迭代的 Workflow。**
