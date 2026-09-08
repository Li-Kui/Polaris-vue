import {createApp, h} from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '../../src/assets/styles/element-ui.scss'
import './style.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import WorkflowPage from '../../src/components/workflow/WorkflowPage.vue'
import WorkflowWorkbench from '../../src/components/workflow/WorkflowWorkbench.vue'
import WorkflowExecutionMonitor from '../../src/components/workflow/WorkflowExecutionMonitor.vue'
import request from '../../src/utils/request'

const searchParams = new URLSearchParams(window.location.search)
const tenantMode = searchParams.get('scope') === 'tenant'
const listMode = searchParams.get('view') === 'list'
const agentMode = searchParams.get('view') === 'agent'
const monitorMode = searchParams.get('view') === 'monitor'
const emptyDatasource = searchParams.get('emptyDatasource') === '1'
if (searchParams.get('theme') === 'dark') document.documentElement.classList.add('dark')

const resourceCatalog = {
  MODEL: [resource('MODEL', '1', 'DeepSeek 官方聊天', 'deepseek-chat', true)],
  AGENT: tenantMode
    ? [resource('AGENT', '12', '租户订单助手', '当前租户智能体', false,
        {agentCode: 'TENANT_ORDER', modelName: 'deepseek-chat', hasTools: true,
          hasInternalReadTools: true, hasWriteTools: false, hasExternalTools: false,
          toolMethodCount: 1, internalReadToolMethodCount: 1,
          hasUnclassifiedTools: false, toolAccessMode: 'DISABLED',
          toolNotice: '该智能体包含只读工具；当前工作流节点不会执行这些工具'})]
    : [resource('AGENT', '11', '意图分发员', '使用现有默认聊天模型', true,
        {agentCode: 'INTENT_ROUTER', modelName: 'deepseek-chat', hasTools: true,
          hasInternalReadTools: true, hasWriteTools: true, hasExternalTools: true,
          toolMethodCount: 3, internalReadToolMethodCount: 1,
          hasUnclassifiedTools: false, toolAccessMode: 'INTERNAL_READ_ONLY',
          toolCallLimit: 5, toolResultLimitChars: 20000,
          toolNotice: '当前工作流可使用其中 1 个内部只读工具；其余工具因作用域、写操作、外部服务或未分类原因不会执行'})],
  KNOWLEDGE_BASE: [resource('KNOWLEDGE_BASE', '21', '风控知识库', '索引已就绪', true)],
  API_CONNECTOR: [resource('API_CONNECTOR', '31', '订单服务 API', 'https://api.example.test', false)],
  DATASOURCE: emptyDatasource
    ? []
    : [resource('DATASOURCE', '41', '用户只读数据库', 'MySQL · 只读', false)]
}

let bindingSequence = 0
const resourceBindings = [
  binding('MODEL', 'primary_model', '1'), binding('KNOWLEDGE_BASE', 'primary_knowledge', '21'),
  binding('API_CONNECTOR', 'primary_api', '31'), binding('DATASOURCE', 'primary_database', '41'),
  binding('AGENT', 'primary_agent', tenantMode ? '12' : '11')
]

function resource(kind, resourceId, name, description, shared, attributes = {}) {
  return {kind, resourceId, name, description, status: '1', available: true, shared, attributes}
}

function binding(resourceKind, resourceKey, resourceId) {
  return {id: ++bindingSequence, ownerType: tenantMode ? 'TENANT' : 'SYSTEM',
    ownerId: tenantMode ? 1001 : 0, tenantId: tenantMode ? 1001 : null,
    scopeType: 'WORKFLOW', definitionId: 99,
    environment: 'PROD', resourceKind,
    resourceKey, resourceId, bindingVersion: 1, status: 'ACTIVE', lockVersion: 0}
}

