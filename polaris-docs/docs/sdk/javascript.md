# JavaScript / Node.js 接入示例

直接使用官方 `openai` NPM 包：

```bash
npm install openai
```

## Node.js 代码示例

```javascript
import OpenAI from 'openai'

const openai = new OpenAI({
  baseURL: 'http://localhost:8080/platform/api/v1',
  apiKey: 'sk-your-api-key-here'
})

async function main() {
  const stream = await openai.chat.completions.create({
    model: 'polaris-default',
    messages: [{ role: 'user', content: '介绍一下微服务架构的优缺点。' }],
    stream: true,
  })

  for await (const chunk of stream) {
    process.stdout.write(chunk.choices[0]?.delta?.content || '')
  }
}

main()
```
