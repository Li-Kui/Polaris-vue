import request from '@/utils/request'
import {getAuthHeaders} from '@/utils/auth'

export function listSubWorkflows(parentId) {
  return request({url: '/ai/workflow/sub-workflows', method: 'get', params: {parentId}})
}

export function getSubWorkflowContract(definitionId, versionId) {
  return request({url: `/ai/workflow/sub-workflows/${definitionId}`, method: 'get', params: {versionId}})
}

export function listWorkflowDefinitions() {
  return request({
    url: '/ai/workflow/definitions',
    method: 'get'
  })
}

export function getWorkflowDefinition(definitionId) {
  return request({
    url: `/ai/workflow/definitions/${definitionId}`,
    method: 'get'
  })
}

export function createWorkflowDraft(definitionJson) {
  return request({
    url: '/ai/workflow/definitions',
    method: 'post',
    data: { definitionJson }
  })
}

export function updateWorkflowDraft(definitionId, definitionJson, expectedRevision) {
  return request({
    url: `/ai/workflow/definitions/${definitionId}/draft`,
    method: 'put',
    data: { definitionJson, expectedRevision },
    headers: { repeatSubmit: false }
  })
}

export function validateWorkflowDraft(definitionId) {
  return request({
    url: `/ai/workflow/definitions/${definitionId}/validate`,
    method: 'post',
    headers: { repeatSubmit: false }
  })
}

export function publishWorkflowDraft(definitionId, expectedRevision) {
  return request({
    url: `/ai/workflow/definitions/${definitionId}/publish`,
    method: 'post',
    data: { expectedRevision },
    headers: { repeatSubmit: false }
  })
}

export function listWorkflowVersions(definitionId) {
  return request({
    url: `/ai/workflow/definitions/${definitionId}/versions`,
    method: 'get'
  })
}

export function getWorkflowVersion(definitionId, versionId) {
  return request({
    url: `/ai/workflow/definitions/${definitionId}/versions/${versionId}`,
    method: 'get'
  })
}

export function rollbackWorkflowDraft(definitionId, versionId, expectedRevision) {
  return request({
    url: `/ai/workflow/definitions/${definitionId}/versions/${versionId}/rollback`,
    method: 'post',
    data: {expectedRevision},
    headers: {repeatSubmit: false}
  })
}

export function cloneWorkflowDefinition(definitionId, data) {
  return request({
    url: `/ai/workflow/definitions/${definitionId}/clone`,
    method: 'post',
    data,
    headers: {repeatSubmit: false}
  })
}

export function listWorkflowNodeDescriptors() {
  return request({
    url: '/ai/workflow/node-descriptors',
    method: 'get'
  })
}

export function resolveWorkflowNodeSchemas(definitionId, environment = 'PROD') {
  return request({
    url: `/ai/workflow/definitions/${definitionId}/node-schemas`,
    method: 'get',
    params: {environment}
  })
}

export function testWorkflowNode(definitionId, nodeId, data) {
  return request({
    url: `/ai/workflow/definitions/${definitionId}/nodes/${nodeId}/tests`,
    method: 'post',
    data,
    headers: { repeatSubmit: false }
  })
}

export function getWorkflowNodeTest(testRunId) {
  return request({
    url: `/ai/workflow/node-tests/${testRunId}`,
    method: 'get'
  })
}

export function cancelWorkflowNodeTest(testRunId) {
  return request({
    url: `/ai/workflow/node-tests/${testRunId}/cancel`,
    method: 'post',
    headers: { repeatSubmit: false }
  })
}

export function inferWorkflowNodeTestSchema(testRunId) {
  return request({
    url: `/ai/workflow/node-tests/${testRunId}/inferred-schema`,
    method: 'post',
    headers: { repeatSubmit: false }
  })
}

export function startWorkflowExecution(data) {
  return request({
    url: '/ai/workflow/executions',
    method: 'post',
    data,
    headers: { repeatSubmit: false }
  })
}

export function startWorkflowExecutionByCode(data) {
  return request({
    url: '/ai/workflow/executions/by-code',
    method: 'post',
    data,
    headers: { repeatSubmit: false }
  })
}

export function listWorkflowExecutions(params) {
  return request({
    url: '/ai/workflow/executions',
    method: 'get',
    params
  })
}

export function getWorkflowExecution(executionId) {
  return request({
    url: `/ai/workflow/executions/${executionId}`,
    method: 'get'
  })
}

export function listWorkflowNodeRuns(executionId) {
  return request({
    url: `/ai/workflow/executions/${executionId}/node-runs`,
    method: 'get'
  })
}

export function listWorkflowArtifacts(executionId) {
  return request({
    url: `/ai/workflow/executions/${executionId}/artifacts`,
    method: 'get'
  })
}

export function downloadWorkflowArtifact(executionId, artifactId) {
  return request({
    url: `/ai/workflow/executions/${executionId}/artifacts/${artifactId}/content`,
    method: 'get',
    responseType: 'blob'
  })
}

