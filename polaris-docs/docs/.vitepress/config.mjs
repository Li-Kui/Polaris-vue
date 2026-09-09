import {defineConfig} from 'vitepress'

export default defineConfig({
  title: "北辰 AI 开放平台",
  description: "企业级 AI 能力中台开放接口文档（兼容 OpenAI 规范）",
  base: '/platform/docs/',
  themeConfig: {
    logo: '/logo.png',
    nav: [
      { text: '开发指南', link: '/guide/quickstart' },
      { text: 'API 参考', link: '/api/chat-completions' },
      { text: '错误码', link: '/guide/errors' },
      { text: '中台控制台', link: '#to-platform-console', target: '_self' }
    ],
    sidebar: [
      {
        text: '开发指南',
        items: [
          { text: '快速开始 (Quickstart)', link: '/guide/quickstart' },
          { text: '鉴权机制与 API Key', link: '/guide/auth' },
          { text: '模型支持与参数说明', link: '/api/models' },
          { text: 'SSE 流式响应对接', link: '/guide/streaming' },
          { text: '错误处理与状态码', link: '/guide/errors' }
        ]
      },
      {
        text: 'API 接口参考',
        items: [
          { text: '对话补全 (Chat Completions)', link: '/api/chat-completions' },
          { text: '模型列表 (List Models)', link: '/api/models' }
        ]
      },
      {
        text: '多语言接入 SDK 示例',
        items: [
          { text: 'Python 接入示例', link: '/sdk/python' },
          { text: 'Node.js / JavaScript 示例', link: '/sdk/javascript' },
          { text: 'Java 接入示例', link: '/sdk/java' },
          { text: 'cURL 命令行调用', link: '/sdk/curl' }
        ]
      }
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/Li-Kui/Polaris-vue' }
    ],
    footer: {
      message: '北辰 AI 开放平台 · Enterprise Middle Platform API',
      copyright: 'Copyright © 2026 Polaris'
    }
  }
})
