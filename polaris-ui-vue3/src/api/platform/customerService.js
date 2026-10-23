import request from '@/utils/platformRequest'
import {getPlatformToken} from '@/utils/auth'
import {readCustomerAnswerStream} from '@/utils/customerAnswerStream'

const base = '/platform/console/customer-services'

export const listCustomerServices = () => request({ url: base, method: 'get' })
export const createCustomerService = config => request({ url: base, method: 'post', data: config })
export const updateCustomerService = (id, revision, config) => request({
  url: `${base}/${id}`, method: 'put', data: { revision, config }
})
export const publishCustomerService = (id, revision) => request({
  url: `${base}/${id}/publish`, method: 'post', data: { revision }
})
export const setCustomerServiceStatus = (id, revision, status) => request({
  url: `${base}/${id}/status`, method: 'put', data: { revision, status }
})
export const createCustomerSession = id => request({ url: `${base}/${id}/sessions`, method: 'post', data: {} })
export const getCustomerSession = id => request({ url: `${base}/sessions/${id}`, method: 'get' })
export const sendCustomerMessage = (id, requestId, text) => request({
  url: `${base}/sessions/${id}/messages`, method: 'post', data: { requestId, text }, timeout: 190000
})
export async function streamCustomerMessage(id, requestId, text, onEvent, signal) {
  // 浏览器不支持读取流时，在提交前选择完整回答；已经提交的流绝不自动补发。
  if (typeof fetch !== 'function' || typeof ReadableStream === 'undefined' || typeof TextDecoder === 'undefined') {
    return (await sendCustomerMessage(id, requestId, text)).data
  }
  const timeout = new AbortController(), timer = setTimeout(() => timeout.abort(), 190000)
  const abort = () => timeout.abort()
  signal?.addEventListener('abort', abort, { once: true })
  if (signal?.aborted) abort()
  try {
    const response = await fetch(`${import.meta.env.VITE_APP_BASE_API}${base}/sessions/${encodeURIComponent(id)}/messages/stream`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'Platform-Token': getPlatformToken() || '' },
      body: JSON.stringify({ requestId, text }), signal: timeout.signal, redirect: 'error', credentials: 'same-origin'
    })
    return await readCustomerAnswerStream(response, requestId, onEvent)
  } finally { clearTimeout(timer); signal?.removeEventListener('abort', abort) }
}
export const interruptCustomerTurn = (id, turnId) => request({
  url: `${base}/sessions/${id}/interrupt`, method: 'post', data: { turnId }
})
export const closeCustomerSession = id => request({ url: `${base}/sessions/${id}/close`, method: 'post' })
export const getCustomerSource = (id, requestId, citationId) => request({
  url: `${base}/sessions/${id}/messages/${encodeURIComponent(requestId)}/sources/${encodeURIComponent(citationId)}`,
  method: 'get'
})
export const listCustomerCapabilityWorkflows = () => request({ url: `${base}/capability-workflows`, method: 'get' })
export const listCustomerCapabilityVersions = id => request({ url: `${base}/capability-workflows/${id}/versions`, method: 'get' })
export const getCustomerCapabilityContract = (id, versionId) => request({
  url: `${base}/capability-workflows/${id}/versions/${encodeURIComponent(versionId)}/contract`, method: 'get'
})
export const checkCustomerCapability = capability => request({ url: `${base}/capability-check`, method: 'post', data: capability })
export const listCustomerSessionCapabilities = id => request({ url: `${base}/sessions/${id}/capabilities`, method: 'get' })
export const listCustomerOperations = id => request({ url: `${base}/sessions/${id}/operations`, method: 'get' })
export const startCustomerOperation = (id, data) => request({ url: `${base}/sessions/${id}/operations`, method: 'post', data })
export const getCustomerOperation = (id, requestId) => request({ url: `${base}/sessions/${id}/operations/${encodeURIComponent(requestId)}`, method: 'get' })
export const cancelCustomerOperation = (id, requestId) => request({ url: `${base}/sessions/${id}/operations/${encodeURIComponent(requestId)}/cancel`, method: 'post' })
export const runCustomerIntent = (id, requestId) => request({
  url: `${base}/sessions/${id}/messages/${encodeURIComponent(requestId)}/run`, method: 'post', data: {}
})

