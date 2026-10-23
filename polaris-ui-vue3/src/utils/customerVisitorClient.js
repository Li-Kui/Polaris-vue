import {readCustomerAnswerStream} from './customerAnswerStream.js'

// 凭证仅留在此页面实例内存中，不进 URL、宿主页、Cookie 或平台 Token 存储。
export function createCustomerVisitorClient(code, { apiBase = import.meta.env?.VITE_APP_BASE_API || '', fetcher = fetch } = {}) {
  if (!/^[a-f0-9]{32}$/.test(code || '')) throw new Error('无效的接待入口')
  if (typeof apiBase !== 'string' || apiBase && (!apiBase.startsWith('/') || apiBase.startsWith('//'))) throw new Error('访客接待需要前后端同源代理')
  const base = `${apiBase || ''}/platform/runtime/customer-visitors/${code}`
  let token = '', sessionId = '', creating = null, closing = null
  async function request(path, { data, audio, signal, blob = false, readStream, privateCall = true, keepalive = false, timeout = 15000 } = {}) {
    if (privateCall && (!token || !sessionId)) throw new Error('接待会话已结束，请重新开始')
    const controller = new AbortController(), abort = () => controller.abort()
    signal?.addEventListener('abort', abort, { once: true }); if (signal?.aborted) abort()
    const timer = setTimeout(abort, timeout)
    try {
      const response = await fetcher(`${base}${path}`, {
        method: data === undefined && audio === undefined ? 'GET' : 'POST', keepalive, credentials: 'omit', redirect: 'error', cache: 'no-store', signal: controller.signal,
        headers: { ...(audio === undefined ? data === undefined ? {} : { 'Content-Type': 'application/json' } : { 'Content-Type': 'audio/wav' }), ...(privateCall ? { 'Customer-Session-Token': token } : {}) },
        ...(audio !== undefined ? { body: audio } : data === undefined ? {} : { body: JSON.stringify(data) })
      })
      if (readStream) return await readStream(response)
      if (blob && response.ok) return response.blob()
      const value = await response.json()
      if (!response.ok || value.code !== 200) throw new Error(value.msg || '接待暂不可用，请重新检查入口')
      return value
    } finally { clearTimeout(timer); signal?.removeEventListener('abort', abort) }
  }
  function path(id) {
    if (!sessionId || id !== sessionId) throw new Error('不能读取其他接待会话')
    return `/sessions/${encodeURIComponent(id)}`
  }
  return {
    entry: () => request('/entry', { privateCall: false }),
    create() {
      if (sessionId) return this.get(sessionId)
      if (creating) return creating
      creating = request('/sessions', { privateCall: false, data: {} }).then(value => {
        const created = value.data
        if (!/^[A-Za-z0-9_-]{43}$/.test(created?.token || '') || !/^[a-f0-9-]{36}$/.test(created?.session?.id || '')) throw new Error('接待会话响应无效')
        token = created.token; sessionId = created.session.id
        return { data: created.session }
      }).finally(() => { creating = null })
      return creating
    },
    get: id => request(path(id)),
    async stream(id, requestId, text, onEvent, signal) {
      // 不支持读取流时仅在提交前选择完整 HTTP；已提交的流不得自动补发。
      if (typeof ReadableStream === 'undefined' || typeof TextDecoder === 'undefined') {
        return (await request(`${path(id)}/messages`, { data: { requestId, text }, signal, timeout: 190000 })).data
      }
      return request(`${path(id)}/messages/stream`, { data: { requestId, text }, signal, timeout: 190000,
        readStream: response => readCustomerAnswerStream(response, requestId, onEvent) })
    },
    interrupt: (id, turnId) => request(`${path(id)}/interrupt`, { data: { turnId } }),
    async close(id) {
      if (!sessionId) return
      const sessionPath = path(id)
      if (closing) return closing
      closing = request(`${sessionPath}/close`, { data: {}, keepalive: true }).then(result => {
        token = ''; sessionId = ''
        return result
      }).finally(() => { closing = null })
      return closing
    },
    source: (id, requestId, citationId) => request(`${path(id)}/messages/${encodeURIComponent(requestId)}/sources/${encodeURIComponent(citationId)}`),
    speechSettings: id => request(`${path(id)}/speech`),
    realtimeTicket: (id, requestId) => request(`${path(id)}/speech/realtime-ticket`, { data: { requestId }, timeout: 8000 }),
    transcribe: (id, requestId, wav) => request(`${path(id)}/speech/requests/${encodeURIComponent(requestId)}`, { audio: wav, timeout: 190000 }),
    speechGet: (id, requestId) => request(`${path(id)}/speech/requests/${encodeURIComponent(requestId)}`),
    speechCancel: async (id, requestId) => request(`${path(id)}/speech/requests/${encodeURIComponent(requestId)}/cancel`, { data: {}, keepalive: true }),
    synthesize: (id, requestId, turnId, partNo) => request(`${path(id)}/speech/requests/${encodeURIComponent(requestId)}/playback`, { data: partNo == null ? { turnId } : { turnId, partNo }, timeout: 190000 }),
    speechAudio: (id, requestId) => request(`${path(id)}/speech/requests/${encodeURIComponent(requestId)}/audio`, { blob: true }),
    asset: (id, signal) => request(`${path(sessionId)}/avatar-assets/${encodeURIComponent(id)}`, { signal, blob: true })
  }
}
