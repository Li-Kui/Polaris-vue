import request from '@/utils/request'
import {getAuthHeaders} from '@/utils/auth'

const lastWorkflowSequences = new Map()

/**
 * 获取所有启用的工作流列表（前端聊天页面下拉选择使用）
 */
export function listActiveWorkflows() {
  return request({
    url: '/ai/workflow/list/active',
    method: 'get'
  })
}

export function listWorkflowExecutors() {
  return request({
    url: '/ai/workflow/executors/available',
    method: 'get'
  })
}

/**
 * 分页条件查询工作流配置列表（管理后台使用）
 */
export function listWorkflow(query) {
  return request({
    url: '/ai/workflow/list',
    method: 'get',
    params: query
  })
}

/**
 * 查询工作流配置详情
 */
export function getWorkflow(id) {
  return request({
    url: '/ai/workflow/' + id,
    method: 'get'
  })
}

/**
 * 新增工作流配置
 */
export function addWorkflow(data) {
  return request({
    url: '/ai/workflow',
    method: 'post',
    data: data
  })
}

/**
 * 修改工作流配置
 */
export function updateWorkflow(data) {
  return request({
    url: '/ai/workflow',
    method: 'put',
    data: data
  })
}

/**
 * 删除工作流配置
 */
export function delWorkflow(id) {
  return request({
    url: '/ai/workflow/' + id,
    method: 'delete'
  })
}

/**
 * 获取工作流的 Mermaid 拓扑图可视化文本
 */
export function getWorkflowGraph(workflowCode) {
  return request({
    url: '/ai/workflow/graph',
    method: 'get',
    params: { workflowCode }
  })
}

/**
 * 实时预览工作流草稿的 Mermaid 拓扑图可视化文本
 */
export function previewWorkflowGraph(graphJson) {
  return request({
    url: '/ai/workflow/preview-mermaid',
    method: 'post',
    data: { graphJson },
    headers: { repeatSubmit: false }
  })
}

export function listPendingWorkflowApprovals() {
  return request({
    url: '/ai/workflow/approvals/pending',
    method: 'get'
  })
}

export function cancelWorkflowExecution(executionId) {
  return request({
    url: `/ai/workflow/executions/${encodeURIComponent(executionId)}/cancel`,
    method: 'post'
  })
}

export function streamWorkflowExecution(data, onEvent, signal) {
  return postWorkflowStream('/ai/workflow/executions/stream', data, onEvent, signal)
}

export function streamWorkflowApproval(executionId, approvalId, data, onEvent, signal) {
  const path = `/ai/workflow/executions/${encodeURIComponent(executionId)}`
    + `/approvals/${encodeURIComponent(approvalId)}/decision`
  return postWorkflowStream(path, data, onEvent, signal)
}

export function streamWorkflowTestApproval(executionId, approvalId, data, onEvent, signal) {
  const path = `/ai/workflow/executions/${encodeURIComponent(executionId)}`
    + `/test-approvals/${encodeURIComponent(approvalId)}/decision`
  return postWorkflowStream(path, data, onEvent, signal)
}

async function postWorkflowStream(path, data, onEvent, signal) {
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  const response = await fetch(baseUrl + path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...getAuthHeaders()
    },
    body: JSON.stringify(data),
    signal
  })
  if (!response.ok) {
    const raw = await response.text()
    let message = raw || `HTTP ${response.status}`
    try {
      const parsed = JSON.parse(raw)
      message = parsed.msg || parsed.message || message
    } catch (ignored) {}
    throw new Error(message)
  }
  if (!response.body) throw new Error('浏览器不支持流式响应')

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    const normalized = buffer.replace(/\r\n/g, '\n')
    const frames = normalized.split('\n\n')
    buffer = frames.pop() || ''
    for (const frame of frames) dispatchFrame(frame, onEvent)
    if (done) break
  }
  if (buffer.trim()) dispatchFrame(buffer, onEvent)
}

function dispatchFrame(frame, onEvent) {
  let eventName = 'message'
  const dataLines = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (dataLines.length === 0) return
  const raw = dataLines.join('\n')
  let payload
  try {
    payload = JSON.parse(raw)
  } catch (error) {
    throw new Error(`工作流事件格式错误: ${error.message}`)
  }
  if (payload.event && payload.event !== eventName) {
    throw new Error(`工作流事件名称不一致: ${eventName}`)
  }
  const executionId = payload.executionId
  const sequence = Number(payload.sequence)
  if (executionId && Number.isFinite(sequence)) {
    const previous = lastWorkflowSequences.get(executionId) || 0
    if (sequence <= previous) return
    lastWorkflowSequences.set(executionId, sequence)
    if (lastWorkflowSequences.size > 500) {
      lastWorkflowSequences.delete(lastWorkflowSequences.keys().next().value)
    }
  }
  onEvent(payload.event || eventName, payload)
}
