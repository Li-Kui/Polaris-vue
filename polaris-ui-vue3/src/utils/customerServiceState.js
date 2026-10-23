// 请求标识用于会话去重；发送超时后的重试必须继续使用同一标识和原始问题。
export function customerMessageRequest(text, pending, newId = () => globalThis.crypto?.randomUUID?.()
  || `csr_${Date.now()}_${Math.random().toString(36).slice(2)}`) {
  if (pending) return pending
  const question = text.trim()
  if (!question || text.length > 2000) throw new Error('请输入 1～2000 字的问题')
  return { requestId: newId(), text: question }
}

// 旧会话的迟到响应不覆盖新会话；服务器确认受理后，客户端无需再重发该输入。
export function customerSnapshot(current, snapshot, pending) {
  if (!current || current.id !== snapshot.id) return { session: current, pending, accepted: false }
  const accepted = !!pending && snapshot.turns.some(turn => turn.requestId === pending.requestId)
  return { session: snapshot, pending: accepted ? null : pending, accepted }
}

export async function customerConfirmation(confirm) {
  try {
    await confirm()
    return true
  } catch (action) {
    if (action === 'cancel' || action === 'close') return false
    throw action
  }
}

export function customerTurnHint(turn) {
  const hints = {
    HISTORY_CAPABILITY_SUMMARY_HIDDEN: '历史能力参数摘要暂不展示；业务操作仅统计已记录的次数，不重新读取或执行任务。',
    SOURCE_UNAVAILABLE: '资料来源已失效，原回答暂不展示。',
    QUOTA_EXCEEDED: '当前租户的可用 Token 配额不足，请联系管理员。',
    QUOTA_UNAVAILABLE: '配额服务暂不可用，请稍后重试。',
    CONTENT_BLOCKED: '内容未通过检测，请调整问题后重试。',
    EVIDENCE_NOT_READY: '知识库缺少当前索引的可引用快照，请检查索引或选择其他知识库。',
    EVIDENCE_UNAVAILABLE: '本轮引用片段不可用，请检查原文和索引快照。',
    KNOWLEDGE_SEARCH_FAILED: '知识检索失败，请检查向量模型、服务商额度和索引状态。',
    MODEL_CALL_FAILED: '回答模型调用失败，请检查模型服务和服务商额度。',
    RESOURCE_UNAVAILABLE: '模型或知识库已变化或不可用，请检查配置后重新发布。',
    RESPONSE_MODE_UNAVAILABLE: '当前模型或连接不支持已发布的响应模式，请调整配置后重新发布。',
    ANSWER_INVALID: '模型未返回可用回答，请缩短问题后重试。',
    ROUTE_INVALID: '未能可靠识别能力或参数，请明确要测试的能力和参数；本轮没有启动任务。',
    CAPABILITY_UNAVAILABLE: '能力或固定版本已不可用，原参数摘要暂不展示，请检查配置。'
  }
  return hints[turn.errorCode] || ({ RUNNING: '正在理解需求并处理咨询…', INTERRUPTED: '本轮已停止或超时，可以继续提问。', FAILED: '本轮未完成，请检查资源或缩短问题后重试。' })[turn.status] || '暂无可展示内容。'
}

// 访客提示只说明接待状态，不展示管理端的模型、配额或知识索引诊断。
export function customerVisitorTurnHint(turn) {
  if (turn.status === 'INTERRUPTED') return '本轮已停止，可以继续咨询。'
  if (turn.status === 'FAILED' || turn.errorCode) return '暂时无法完成回答，请稍后再试或联系人工。'
  return turn.status === 'RUNNING' ? '正在为你准备回答…' : '本轮已停止，可以继续咨询。'
}

export function customerIntentRunnable(turn, turns) {
  return turn?.status === 'COMPLETED' && turn.intent?.state === 'READY' && !turn.intent.started
    && turn.id === turns?.at(-1)?.id
}

export function customerIntentEntries(intent) {
  return Object.entries(intent?.input || {}).map(([name, value]) => ({ name, value: String(value) }))
}

export function customerIntentLabel(intent, name) {
  return intent?.parameters?.find(parameter => parameter.name === name)?.label || name
}
