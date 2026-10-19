export function workflowUsageSummary(value) {
  let usage = value
  if (typeof usage === 'string') {
    try { usage = JSON.parse(usage) } catch { usage = null }
  }
  return [
    ['inputTokens', '输入 Token'], ['outputTokens', '输出 Token'], ['totalTokens', '总 Token']
  ].map(([key, label]) => {
    const raw = usage?.[key]
    const amount = typeof raw === 'number' || (typeof raw === 'string' && raw.trim()) ? Number(raw) : NaN
    return {key, label, value: Number.isFinite(amount) && amount >= 0 ? amount.toLocaleString('zh-CN') : '未记录'}
  })
}