export function listWorkflowExecutionEvents(executionId, afterSequence = 0, limit = 200) {
  return request({
    url: `/ai/workflow/executions/${executionId}/events`,
    method: 'get',
    params: {afterSequence, limit}
  })
}

export function streamWorkflowExecutionEvents(executionId, afterSequence, handlers = {}) {
  const controller = new AbortController()
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  const url = `${baseUrl}/ai/workflow/executions/${encodeURIComponent(executionId)}`
    + `/events/stream?afterSequence=${Math.max(0, afterSequence || 0)}`
  ;(async () => {
    try {
      const response = await fetch(url, {
        headers: {...getAuthHeaders(), Accept: 'text/event-stream'},
        signal: controller.signal
      })
      if (!response.ok || !response.body) {
        throw new Error(`SSE 连接失败: ${response.status}`)
      }
      handlers.onOpen?.()
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const {done, value} = await reader.read()
        if (done) break
        buffer += decoder.decode(value, {stream: true}).replace(/\r\n/g, '\n')
        let boundary
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          const event = parseSseBlock(block)
          if (event) handlers.onEvent?.(event)
        }
      }
      handlers.onClose?.()
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
  let value = raw
  try {
    value = JSON.parse(raw)
  } catch (error) {
    // 非 JSON 控制消息保持为文本。
  }
  return {event, id, data: value}
}

export function cancelWorkflowExecution(executionId) {
  return request({
    url: `/ai/workflow/executions/${executionId}/cancel`,
    method: 'post',
    headers: { repeatSubmit: false }
  })
}

export function retryWorkflowExecution(executionId, idempotencyKey) {
  return request({
    url: `/ai/workflow/executions/${executionId}/retry`,
    method: 'post',
    data: {idempotencyKey: idempotencyKey || null},
    headers: {repeatSubmit: false}
  })
}

export function listWorkflowApprovals(status = '') {
  return request({
    url: '/ai/workflow/approvals',
    method: 'get',
    params: {status}
  })
}

export function getWorkflowApproval(approvalInstanceId) {
  return request({
    url: `/ai/workflow/approvals/${approvalInstanceId}`,
    method: 'get'
  })
}

export function listWorkflowApprovalDirectory(keyword = '') {
  return request({
    url: '/ai/workflow/approvals/directory',
    method: 'get',
    params: {keyword}
  })
}

export function decideWorkflowApproval(approvalInstanceId, data) {
  return request({
    url: `/ai/workflow/approvals/${approvalInstanceId}/decision`,
    method: 'post',
    data,
    headers: {repeatSubmit: false}
  })
}

export function repairWorkflowApproval(approvalInstanceId, data) {
  return request({
    url: `/ai/workflow/approvals/${approvalInstanceId}/repair`,
    method: 'post',
    data,
    headers: {repeatSubmit: false}
  })
}

export function reassignWorkflowApproval(approvalInstanceId, data) {
  return request({
    url: `/ai/workflow/approvals/${approvalInstanceId}/reassign`,
    method: 'post',
    data,
    headers: {repeatSubmit: false}
  })
}

export function restartWorkflowApprovalStage(approvalInstanceId, data) {
  return request({
    url: `/ai/workflow/approvals/${approvalInstanceId}/restart-stage`,
    method: 'post',
    data,
    headers: {repeatSubmit: false}
  })
}

export function remindWorkflowApproval(approvalInstanceId, data) {
  return request({
    url: `/ai/workflow/approvals/${approvalInstanceId}/remind`,
    method: 'post',
    data,
    headers: {repeatSubmit: false}
  })
}

export function listWorkflowResourceBindings(params) {
  return request({
    url: '/ai/workflow/resource-bindings',
    method: 'get',
    params
  })
}

export function listWorkflowResources(params) {
  return request({
    url: '/ai/workflow/resources',
    method: 'get',
    params
  })
}

/** 管理端创建可供工作流复用的全局 API 连接。 */
export function createWorkflowApiConnector(data) {
  return request({
    url: '/ai/workflow/connectors',
    method: 'post',
    data,
    headers: {repeatSubmit: false}
  })
}

/** 管理端创建可供工作流复用的全局数据库连接。 */
export function createWorkflowDatasource(data) {
  return request({
    url: '/ai/workflow/datasources',
    method: 'post',
    data,
    headers: {repeatSubmit: false}
  })
}

/** 在工作流中预览当前身份可访问数据库连接上的只读查询。 */
export function queryWorkflowDatasource(id, data) {
  return request({
    url: `/ai/workflow/datasources/${id}/query`,
    method: 'post',
    data,
    headers: {repeatSubmit: false}
  })
}

export function saveWorkflowResourceBinding(data) {
  return request({
    url: '/ai/workflow/resource-bindings',
    method: 'post',
    data,
    headers: { repeatSubmit: false }
  })
}

export function disableWorkflowResourceBinding(bindingId) {
  return request({
    url: `/ai/workflow/resource-bindings/${bindingId}/disable`,
    method: 'post',
    headers: { repeatSubmit: false }
  })
}

export function listWorkflowTriggers(params) {
  return request({
    url: '/ai/workflow/triggers',
    method: 'get',
    params
  })
}

export function createWorkflowTrigger(data) {
  return request({
    url: '/ai/workflow/triggers',
    method: 'post',
    data,
    headers: {repeatSubmit: false}
  })
}

export function updateWorkflowTriggerStatus(triggerId, data) {
  return request({
    url: `/ai/workflow/triggers/${triggerId}/status`,
    method: 'put',
    data,
    headers: {repeatSubmit: false}
  })
}

/** 聊天模式仅展示已经发布且实际可执行的工作流。 */
export async function listActiveWorkflows() {
  const response = await listWorkflowDefinitions()
  return {
    ...response,
    data: (response.data || []).filter(item =>
      item.currentPublishedVersionId && item.status !== 'DISABLED')
  }
}

export function listPendingWorkflowApprovals() {
  return listWorkflowApprovals('PENDING')
}

/**
 * 启动正式工作流执行，并将持久化事件转换给现有聊天渲染器。
 * 工作流执行本身不依赖此 SSE 连接。
 */
export async function streamWorkflowExecution(data, onEvent, signal) {
  const response = await startWorkflowExecutionByCode({
    workflowCode: data.workflowCode,
    input: {
      message: data.message,
      conversationId: data.conversationId,
      fileUrl: data.fileUrl || null,
      attachmentTokens: data.attachmentTokens || []
    },
    environment: data.environment || 'PROD',
    idempotencyKey: data.idempotencyKey || null
  })
  const executionId = response.data.executionId
  onEvent?.('execution_started', {executionId, payload: {status: response.data.status}})
  await consumeChatExecutionEvents(executionId, 0, onEvent, signal)
  return response
}

export async function streamWorkflowApproval(
  executionId,
  approvalInstanceId,
  data,
  onEvent,
  signal
) {
  const execution = await getWorkflowExecution(executionId)
  const afterSequence = execution.data.eventSequence || 0
  await decideWorkflowApproval(approvalInstanceId, {
    decision: data.approve ? 'APPROVE' : 'REJECT',
    comment: data.feedback || null,
    expectedLockVersion: null
  })
  await consumeChatExecutionEvents(executionId, afterSequence, onEvent, signal)
}

async function consumeChatExecutionEvents(executionId, afterSequence, onEvent, signal) {
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  const url = `${baseUrl}/ai/workflow/executions/${encodeURIComponent(executionId)}`
    + `/events/stream?afterSequence=${Math.max(0, afterSequence || 0)}`
  const response = await fetch(url, {
    headers: {...getAuthHeaders(), Accept: 'text/event-stream'},
    signal
  })
  if (!response.ok || !response.body) {
    throw new Error(`工作流事件连接失败: ${response.status}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const {done, value} = await reader.read()
    if (done) break
    buffer += decoder.decode(value, {stream: true}).replace(/\r\n/g, '\n')
    let boundary
    while ((boundary = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const event = parseSseBlock(block)
      if (event?.event === 'workflow') {
        await emitChatWorkflowEvent(executionId, event.data, onEvent)
      }
    }
  }
}

async function emitChatWorkflowEvent(executionId, event, onEvent) {
  const eventType = event.eventType
  const payload = parseJson(event.payloadJson)
  const envelope = {executionId, nodeId: event.nodeId || null, payload}
  if (eventType === 'NODE_STARTED') {
    onEvent?.('node_start', envelope)
  } else if (eventType === 'NODE_SUCCEEDED' || eventType === 'NODE_SKIPPED') {
    onEvent?.('node_done', envelope)
  } else if (eventType === 'NODE_FAILED') {
    onEvent?.('node_error', envelope)
  } else if (eventType === 'APPROVAL_CREATED') {
    onEvent?.('node_interrupt', {
      ...envelope,
      payload: {...payload, approvalId: payload.approvalInstanceId}
    })
  } else if (eventType === 'EXECUTION_REJECTED') {
    onEvent?.('workflow_rejected', envelope)
  } else if (eventType === 'EXECUTION_FAILED'
    || eventType === 'EXECUTION_CANCELLED'
    || eventType === 'EXECUTION_NEEDS_ATTENTION') {
    const execution = await getWorkflowExecution(executionId)
    onEvent?.('error', {
      ...envelope,
      payload: {message: execution.data.errorMessage || eventType}
    })
  } else if (eventType === 'EXECUTION_SUCCEEDED') {
    const execution = await getWorkflowExecution(executionId)
    onEvent?.('workflow_done', {
      ...envelope,
      payload: {result: workflowResult(execution.data.outputJson)}
    })
  }
}

function parseJson(value) {
  if (!value) return {}
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch (error) {
    return {value}
  }
}

function workflowResult(value) {
  const output = parseJson(value)
  if (typeof output === 'string') return output
  if (typeof output.result === 'string') return output.result
  if (typeof output.content === 'string') return output.content
  if (typeof output.text === 'string') return output.text
  return Object.keys(output).length ? JSON.stringify(output, null, 2) : ''
}