request.defaults.adapter = async config => {
  const url = config.url || ''
  const path = url.split('?')[0]
  let data = null
  if (path === '/ai/workflow/definitions') {
    data = workflowDefinitions
  } else if (/^\/ai\/workflow\/definitions\/\d+\/draft$/.test(path) && config.method === 'put') {
    const payload = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    const draft = JSON.parse(payload.definitionJson)
    const definitionId = Number(path.split('/')[4])
    const current = workflowDefinitions.find(item => item.id === definitionId)
    data = {
      ...(current || {}),
      id: definitionId,
      tenantId: tenantMode ? 1001 : null,
      workflowName: draft.metadata.name,
      workflowCode: draft.metadata.code,
      description: draft.metadata.description,
      draftRevision: Number(payload.expectedRevision || current?.draftRevision || 0) + 1,
      currentPublishedVersionId: current?.currentPublishedVersionId || 'version-1',
      draftJson: payload.definitionJson
    }
    if (current) Object.assign(current, data)
  } else if (path === '/ai/workflow/node-descriptors') {
    data = descriptors
  } else if (url.startsWith('/ai/workflow/resources')) {
    const kind = new URLSearchParams(url.split('?')[1] || '').get('kind')
    data = resourceCatalog[kind] || []
  } else if (url.startsWith('/ai/workflow/resource-bindings') && config.method === 'post') {
    const payload = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    const existing = resourceBindings.find(item => item.resourceKind === payload.resourceKind
      && item.resourceKey === payload.resourceKey && item.environment === payload.environment)
    if (existing) {
      Object.assign(existing, payload, {status: 'ACTIVE', lockVersion: existing.lockVersion + 1})
      data = existing
    } else {
      data = binding(payload.resourceKind, payload.resourceKey, payload.resourceId)
      Object.assign(data, payload)
      resourceBindings.push(data)
    }
  } else if (url.startsWith('/ai/workflow/resource-bindings')) {
    data = resourceBindings
  } else if (url.includes('/nodes/agent_review/tests')) {
    data = agentTestResult()
  } else if (url === '/ai/workflow/node-tests/agent-test-demo') {
    data = agentTestResult()
  } else if (path.endsWith('/node-runs')) {
    data = monitorMode ? monitorNodeRuns : []
  } else if (path.endsWith('/events')) {
    data = monitorMode ? monitorEvents : []
  } else if (path.endsWith('/artifacts')) {
    data = []
  } else if (path.includes('/executions/')) {
    data = monitorMode ? monitorExecution : {executionId: 'execution-demo', status: 'SUCCEEDED'}
  } else if (path === '/ai/workflow/executions' && config.method === 'get') {
    data = monitorMode ? [monitorExecution] : []
  } else if (path === '/ai/workflow/executions') {
    data = {executionId: 'execution-demo', status: 'RUNNING'}
  }
  return {data: {code: 200, data}, status: 200, statusText: 'OK', headers: {}, config,
    request: {responseType: config.responseType}}
}

const definition = {
  schemaVersion: '2.0',
  metadata: {
    code: 'order_risk_review',
    name: '订单风险处置流程',
    description: '综合订单、用户和知识库内容完成风险处置。',
    tags: ['风控']
  },
  inputs: {type: 'object', properties: {orderId: {type: 'string'}}},
  nodes: [
    node('order_api', 'http_get', '订单 API', 160, 70, 'primary_api'),
    node('user_database', 'database_query', '用户数据库', 160, 235, 'primary_database'),
    node('risk_knowledge', 'knowledge_retrieval', '风控知识库', 160, 400, 'primary_knowledge'),
    node('merge_data', 'transform', '合并数据', 430, 205),
    node('risk_analysis', 'llm', '大模型风险分析', 685, 205, 'primary_model'),
    node('risk_classifier', 'llm_classifier', '风险等级判断', 685, 405, 'primary_model'),
    node('manual_approval', 'approval', '人工审批', 920, 365)
  ],
  edges: [
    edge('__start__', 'order_api'), edge('__start__', 'user_database'), edge('__start__', 'risk_knowledge'),
    edge('order_api', 'merge_data'), edge('user_database', 'merge_data'), edge('risk_knowledge', 'merge_data'),
    edge('merge_data', 'risk_analysis'), edge('risk_analysis', 'risk_classifier'),
    edge('risk_classifier', 'manual_approval', 'CONDITION', '$.nodes.risk_classifier.output.branch == "high"'),
    edge('risk_classifier', '__end__', 'CONDITION', '', true), edge('manual_approval', '__end__')
  ],
  outputs: {},
  policies: {timeoutSeconds: 1800, maxNodeRuns: 200, maxParallelism: 10, tokenBudget: 100000, costBudget: 100}
}

