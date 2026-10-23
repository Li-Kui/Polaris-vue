export function customerRecordDuration(value) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) return '未记录'
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} 秒`
}
export function customerRecordTime(value) {
  if (!value) return '未记录'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '未记录' : date.toLocaleString()
}
export function customerRecordStatus(row) {
  if (row.overdue) return '超时待核定'
  return ({ ACTIVE: '未关闭', CLOSED: '已关闭', EXPIRED: '已到期', RUNNING: '处理中', COMPLETED: '完成', FAILED: '失败', INTERRUPTED: '已停止' })[row.status] || '未知状态'
}
export function customerSpeechStage(kind, partNo) {
  if (kind === 'TTS' && Number.isInteger(partNo) && partNo >= 1 && partNo <= 8) return `第 ${partNo} 段合成（不含播放）`
  return ({ STREAM: '实时识别（含录音）', STT: '短录音识别（不含录音）', TTS: '回答合成（不含播放）', CANCEL: '取消记录' })[kind] || '其他语音记录'
}
