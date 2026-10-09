# 工作流执行 (Workflows)

通过 API Key 启动已发布工作流，并查询状态、读取事件、订阅 SSE 或取消执行。

## 接口总览

| 方法 | 路径 | 所需权限 |
|:---|:---|:---|
| `POST` | `/platform/api/workflows/{workflowCode}/executions` | `workflow:execute` |
| `GET` | `/platform/api/workflow-executions/{executionId}` | `workflow:read` |
| `GET` | `/platform/api/workflow-executions/{executionId}/events` | `workflow:read` |
| `GET` | `/platform/api/workflow-executions/{executionId}/events/stream` | `workflow:read` |
| `POST` | `/platform/api/workflow-executions/{executionId}/cancel` | `workflow:cancel` |

所有接口都需要 API Key，可使用 `X-API-Key` 或 `Authorization: Bearer <API Key>`。API Key 还必须允许访问目标工作流，详见[鉴权机制与 API Key](/guide/auth)。

## 判断响应是否成功

普通 JSON 接口需要同时检查 HTTP 状态和响应体 `code`，不能只以 HTTP 200 判断成功。当前鉴权过滤器在缺少或无效密钥时返回 HTTP 401，限流时返回 HTTP 429；控制器权限拒绝沿用统一异常响应，可能返回 HTTP 200 和业务码 `403`。只有 HTTP 成功且 `code === 200` 才应继续读取 `data`。

限流响应包含 `X-RateLimit-Limit`、`X-RateLimit-Remaining` 和 `X-RateLimit-Reset`，触发限流时另有 `Retry-After`。SSE 应先检查 HTTP 状态及 `Content-Type` 是否为 `text/event-stream`，再处理事件；错误响应不能按事件流解析。

工作流 SSE 订阅建立前，控制器异常返回非 2xx HTTP 状态和 `application/json` 的 `AjaxResult`，客户端应读取 `msg` 展示错误。例如读取权限不足返回 HTTP 403，非法续读参数返回 HTTP 400。执行不存在、属于其他租户/密钥/分享或受其他访客会话保护时，执行详情、事件和取消接口统一返回 HTTP 404 / `code: 404` /“工作流执行不存在或无权访问”，不返回 `data`，避免泄露执行是否存在；SSE 建立订阅前也遵循此约定。已建立的事件流不能再切换成 JSON 响应。

## 启动工作流

```http
POST /platform/api/workflows/{workflowCode}/executions
Content-Type: application/json
X-API-Key: sk-xxx
Idempotency-Key: order-20260930-001
```

`Idempotency-Key` 可选。重试同一业务请求时应复用同一个值，避免重复创建执行。

### 请求体

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|:---|:---|:---:|:---|:---|
| `input` | object | 否 | `{}` | 工作流输入，字段由已发布版本的输入 Schema 决定 |
| `environment` | string | 否 | `PROD` | 执行环境：`DEV`、`TEST` 或 `PROD` |

```json
{
  "input": {
    "content": "请总结这段文本",
    "language": "zh-CN"
  },
  "environment": "PROD"
}
```

### 响应示例

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "executionId": "01K6E9...",
    "workflowCode": "content-summary",
    "workflowVersionId": "01K6E8...",
    "versionNo": 3,
    "status": "QUEUED",
    "eventSequence": 1,
    "cancelRequested": false,
    "createTime": "2026-09-30T20:00:00.000+08:00"
  }
}
```

## 查询执行状态

```http
GET /platform/api/workflow-executions/{executionId}
X-API-Key: sk-xxx
```

常见状态包括：

| 状态 | 说明 |
|:---|:---|
| `QUEUED` / `RUNNING` / `RECOVERING` | 等待或正在执行 |
| `WAITING_APPROVAL` / `WAITING_TIMER` / `WAITING_EVENT` | 等待审批、定时器或外部事件 |
| `NEEDS_ATTENTION` | 需要人工处理 |
| `SUCCEEDED` | 执行成功，结果位于 `outputJson` |
| `FAILED` / `CANCELLED` / `REJECTED` | 执行失败、取消或被拒绝 |

`inputJson`、`outputJson`、`budgetJson` 和 `usageJson` 是 JSON 字符串，客户端需要按需再次解析。

## 读取事件

```http
GET /platform/api/workflow-executions/{executionId}/events?afterSequence=0&limit=200
X-API-Key: sk-xxx
```

也可以使用 `Last-Event-ID` 请求头代替 `afterSequence`。当两者同时提供时，以 `afterSequence` 为准。

事件包含 `sequenceNo`、`eventType`、`nodeRunId`、`nodeId`、`payloadJson` 和 `createTime`。保存最后一个 `sequenceNo`，即可在断线后继续读取。

## SSE 订阅

```http
GET /platform/api/workflow-executions/{executionId}/events/stream?afterSequence=0
Accept: text/event-stream
X-API-Key: sk-xxx
```

连接建立后首先收到 `connected` 事件，工作流事件使用 `workflow` 事件名：

```text
event: connected
data: {"executionId":"01K6E9...","afterSequence":0,"status":"RUNNING"}