const agentDefinition = {
  schemaVersion: '2.0',
  metadata: {
    code: 'agent_order_review',
    name: '智能体订单审核',
    description: '使用订单审核智能体判断订单是否可以通过。',
    tags: ['智能体']
  },
  inputs: {type: 'object', properties: {orderContent: {type: 'string', title: '订单内容'}}},
  nodes: [node('agent_review', 'agent', '订单审核助手', 430, 240, 'primary_agent')],
  edges: [edge('__start__', 'agent_review'), edge('agent_review', '__end__')],
  outputs: {},
  policies: {timeoutSeconds: 600, maxNodeRuns: 20, maxParallelism: 2, tokenBudget: 20000, costBudget: 20}
}

function agentTestResult() {
  return {
    testRunId: 'agent-test-demo',
    status: 'SUCCEEDED',
    mode: 'NODE',
    durationMs: 1260,
    sideEffect: 'NONE',
    schemaSource: 'NODE_CONTRACT',
    schemaDiagnostics: [],
    usage: {
      inputTokens: 128,
      outputTokens: 42,
      totalTokens: 170,
      toolAttempts: 1,
      toolCalls: 1,
      toolSucceeded: 1,
      toolFailed: 0,
      toolBlocked: 0,
      toolDurationMs: 86,
      toolResultTruncated: 0
    },
    output: {
      text: '订单信息完整，可以通过审核。建议保留当前风控记录以便后续追溯。',
      agentCode: 'ORDER_REVIEW',
      agentName: '订单审核助手'
    }
  }
}

const monitorExecution = {
  executionId: 'agent-execution-demo',
  workflowCode: 'agent_order_review',
  versionNo: 7,
  status: 'SUCCEEDED',
  createTime: '2026-09-06 15:20:00',
  inputJson: JSON.stringify({orderContent: '新客户首次下单，金额 680 元'}),
  outputJson: JSON.stringify({approved: true, reason: '订单信息完整'}),
  budgetJson: JSON.stringify({tokenBudget: 20000, maxNodeRuns: 20}),
  usageJson: JSON.stringify({totalTokens: 170, toolCalls: 2})
}

const monitorNodeRuns = [{
  nodeRunId: 'agent-node-run-demo',
  nodeId: 'agent_review',
  attemptNo: 1,
  status: 'SUCCEEDED',
  sideEffectStatus: 'NONE',
  errorMessage: ''
}]

const monitorEvents = [
  monitorEvent(1, 'WORKFLOW_STARTED'),
  monitorEvent(2, 'AGENT_TOOL_STARTED', {toolName: 'querySystemUsers', callNo: 1}),
  monitorEvent(3, 'AGENT_TOOL_SUCCEEDED', {
    toolName: 'querySystemUsers', callNo: 1, durationMs: 86, resultTruncated: true
  }),
  monitorEvent(4, 'AGENT_TOOL_STARTED', {toolName: 'querySensitiveOrder', callNo: 2}),
  monitorEvent(5, 'AGENT_TOOL_BLOCKED', {
    toolName: 'querySensitiveOrder', callNo: 2, durationMs: 2, reasonCode: 'PERMISSION_REVOKED'
  }),
  monitorEvent(6, 'WORKFLOW_SUCCEEDED')
]

function monitorEvent(sequenceNo, eventType, payload = {}) {
  return {
    sequenceNo,
    eventType,
    nodeId: eventType.startsWith('AGENT_TOOL_') ? 'agent_review' : null,
    nodeRunId: eventType.startsWith('AGENT_TOOL_') ? 'agent-node-run-demo' : null,
    payloadJson: JSON.stringify(payload),
    createTime: `2026-09-06 15:20:0${sequenceNo}`
  }
}

function node(id, type, name, x, y, resourceKey) {
  const kinds = {
    http_get: 'API_CONNECTOR', database_query: 'DATASOURCE', knowledge_retrieval: 'KNOWLEDGE_BASE',
    llm: 'MODEL', llm_classifier: 'MODEL', agent: 'AGENT'
  }
  return {
    id, type, typeVersion: '1.0', name, inputMapping: {}, config: type === 'llm_classifier'
      ? {branches: [{slug: 'high', description: '高风险'}, {slug: 'low', description: '低风险'}]}
      : type === 'agent'
        ? {task: '审核订单信息，说明是否可以通过；信息不足时明确列出需要补充的内容。', maxWaitSeconds: 300}
        : {},
    timeoutSeconds: 120, onError: 'FAIL', ui: {x, y},
    resourceRefs: resourceKey ? [{kind: kinds[type], key: resourceKey, required: true}] : []
  }
}

