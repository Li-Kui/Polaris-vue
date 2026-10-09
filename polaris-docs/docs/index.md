---
layout: home

hero:
  name: "北辰 AI 开放平台"
  text: "企业级大模型与知识库开放中台"
  tagline: "兼容 OpenAI 核心协议，并提供可恢复工作流开放接口"
  actions:
    - theme: brand
      text: 快速开始 →
      link: /guide/quickstart
    - theme: alt
      text: 对话 API
      link: /api/chat-completions
    - theme: alt
      text: 工作流 API
      link: /api/workflow-executions

features:
  - title: ⚡ 标准 OpenAI 兼容
    details: 无缝迁移现有 OpenAI SDK / LangChain / LlamaIndex 代码，只需修改 Base URL 和 API Key 即可接入。
  - title: 🔒 多租户严格数据隔离
    details: 租户间模型配置、知识库检索库、对话历史全物理隔离，保障企业数据资产安全。
  - title: 🌊 毫秒级流式响应 (SSE)
    details: 基于 Server-Sent Events (SSE) 协议，支持大模型 Token 级极速流式打字机吐字与安全过滤。
  - title: 📊 精确 Token 计量与配额
    details: 内置 Token 级消耗统计与租户配额限制，保障各业务条线资源可控可计量。
  - title: 🔁 可恢复工作流执行
    details: 支持幂等启动、状态查询、持久化事件续读、SSE 订阅和取消执行。
---
