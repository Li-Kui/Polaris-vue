// 只转换展示文案，状态判断、接口请求与持久化仍使用原始状态码。
const STATUS_LABELS = Object.freeze({
  QUEUED: '等待执行',
  PENDING: '等待执行',
  READY: '准备就绪',
  RUNNING: '执行中',
  WAITING: '等待中',
  RETRY_WAIT: '等待重试',
  WAITING_APPROVAL: '等待审批',
  WAITING_TIMER: '等待定时唤醒',
  WAITING_EVENT: '等待事件',
  RECOVERING: '恢复中',
  NEEDS_ATTENTION: '待人工确认',
  SUCCEEDED: '执行成功',
  FAILED: '执行失败',
  CANCELLED: '已取消',
  REJECTED: '已拒绝',
  SKIPPED: '已跳过'
})

export function workflowStatusLabel(status) {
  return Object.hasOwn(STATUS_LABELS, status) ? STATUS_LABELS[status] : status || '未知状态'
}
