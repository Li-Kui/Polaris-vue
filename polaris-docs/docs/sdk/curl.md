# cURL 命令行调用

在终端或 Linux 服务器上，可以使用 `curl` 直接与北辰 API 进行交互。

## 1. 阻塞式对话调用

```bash
curl -X POST http://localhost:8080/platform/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sk-your-api-key" \
  -d '{
    "model": "polaris-default",
    "messages": [
      { "role": "user", "content": "请用简短的语言解释什么是量子力学。" }
    ],
    "stream": false
  }'
```

## 2. 流式 SSE 对话调用

使用 `-N` / `--no-buffer` 参数防止终端缓冲，实现实时流式输出：

```bash
curl -N -X POST http://localhost:8080/platform/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sk-your-api-key" \
  -d '{
    "model": "polaris-default",
    "messages": [
      { "role": "user", "content": "写一段关于人工智能未来的演讲稿。" }
    ],
    "stream": true
  }'
```

## 3. 查询可用模型列表

```bash
curl -X GET http://localhost:8080/platform/api/v1/models \
  -H "X-API-Key: sk-your-api-key"
```
