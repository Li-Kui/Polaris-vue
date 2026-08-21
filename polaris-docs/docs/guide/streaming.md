# SSE 流式响应对接

对于聊天问答等交互场景，推荐启用流式响应 (`stream: true`)，以实现实时打字机吐字效果。

## 请求参数

在请求体中设置 `"stream": true`：

```json
{
  "model": "polaris-default",
  "messages": [
    {"role": "user", "content": "写一首关于北极星的现代诗"}
  ],
  "stream": true
}
```

## SSE 数据格式

服务端将返回 `text/event-stream` 格式的事件流，每个 chunk 为 JSON 字符串：

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream;charset=UTF-8

data: {"id":"chatcmpl-1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"夜"},"finish_reason":null}]}

data: {"id":"chatcmpl-1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"幕"},"finish_reason":null}]}

data: {"id":"chatcmpl-1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"深"},"finish_reason":null}]}

data: [DONE]
```

当收到 `data: [DONE]` 时，表示本次流式生成已全部结束。
