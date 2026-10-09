# JavaScript / Node.js 接入示例

直接使用官方 `openai` NPM 包：

```bash
npm install openai
```

## Node.js 代码示例

请用中台首页接入示例中的实际 Base URL 替换下方占位地址，保留部署的 API 代理前缀，参见[快速开始](/guide/quickstart)。

```javascript
import OpenAI from 'openai'

const openai = new OpenAI({
  baseURL: 'https://your-polaris-host/prod-api/platform/api/v1',
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