const channels = '/platform/console/customer-channels'
export const listCustomerChannels = id => request({ url: `${channels}/apps/${id}`, method: 'get' })
export const createCustomerChannel = (id, settings) => request({ url: `${channels}/apps/${id}`, method: 'post', data: settings })
export const updateCustomerChannel = (id, revision, status, settings) => request({
  url: `${channels}/items/${id}`, method: 'put', data: { revision, status, settings }
})
export const getCustomerChannelEntry = code => request({ url: `${channels}/entries/${encodeURIComponent(code)}`, method: 'get' })
export const createCustomerChannelSession = code => request({ url: `${channels}/entries/${encodeURIComponent(code)}/sessions`, method: 'post', data: {} })

export const listCustomerSpeechModels = () => request({ url: `${base}/speech-models`, method: 'get' })
export const getCustomerSpeechSettings = id => request({ url: `${base}/sessions/${id}/speech`, method: 'get' })
export const getCustomerRealtimeTicket = (id, requestId) => request({
  url: `${base}/sessions/${id}/speech/realtime-ticket`, method: 'post', data: { requestId }, timeout: 8000
})
export const transcribeCustomerSpeech = (id, requestId, wav) => request({
  url: `${base}/sessions/${id}/speech/requests/${encodeURIComponent(requestId)}`, method: 'post', data: wav,
  headers: { 'Content-Type': 'audio/wav' }, timeout: 190000
})
export const getCustomerSpeech = (id, requestId) => request({ url: `${base}/sessions/${id}/speech/requests/${encodeURIComponent(requestId)}`, method: 'get' })
export const cancelCustomerSpeech = (id, requestId) => request({ url: `${base}/sessions/${id}/speech/requests/${encodeURIComponent(requestId)}/cancel`, method: 'post', data: {} })

export const synthesizeCustomerSpeech = (id, requestId, turnId, partNo) => request({
  url: `${base}/sessions/${id}/speech/requests/${encodeURIComponent(requestId)}/playback`, method: 'post', data: partNo == null ? { turnId } : { turnId, partNo }, timeout: 190000
})
export const getCustomerSpeechAudio = (id, requestId) => request({
  url: `${base}/sessions/${id}/speech/requests/${encodeURIComponent(requestId)}/audio`, method: 'get', responseType: 'blob'
})

export const listCustomerAvatarAssets = () => request({ url: `${base}/avatar-assets`, method: 'get' })
export const uploadCustomerAvatarAsset = file => request({
  url: `${base}/avatar-assets`, method: 'post', params: { name: file.name }, data: file,
  headers: { 'Content-Type': 'application/octet-stream' }, timeout: 60000
})
export const getCustomerAvatarAsset = (id, signal) => request({ url: `${base}/avatar-assets/${encodeURIComponent(id)}`, method: 'get', responseType: 'blob', signal, silentCancellation: true })
export const getCustomerSessionAvatarAsset = (sessionId, id, signal) => request({
  url: `${base}/sessions/${encodeURIComponent(sessionId)}/avatar-assets/${encodeURIComponent(id)}`, method: 'get', responseType: 'blob', signal, silentCancellation: true
})

export const listCustomerRecords = (appId, before, signal) => request({
  url: `${base}/${appId}/records`, method: 'get', params: before ? { before } : {}, signal, silentCancellation: true
})
export const getCustomerRecord = (appId, sessionId, signal) => request({
  url: `${base}/${appId}/records/${encodeURIComponent(sessionId)}`, method: 'get', signal, silentCancellation: true
})
