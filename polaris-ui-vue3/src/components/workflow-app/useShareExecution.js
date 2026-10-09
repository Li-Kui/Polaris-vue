import {computed, onUnmounted, ref} from 'vue'
import {
    cancelShareExecution,
    getShareExecution,
    startShareExecution,
    streamShareExecutionEvents
} from '@/api/workflowApp/runtime'
import {parseRuntimeValue} from '@/utils/workflowRuntime'

const terminalStatuses = ['SUCCEEDED', 'FAILED', 'CANCELLED', 'REJECTED', 'NEEDS_ATTENTION']
const recoveryLifetime = 24 * 60 * 60 * 1000
const recoveryKey = code => `workflow-share-execution:${encodeURIComponent(code)}`
const legacyShareDenials = ['分享不存在或已失效', '该分享已被停用', '该分享已过期']

function readRecovery(code) {
  try {
    const record = JSON.parse(sessionStorage.getItem(recoveryKey(code)) || 'null')
    return record && typeof record.executionId === 'string' && /^[a-zA-Z0-9-]{1,80}$/.test(record.executionId)
      && Number.isFinite(record.savedAt) && Date.now() >= record.savedAt
      && Date.now() - record.savedAt < recoveryLifetime ? record.executionId : null
  } catch { return null }
}

export function useShareExecution(shareCode, handlers = {}) {
  const status = ref('idle')
  const execution = ref(null)
  const result = ref(null)
  const events = ref([])
  const errorMessage = ref('')
  const cancelling = ref(false)
  // 只保存本标签页、当前分享的执行 ID，不保存问题、附件凭据、结果或服务商地址。
  const pendingRecovery = ref(readRecovery(shareCode()))
  const isActive = computed(() => ['running', 'disconnected'].includes(status.value))
  let controller = null
  let sequence = 0
  let generation = 0

  onUnmounted(() => { generation++; controller?.abort() })

  const remember = id => {
    try { sessionStorage.setItem(recoveryKey(shareCode()), JSON.stringify({ executionId: id, savedAt: Date.now() })) }
    catch { /* 禁用存储时仍可在当前页面恢复。 */ }
  }
  const forgetRecovery = () => {
    if (isActive.value || pendingRecovery.value) return
    try { sessionStorage.removeItem(recoveryKey(shareCode())) } catch { /* 存储不可用 */ }
  }
  const clearUnavailable = () => {
    if (status.value !== 'unavailable') return
    forgetRecovery()
    status.value = 'idle'
    execution.value = null
    result.value = null
    events.value = []
    errorMessage.value = ''
  }

  const finish = async (id, token) => {
    try {
      const response = await getShareExecution(shareCode(), id)
      if (token !== generation) return
      execution.value = response.data || response
      if (!terminalStatuses.includes(execution.value.status)) {
        status.value = 'disconnected'
        errorMessage.value = '连接已断开，任务可能仍在运行。请恢复状态，不要重复提交。'
        return
      }
      controller?.abort()
      status.value = execution.value.status === 'SUCCEEDED' ? 'success'
        : execution.value.status === 'NEEDS_ATTENTION' ? 'attention'
        : execution.value.status === 'CANCELLED' ? 'cancelled' : 'error'
      result.value = parseRuntimeValue(execution.value.outputJson)
      errorMessage.value = status.value === 'attention'
        ? '执行结果需要管理员确认，服务商可能已完成处理。请勿重复运行，以免重复生成或计费。'
        : execution.value.errorMessage || '执行未成功完成，请修改输入后重试'
      const payload = { execution: execution.value, result: result.value }
      handlers.onSettled?.(payload)
      if (status.value === 'success') handlers.onCompleted?.(payload)
    } catch (error) {
      if (token !== generation) return
      // 兼容旧后端将分享拒绝返回 HTTP 200/code 500；只匹配已知固定业务提示，不泛化网络错误。
      const legacyDenial = Number(error.httpStatus) === 200 && Number(error.code) === 500 && legacyShareDenials.includes(error.message)
      if (legacyDenial || [403, 404].includes(Number(error.httpStatus)) || [403, 404].includes(Number(error.code))) {
        controller?.abort()
        status.value = 'unavailable'
        result.value = null
        events.value = []
        execution.value = { executionId: id }
        errorMessage.value = error.message || '该执行已不可访问，请联系管理员'
        handlers.onUnavailable?.(error)
        return
      }
      status.value = 'disconnected'
      errorMessage.value = error.message || '读取状态失败，请恢复状态'
    }
  }

  const subscribe = (id, token) => {
    controller?.abort()
    status.value = 'running'
    errorMessage.value = ''
    controller = streamShareExecutionEvents(shareCode(), id, sequence, {
      onMessage: event => {
        if (token !== generation) return
        sequence = Math.max(sequence, Number(event.sequenceNo) || 0)
        if (['NODE_STARTED', 'NODE_SUCCEEDED', 'NODE_FAILED'].includes(event.eventType)) {
          events.value.push({ time: new Date(event.createTime || Date.now()).toLocaleTimeString(), nodeId: event.nodeId, type: event.eventType })
        }
        handlers.onMessage?.(event)
        // 写节点的不确定结果需要人工处理；公开页停止等待，但不替管理员恢复执行。
        if (event.eventType === 'EXECUTION_NEEDS_ATTENTION') {
          controller?.abort()
          finish(id, token)
        }
      },
      onDone: () => finish(id, token),
      onError: () => finish(id, token)
    })
  }

  const run = async (input, options = {}) => {
    if (isActive.value || pendingRecovery.value || status.value === 'attention') return
    if (status.value === 'unavailable') return
    const token = ++generation
    controller?.abort()
    status.value = 'running'
    result.value = null
    execution.value = null
    events.value = []
    sequence = 0
    errorMessage.value = ''
    try {
      const response = await startShareExecution(shareCode(), { input, attachments: options.attachments })
      if (token !== generation) return
      execution.value = response.data || response
      if (!execution.value.executionId) throw new Error('服务端未返回执行 ID')
      remember(execution.value.executionId)
      handlers.onStarted?.({ execution: execution.value })
      subscribe(execution.value.executionId, token)
    } catch (error) {
      if (token !== generation) return
      status.value = 'error'
      errorMessage.value = error.message || '启动失败，请重试'
      handlers.onError?.(error)
    }
  }

  const resume = async id => {
    if (!id) return
    pendingRecovery.value = null
    remember(id)
    const token = ++generation
    controller?.abort()
    if (execution.value?.executionId !== id) { sequence = 0; events.value = []; result.value = null }
    status.value = 'running'
    execution.value = { executionId: id }
    await finish(id, token)
    if (token === generation && status.value === 'disconnected') subscribe(id, token)
  }

  const cancel = async () => {
    const id = execution.value?.executionId
    if (!id || !isActive.value || cancelling.value) return
    cancelling.value = true
    try {
      await cancelShareExecution(shareCode(), id)
      await resume(id)
    } catch (error) {
      errorMessage.value = error.message || '取消失败，请重试'
    } finally {
      cancelling.value = false
    }
  }

  return { status, execution, result, events, errorMessage, cancelling, isActive, pendingRecovery, forgetRecovery, clearUnavailable, run, resume, cancel }
}