function edge(source, target, kind = 'NORMAL', expression = '', defaultBranch = false) {
  return {
    id: `${source}-${target}`, source, target, kind, default: defaultBranch,
    condition: kind === 'CONDITION' && !defaultBranch ? {expression, priority: 100, onError: 'FAIL'} : undefined
  }
}

const descriptors = [
  descriptor('llm', '大模型调用', 'ai', ['MODEL']),
  descriptor('agent', 'AI 智能体', 'ai', ['AGENT']),
  descriptor('llm_classifier', '大模型语义分类', 'ai', ['MODEL']),
  descriptor('knowledge_retrieval', '知识库检索', 'ai', ['KNOWLEDGE_BASE']),
  descriptor('http_get', 'HTTP GET', 'integration', ['API_CONNECTOR']),
  descriptor('http_request', 'HTTP 写请求', 'integration', ['API_CONNECTOR'], 'WRITE'),
  descriptor('database_query', '数据库只读查询', 'data', ['DATASOURCE']),
  descriptor('condition', '条件分支', 'control'),
  descriptor('parallel', '并行分支', 'control'),
  descriptor('join', '分支汇聚', 'control'),
  descriptor('loop', '循环', 'control'),
  descriptor('wait', '等待', 'control'),
  descriptor('approval', '人工审批', 'control'),
  descriptor('transform', '数据转换', 'data'),
  descriptor('artifact', '输出产物', 'data')
]

const workflowDefinitions = [
  workflowDefinition(99, '订单风险处置流程', 'order_risk_review', true, '综合订单、用户和知识库内容完成风险处置。'),
  workflowDefinition(100, '客户问题智能分流', 'customer_service_router', true, '识别咨询意图并路由到知识库、智能体或人工坐席。'),
  workflowDefinition(101, '合同审查草稿', 'contract_review_draft', false, '抽取合同条款并执行风险检查。')
]

function workflowDefinition(id, workflowName, workflowCode, published, description) {
  return {
    id, workflowName, workflowCode, description, draftRevision: published ? 6 : 2,
    currentPublishedVersionId: published ? `version-${id}` : null,
    updateTime: '2026-08-26 10:30:00'
  }
}

function descriptor(type, displayName, category, requiredResourceKinds = [], sideEffect = 'READ') {
  return {
    type, displayName, category, handlerVersion: '1.0', requiredResourceKinds, sideEffect,
    capabilities: ['CANCELLABLE', 'RETRYABLE', 'CHECKPOINT_SAFE'],
    configSchema: {type: 'object', properties: {}}
  }
}

const app = createApp({
  render() {
    if (listMode) {
      return h(WorkflowPage, {
        appearance: tenantMode ? 'platform' : 'admin',
        canEdit: true,
        canPublish: true,
        canExecute: true,
        canApprove: true
      })
    }
    if (monitorMode) {
      return h(WorkflowExecutionMonitor, {
        definitionId: 99,
        workflowName: '智能体订单审核',
        workflowCode: 'agent_order_review',
        canExecute: true
      })
    }
    return h(WorkflowWorkbench, {
      modelValue: {
        id: 99,
        tenantId: tenantMode ? 1001 : null,
        workflowName: (agentMode ? agentDefinition : definition).metadata.name,
        workflowCode: (agentMode ? agentDefinition : definition).metadata.code,
        draftRevision: 6,
        currentPublishedVersionId: 'version-1',
        draftJson: JSON.stringify(agentMode ? agentDefinition : definition)
      },
      descriptors,
      canEdit: true,
      canPublish: true,
      canExecute: true,
      canDebug: true,
      appearance: tenantMode ? 'platform' : 'admin'
    })
  }
})

Object.entries(ElementPlusIconsVue).forEach(([key, component]) => app.component(key, component))
app.use(ElementPlus, {locale: zhCn})
app.mount('#app')
