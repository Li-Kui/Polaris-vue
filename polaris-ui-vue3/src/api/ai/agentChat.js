import request from '@/utils/request'
import {getAuthHeaders} from '@/utils/auth'
import {consumeAgentChatStream} from '@/components/agent/agentChat'

const base = '/ai/chat/collaboration'
const segment = value => encodeURIComponent(value)
export const prepareAgentChat = data => request({url: `${base}/prepare`, method: 'post', data, headers: {repeatSubmit: false}})
export const getAgentChatRequest = id => request({url: `${base}/requests/${segment(id)}`, method: 'get'})
export const getAgentChatRun = id => request({url: `${base}/runs/${segment(id)}`, method: 'get'})
export const cancelAgentChat = id => request({url: `${base}/requests/${segment(id)}/cancel`, method: 'post', headers: {repeatSubmit: false}})
export const getAgentChatSources = (conversation, message) => request({
  url: `${base}/conversations/${segment(conversation)}/messages/${segment(message)}/sources`, method: 'get'})
export const getAgentChatHistoryCitation = (conversation, message, origin, citation) => request({
  url: `${base}/conversations/${segment(conversation)}/messages/${segment(message)}/history-citations/${segment(origin)}/${segment(citation)}`, method: 'get'})

export async function streamAgentChat(confirmationId, input, onEvent, signal) {
  const response = await fetch(`${import.meta.env.VITE_APP_BASE_API || ''}${base}/stream`, {
    method: 'POST', headers: {'Content-Type': 'application/json', Accept: 'text/event-stream', ...getAuthHeaders()},
    body: JSON.stringify({confirmationId, input}), signal
  })
  if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream')) {
    throw new Error(`协作连接未建立（${response.status}），请核对原请求状态`)
  }
  await consumeAgentChatStream(response.body, onEvent)
}
