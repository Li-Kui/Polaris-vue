# 鉴权机制与 API Key

北辰 AI 开放平台采用标准 HTTP 请求头进行鉴权认证。

## 请求头格式

所有发往 `/platform/api/**` 的开放接口请求都必须携带 `X-API-Key` 请求头。OpenAI 兼容接口使用 `/platform/api/v1/**`，工作流接口使用 `/platform/api/workflows/**` 和 `/platform/api/workflow-executions/**`。

```http
X-API-Key: sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

> **提示**：为兼容部分标准 OpenAI 客户端库，北辰也支持通过 `Authorization: Bearer sk-xxx` 传递 API Key。

## 工作流权限与范围

调用工作流时，API Key 还需要对应权限：

| 权限 | 用途 |
|:---|:---|
| `workflow:execute` | 启动已发布工作流 |
| `workflow:read` | 查询执行状态、事件和订阅 SSE |
| `workflow:cancel` | 取消尚未结束的执行 |

API Key 可以配置允许访问的工作流编码列表。列表为空表示不限制；配置后，启动列表以外的工作流会返回 `403 Forbidden`。

## 鉴权失败处理

| HTTP 状态码 | 错误信息 | 原因与排查 |
|:---|:---|:---|
| `401 Unauthorized` | `缺少 X-API-Key 或 Authorization Bearer 请求头` | 请求未携带有效的 API Key 请求头 |
| `401 Unauthorized` | `无效的 API Key` | API Key 错误或在数据库中不存在 |
| `403 Forbidden` | `API Key 已被停用` | 管理员在中台控制台停用了该 Key |
| `403 Forbidden` | `API Key 已过期` | 该 Key 超过了设定的有效期限 |
| `403 Forbidden` | `API Key缺少权限` | Key 未配置当前接口需要的工作流权限 |
| `403 Forbidden` | `API Key无权调用该工作流` | 工作流编码不在 Key 的允许范围内 |
| `429 Too Many Requests` | `租户 Token 配额已耗尽` | 租户可用 Token 余额不足 |