id: 8
event: workflow
data: {"sequenceNo":8,"eventType":"NODE_SUCCEEDED","nodeId":"llm_1","payloadJson":"{...}"}
```

服务端在执行进入终态且事件全部发送后关闭连接，并定期发送注释心跳。客户端应保存事件 `id`，重连时通过 `Last-Event-ID` 或 `afterSequence` 续读。

事件发送前及心跳维护时，服务端会重新检查调用主体的有效性。API Key 被停用、删除、过期，或失去 `workflow:read` / 当前工作流范围时，旧连接会关闭；租户停用及授权查询不可用也按拒绝访问处理。静默连接默认每 15 秒复查，不保证零延迟。已建立的流只关闭连接，不能再返回 JSON 错误；客户端应结合重连或查询响应判断是否可继续访问，不能把流关闭本身当作执行成功或自动取消。

## 取消执行

```http
POST /platform/api/workflow-executions/{executionId}/cancel
X-API-Key: sk-xxx
```

取消是协作式操作。接口返回后应继续查询执行状态，直到进入 `CANCELLED` 或其他终态。

## 分享对话附件接口

以下接口属于浏览器分享运行时，**不使用 API Key**，与上文服务端工作流 API 分开。仅对有效的对话模板分享开放，并受分享限流、过期和发布状态限制。

| 方法 | 路径 | 用途 |
|:---|:---|:---|
| `GET` | `/platform/runtime/shares/{shareCode}/manifest` | 加载分享清单并初始化访客 Cookie |
| `POST` | `/platform/runtime/shares/{shareCode}/attachments` | `multipart/form-data` 的 `file` 字段上传附件 |
| `GET` | `/platform/runtime/shares/{shareCode}/attachments/{token}` | 同一访客读取原文件，响应禁止缓存 |
| `DELETE` | `/platform/runtime/shares/{shareCode}/attachments/{token}` | 移除自己上传的附件 |
| `POST` | `/platform/runtime/shares/{shareCode}/executions` | 传入聊天输入及附件映射，启动执行 |

客户端必须在上传、执行、文件读取以及后续状态查询、事件订阅、取消操作中保留同一访客 Cookie。其他访客即使知道附件 token 或执行 ID，也不能访问该会话的附件执行。token 不是公开下载地址，不应放进 URL 参数或对外分享。

上传成功返回 `data`，包含 `token`、`name`、`size`、`mediaType` 和 `truncated`。文档过长时 `truncated=true`，客户端应提示只提取前 28000 字符。格式、限额、生命周期及视觉模型要求见[工作流页面与 iframe 分享](/guide/workflow-sharing#对话附件)。

执行请求中，附件映射位于 `input` **之外**，不会改变已发布工作流的字符串输入契约：

```json
{
  "input": {
    "messages": [{ "role": "user", "content": "请总结附件" }],
    "currentMessage": "请总结附件"
  },
  "attachments": [{ "messageIndex": 0, "tokens": ["上传接口返回的token"] }]
}
```

`messageIndex` 为从 0 开始的 `messages` 数组下标，只能关联用户消息。后续追问应再次携带历史附件映射；服务端会校验归属、读取文档内容或解析私有图片，再注入对应消息。客户端不能直接构造内部图片引用，也不能指定服务器路径或远程图片地址。

## cURL 示例

将 `https://your-polaris-host/prod-api` 替换为当前部署的站点和 API 代理前缀，参见[快速开始](/guide/quickstart)。工作流 API 使用 `/platform/api/workflows`，不是聊天 API 的 `/platform/api/v1`。

```bash
curl -X POST https://your-polaris-host/prod-api/platform/api/workflows/content-summary/executions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sk-your-api-key-here" \
  -H "Idempotency-Key: summary-demo-001" \
  -d '{"input":{"content":"需要处理的文本"},"environment":"PROD"}'
```
