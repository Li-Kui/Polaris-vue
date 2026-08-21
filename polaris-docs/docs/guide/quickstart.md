# 快速开始 (Quickstart)

欢迎使用北辰 AI 开放平台！本文档将引导你在 3 分钟内完成首个 API 请求。

## 1. 获取 API Key

1. 登录<a href="/platform/login">中台控制台</a>。
2. 在左侧菜单点击 **API 密钥管理**。
3. 点击 **新建 API 密钥**，生成一把形如 `sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` 的密钥。
4. 妥善保存你的 API Key，不要泄露在公开客户端代码中。

## 2. 发起首个对话请求 (cURL)

北辰 API 协议完全兼容 OpenAI 标准，使用标准 HTTP POST 请求即可：

```bash
curl http://localhost:8080/platform/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sk-your-api-key-here" \
  -d '{
    "model": "polaris-default",
    "messages": [
      {
        "role": "user",
        "content": "你好！请简要介绍一下你自己。"
      }
    ],
    "stream": false
  }'
```

### 响应示例

```json
{
  "id": "chatcmpl-87632482348",
  "object": "chat.completion",
  "created": 1724112000,
  "model": "polaris-default",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好！我是由北辰 AI 中台驱动的大模型助手，很高兴为你提供智能服务。"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "total_tokens": 42
  }
}
```
