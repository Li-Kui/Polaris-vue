# Python 接入示例

因为北辰 API 全面兼容 OpenAI 协议规范，你可以直接使用官方 `openai` Python 库进行调用，只需配置 `base_url` 与 `api_key`！

## 安装官方 SDK

```bash
pip install openai
```

## 代码示例 (流式对话)

```python
from openai import OpenAI

# 1. 初始化客户端，指向北辰中台 Base URL
client = OpenAI(
    base_url="http://localhost:8080/platform/api/v1",
    api_key="sk-your-api-key-here"
)

# 2. 发起流式对话
response = client.chat.completions.create(
    model="polaris-default",
    messages=[
        {"role": "user", "content": "请用 Python 编写一个快速排序算法。"}
    ],
    stream=True
)

# 3. 逐 Token 打印输出
print("AI 回复: ", end="", flush=True)
for chunk in response:
    content = chunk.choices[0].delta.content
    if content:
        print(content, end="", flush=True)
print()
```
