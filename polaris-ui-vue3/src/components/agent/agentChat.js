export const freshChatOptions = () => ({enabled: false, agentId: null, answerMode: 'QUERY_EVIDENCE',
  allowExternalTools: false, externalQuery: '', inheritPreviousContext: false, contextStartMessageId: null})

const positiveId = value => /^[1-9][0-9]{0,18}$/.test(String(value))
export const isActiveChatRequest = view => ['PREPARED', 'RUNNING', 'UNKNOWN'].includes(view?.status)
export function chatStatusLabel(view) {
  if (view?.cancelRequested && view.status === 'RUNNING') return '正在停止'
  return ({PREPARED: '等待发送', RUNNING: '运行中', SUCCEEDED: '已完成', FAILED: '失败',
    CANCELLED: '已停止', NEEDS_ATTENTION: '结果不确定，请核对', EXPIRED: '已过期'})[view?.status] || '状态待核对'
}
export function chatStageLabel(stage, callNo) {
  return ({INPUT_REVIEW: '正在核对输入', INPUT: '正在核对输入', KNOWLEDGE: '正在查找知识依据',
    PLAN: '正在规划本轮查询', TOOL: `正在执行第 ${callNo || ''} 项查询`, ANSWER: '正在生成并审核回答',
    CLAIMED: '正在准备协作'})[stage] || '正在准备协作'
}
export function chatErrorLabel(code) {
  const name = String(code || '').replace(/^AGENT_/, '')
  return ({CANCELLED: '本轮已停止', ACCESS_DENIED: '会话或来源权限已变化', SNAPSHOT_CHANGED: '配置或前文已变化，请重新确认',
    MODERATION_BLOCKED: '本轮内容未通过审核', KNOWLEDGE_UNAVAILABLE: '知识来源暂不可用', INSUFFICIENT_EVIDENCE: '未找到可用依据',
    BUDGET_EXCEEDED: '已达到本轮调用上限', DEADLINE_EXCEEDED: '已达到本轮等待时间上限', RESULT_UNKNOWN: '原请求结果不确定，请核对',
    AUDIT_INCOMPLETE: '保存或审计未完成，请核对原运行',
    MODEL_FAILED: '模型回答未完成', TOOL_FAILED: '外部查询未完成', DUPLICATE_RUN: '请求已存在，请核对原运行'})[name]
    || '本轮未完成，请核对原运行状态'
}

// 只提交服务端支持的字段；不展开会话对象、附件或客户端历史正文。
export function agentChatInput({conversationId, question, knowledgeBaseId, options, requestKey}) {
  if (!positiveId(conversationId) || !positiveId(options.agentId)) throw new Error('请选择协作智能体')
  const text = String(question || '').trim()
  if (!text || text.length > 4000) throw new Error('问题须为 1～4000 字符')
  if (!['QUERY_EVIDENCE', 'CONTINUE_HISTORY'].includes(options.answerMode)) throw new Error('请选择本轮回答方式')
  const external = options.answerMode === 'QUERY_EVIDENCE' && options.allowExternalTools === true
  const externalQuery = external ? String(options.externalQuery || '').trim() : ''
  if (external && (!externalQuery || externalQuery.length > 4000)) throw new Error('请填写本轮允许外发的查询，最多 4000 字符')
  if (options.answerMode === 'QUERY_EVIDENCE' && !knowledgeBaseId && !external) throw new Error('请选择知识库或开启本轮 MCP 查询')
  if (knowledgeBaseId != null && !positiveId(knowledgeBaseId)) throw new Error('知识库选择无效')
  if (options.contextStartMessageId != null && !positiveId(options.contextStartMessageId)) throw new Error('前文范围无效')
  return Object.freeze({conversationId, requestKey, agentId: options.agentId, question: text,
    knowledgeBaseId: knowledgeBaseId || null, externalQuery, allowExternalTools: external, answerMode: options.answerMode,
    contextStartMessageId: options.contextStartMessageId || null, inheritPreviousContext: options.inheritPreviousContext === true})
}

export function chatHistoryOptions(messages) {
  return (messages || []).flatMap((message, index, list) => {
    const answer = list[index + 1]
    if (message.role !== 'user' || !positiveId(message.id) || !message.agentRunId || answer?.role !== 'assistant'
        || message.agentRunId !== answer.agentRunId || !answer.id || answer.error || answer.streaming) return []
    return [{id: message.id, label: String(message.content || '').slice(0, 55)}]
  }).slice(-6)
}

// 历史链接仅来自服务端恢复的可访问指针；不让模型正文自行声明片段权限。
export function decorateAgentHistoryCitations(html, message) {
  if (!message?.id || message.streaming) return html
  const allowed = new Set((message.collaborationSources?.history || []).filter(old => old.available === true
    && positiveId(old.reference?.messageId) && /^S[1-9][0-9]{0,2}$/.test(old.reference?.citationId)
    && old.knowledge?.length).map(old => `${old.reference.messageId}:${old.reference.citationId}`))
  if (!allowed.size) return html
  const protectedTags = new Set(['pre', 'code', 'a', 'button', 'script', 'style']), stack = []
  return String(html).split(/(<[^>]+>)/g).map(part => {
    if (part.startsWith('<')) {
      const tag = part.match(/^<\s*(\/?)\s*([a-z][\w-]*)/i)
      if (tag && protectedTags.has(tag[2].toLowerCase())) {
        const name = tag[2].toLowerCase()
        if (!tag[1] && !/\/\s*>$/.test(part)) stack.push(name)
        else if (tag[1] && stack.at(-1) === name) stack.pop()
      }
      return part
    }
    if (stack.length) return part
    return part.replace(/\[H([1-9][0-9]{0,18}):(S[1-9][0-9]{0,2})\]/g, (text, origin, id) => allowed.has(`${origin}:${id}`)
      ? `<button type="button" class="knowledge-inline-citation" data-agent-history="${origin}" data-agent-citation="${id}" aria-label="查看历史参考片段 ${id}">${text}</button>` : text)
  }).join('')
}

// 原生 POST SSE：按完整事件解析，跨网络分片保留 UTF-8、多行 data 和 CRLF。
// 连接结束不是执行成功，必须有服务端终态；调用方只读恢复状态，绝不重发。
export async function consumeAgentChatStream(body, onEvent) {
  if (!body) throw new Error('浏览器未提供可读取的响应流')
  const reader = body.getReader(), decoder = new TextDecoder()
  let buffer = '', terminal = false
  try {
    while (!terminal) {
      const {value, done} = await reader.read()
      buffer += done ? decoder.decode() : decoder.decode(value, {stream: true})
      let boundary
      while ((boundary = /\r?\n\r?\n/.exec(buffer))) {
        const block = buffer.slice(0, boundary.index)
        buffer = buffer.slice(boundary.index + boundary[0].length)
        let event = 'message'; const data = []
        for (const line of block.split(/\r?\n/)) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
        }
        if (!data.length) continue
        let payload
        try { payload = JSON.parse(data.join('\n')) } catch { throw new Error('协作事件格式异常，请核对原运行') }
        await onEvent(event, payload)
        if (['done', 'existing', 'error'].includes(event)) { terminal = true; break }
      }
      if (done) break
      if (buffer.length > 1048576) throw new Error('协作事件超出限制，请核对原运行')
    }
    if (!terminal) throw new Error('连接已中断，请核对原运行；不会自动重试')
  } finally {
    try { await reader.cancel() } catch { /* 已断开的流无需再次处理。 */ }
    reader.releaseLock()
  }
}
