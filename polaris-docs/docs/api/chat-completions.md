# 对话补全 (Chat Completions)

根据提供的上下文消息生成 AI 回复。

### 接口信息

- **请求方式**: `POST`
- **请求路径**: `/platform/api/v1/chat/completions`
- **认证方式**: `X-API-Key: sk-xxx`
- **Content-Type**: `application/json`

### 请求参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|:---|:---|:---:|:---|:---|
| `model` | string | 否 | `polaris-default` | 要调用的模型名称或智能体编码 |
| `messages` | array | **是** | - | 历史消息上下文列表 |
| `messages[].role` | string | **是** | - | 消息发送者角色：`user` 或 `assistant` |
| `messages[].content`| string | **是** | - | 消息文本内容 |
| `stream` | boolean | 否 | `false` | 是否开启 SSE 流式打字机推送 |
| `temperature` | number | 否 | `0.7` | 采样温度 (0.0 ~ 2.0)，值越大越具创造力 |
| `max_tokens` | integer | 否 | `null` | 回复生成的最大 Token 数量限制 |

### 请求示例 (非流式)

```json
{
  "model": "polaris-default",
  "messages": [
    { "role": "user", "content": "用三句话总结敏捷开发的核心理念。" }
  ],
  "stream": false
}
```

### 响应示例 (非流式)

```json
{
  "id": "chatcmpl-7f9e8a1b",
  "object": "chat.completion",
  "created": 1724112000,
  "model": "polaris-default",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "1. 以人为本，强调团队沟通与协作；\n2. 快速迭代，持续交付可工作的软件；\n3. 拥抱变化，敏捷响应业务与需求调整。"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "total_tokens": 58
  }
}
```
