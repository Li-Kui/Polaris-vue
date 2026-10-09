import axios from 'axios'
import {resolveChatAttachmentDownload} from '@/utils/workflowChatAttachments'

const runtimeRequest = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' }
})

function runtimeError(message, httpStatus, code) {
  const error = new Error(typeof message === 'string' && message ? message : '请求失败')
  error.httpStatus = httpStatus
  error.code = code
  return error
}

// 响应拦截器
runtimeRequest.interceptors.response.use(
  async response => {
    const data = await resolveChatAttachmentDownload(response.data, response.headers?.['content-disposition'])
    if (data?.code != null && data.code !== 200) {
      return Promise.reject(runtimeError(data.msg, response.status, data.code))
    }
    return data
  },
  async error => {
    const data = await resolveChatAttachmentDownload(error.response?.data, error.response?.headers?.['content-disposition'])
    const msg = data?.msg || error.message || '请求失败'
    return Promise.reject(runtimeError(msg, error.response?.status, data?.code))
  }
)

export function getShareManifest(shareCode) {
  return runtimeRequest({
    url: `/platform/runtime/shares/${shareCode}/manifest`,
    method: 'get'
  })
}

export function startShareExecution(shareCode, data) {
  return runtimeRequest({
    url: `/platform/runtime/shares/${shareCode}/executions`,
    method: 'post',
    data
  })
}

export function getShareExecution(shareCode, executionId) {
  return runtimeRequest({
    url: `/platform/runtime/shares/${shareCode}/executions/${executionId}`,
    method: 'get'
  })
}

export function getShareExecutionEvents(shareCode, executionId, afterSequence) {
  return runtimeRequest({
    url: `/platform/runtime/shares/${shareCode}/executions/${executionId}/events`,
    method: 'get',
    params: { afterSequence }
  })
}

export function cancelShareExecution(shareCode, executionId) {
  return runtimeRequest({
    url: `/platform/runtime/shares/${shareCode}/executions/${executionId}/cancel`,
    method: 'post'
  })
}

export function uploadShareFile(shareCode, file) {
  const formData = new FormData()
  formData.append('file', file)
  return runtimeRequest({
    url: `/platform/runtime/shares/${shareCode}/upload`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function uploadShareChatAttachment(shareCode, file) {
  const formData = new FormData()
  formData.append('file', file)
  return runtimeRequest({
    url: `/platform/runtime/shares/${shareCode}/attachments`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getShareChatAttachment(shareCode, token) {
  return runtimeRequest({
    url: `/platform/runtime/shares/${shareCode}/attachments/${token}`,
    method: 'get',
    responseType: 'blob'
  })
}

export function removeShareChatAttachment(shareCode, token) {
  return runtimeRequest({
    url: `/platform/runtime/shares/${shareCode}/attachments/${token}`,
    method: 'delete'
  })
}

/**
 * 轮询执行事件直到完成
 * @param {string} shareCode
 * @param {string} executionId
 * @param {number} afterSequence
 * @param {object} handlers - { onMessage, onDone, onError }
 * @param {number} intervalMs - 轮询间隔（默认 1500ms）
 * @returns {object} controller - { stop() } 可用于中止轮询
 */
export function pollShareExecutionEvents(shareCode, executionId, afterSequence, handlers, intervalMs = 1500) {
  let stopped = false
  let seq = afterSequence || 0

  const poll = async () => {
    while (!stopped) {
      try {
        const res = await getShareExecutionEvents(shareCode, executionId, seq)
        const events = res.data || res || []
        if (Array.isArray(events) && events.length > 0) {
          for (const event of events) {
            if (handlers.onMessage) handlers.onMessage(event)
            if (event.sequenceNo != null && event.sequenceNo > seq) {
              seq = event.sequenceNo
            }
          }
        }
        // 检查执行状态
        const execRes = await getShareExecution(shareCode, executionId)
        const exec = execRes.data || execRes
        const status = exec?.status
        if (['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status)) {
          if (handlers.onDone) handlers.onDone(exec)
          return
        }
      } catch (error) {
        if (handlers.onError) handlers.onError(error)
        return
      }
      await new Promise(resolve => setTimeout(resolve, intervalMs))
    }
  }

  poll()
  return { stop: () => { stopped = true } }
}

export function streamShareExecutionEvents(shareCode, executionId, afterSequence = 0, handlers = {}) {
  const controller = new AbortController()
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  const url = `${baseUrl}/platform/runtime/shares/${encodeURIComponent(shareCode)}`
    + `/executions/${encodeURIComponent(executionId)}/events/stream`
    + `?afterSequence=${Math.max(0, afterSequence || 0)}`
  ;(async () => {
    try {
      const response = await fetch(url, {
        headers: { Accept: 'text/event-stream' },
        signal: controller.signal
      })
      const contentType = (response.headers.get('Content-Type') || '').split(';')[0].trim().toLowerCase()
      if (!response.ok || !response.body || contentType !== 'text/event-stream') {
        let message = `SSE 连接失败: ${response.status}`
        let code
        if (contentType === 'application/json' || contentType.endsWith('+json')) {
          try {
            const data = await response.json()
            code = data?.code
            if (typeof data?.msg === 'string' && data.msg) message = data.msg
          } catch (error) {
            // 无法解析的代理错误响应保留通用提示，不展示原始内容。
          }
        }
        throw runtimeError(message, response.status, code)
      }
      handlers.onOpen?.()
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
        let boundary
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          const event = parseSseBlock(block)
          if (event?.event === 'workflow') handlers.onMessage?.(event.data)
          handlers.onEvent?.(event)
        }
      }
      handlers.onDone?.()
    } catch (error) {
      if (error.name !== 'AbortError') handlers.onError?.(error)
    }
  })()
  return controller
}

function parseSseBlock(block) {
  let event = 'message'
  let id = null
  const data = []
  block.split('\n').forEach(line => {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('id:')) id = line.slice(3).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  })
  if (!data.length) return null
  const raw = data.join('\n')
  try {
    return { event, id, data: JSON.parse(raw) }
  } catch (error) {
    return { event, id, data: raw }
  }
}
