// 只提交管理员配置，不把审批快照、预检文本或运行数据混入保存请求。
export function bindingPayload(rows) {
  return rows.map(row => ({toolId: row.toolId, purpose: row.purpose,
    queryArgument: row.queryArgument?.trim() || null, fixedArgumentsJson: row.fixedArgumentsJson}))
}
export function bindingsChanged(rows, baseline) {
  return rows.some(row => row.refreshSnapshot === true) || JSON.stringify(bindingPayload(rows)) !== baseline
}
export function invocationSourceLabel(source) {
  return ({MANUAL: '手工', WORKFLOW: '工作流', AGENT_WORKFLOW: '协作智能体', AGENT_CHAT: '对话协作'})[source] || '未知来源'
}
export function isWorkflowInvocation(source) {return source === 'WORKFLOW' || source === 'AGENT_WORKFLOW'}
