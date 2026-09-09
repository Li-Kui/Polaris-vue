# 鉴权机制与 API Key

北辰 AI 开放平台采用标准 HTTP 请求头进行鉴权认证。

## 请求头格式

在所有发往 `/platform/api/v1/**` 的请求中，必须携带 `X-API-Key` 请求头：

```http
X-API-Key: sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

> **提示**：为兼容部分标准 OpenAI 客户端库，北辰也支持通过 `Authorization: Bearer sk-xxx` 传递 API Key。

## 鉴权失败处理

| HTTP 状态码 | 错误信息 | 原因与排查 |
|:---|:---|:---|
| `401 Unauthorized` | `缺少 X-API-Key 请求头` | 请求未携带有效的 API Key 请求头 |
| `401 Unauthorized` | `无效的 API Key` | API Key 错误或在数据库中不存在 |
| `403 Forbidden` | `API Key 已被停用` | 管理员在中台控制台停用了该 Key |
| `403 Forbidden` | `API Key 已过期` | 该 Key 超过了设定的有效期限 |
| `429 Too Many Requests` | `租户 Token 配额已耗尽` | 租户可用 Token 余额不足 |
