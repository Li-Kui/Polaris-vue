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
import request from '../../src/utils/request'

const searchParams = new URLSearchParams(window.location.search)
const tenantMode = searchParams.get('scope') === 'tenant'
const listMode = searchParams.get('view') === 'list'
const emptyDatasource = searchParams.get('emptyDatasource') === '1'
if (searchParams.get('theme') === 'dark') document.documentElement.classList.add('dark')

const resourceCatalog = {
  MODEL: [resource('MODEL', '1', 'DeepSeek 官方聊天', 'deepseek-chat', true)],
  AGENT: tenantMode
    ? [resource('AGENT', '12', '租户订单助手', '当前租户智能体', false)]
    : [resource('AGENT', '11', '意图分发员', '使用现有默认聊天模型', true)],
  KNOWLEDGE_BASE: [resource('KNOWLEDGE_BASE', '21', '风控知识库', '索引已就绪', true)],
  API_CONNECTOR: [resource('API_CONNECTOR', '31', '订单服务 API', 'https://api.example.test', false)],
  DATASOURCE: emptyDatasource
    ? []
    : [resource('DATASOURCE', '41', '用户只读数据库', 'MySQL · 只读', false)]
}

let bindingSequence = 0
const resourceBindings = [
  binding('MODEL', 'primary_model', '1'), binding('KNOWLEDGE_BASE', 'primary_knowledge', '21'),
  binding('API_CONNECTOR', 'primary_api', '31'), binding('DATASOURCE', 'primary_database', '41')
]

function resource(kind, resourceId, name, description, shared) {
  return {kind, resourceId, name, description, status: '1', available: true, shared, attributes: {}}
}

function binding(resourceKind, resourceKey, resourceId) {
  return {id: ++bindingSequence, ownerType: tenantMode ? 'TENANT' : 'SYSTEM',
    ownerId: tenantMode ? 1001 : 0, tenantId: tenantMode ? 1001 : null,
    environment: 'PROD', resourceKind,
    resourceKey, resourceId, bindingVersion: 1, status: 'ACTIVE', lockVersion: 0}
}

request.defaults.adapter = async config => {
  const url = config.url || ''
  let data = null
  if (url === '/ai/workflow/definitions') {
    data = workflowDefinitions
  } else if (url === '/ai/workflow/node-descriptors') {
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
  } else if (url.includes('/node-runs')) {
    data = []
  } else if (url.includes('/executions/')) {
    data = {executionId: 'execution-demo', status: 'SUCCEEDED'}
  } else if (url === '/ai/workflow/executions') {
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
    node('risk_knowledge', 'knowledge_rag', '风控知识库', 160, 400, 'primary_knowledge'),
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

function node(id, type, name, x, y, resourceKey) {
  const kinds = {
    http_get: 'API_CONNECTOR', database_query: 'DATASOURCE', knowledge_rag: 'KNOWLEDGE_BASE',
    llm: 'MODEL', llm_classifier: 'MODEL'
  }
  return {
    id, type, typeVersion: '1.0', name, inputMapping: {}, config: type === 'llm_classifier'
      ? {branches: [{slug: 'high', description: '高风险'}, {slug: 'low', description: '低风险'}]} : {},
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
  descriptor('knowledge_rag', '知识库检索', 'ai', ['KNOWLEDGE_BASE']),
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

function descriptor(type, displayName, category, requiredResourceKinds = [], sideEffect = 'NONE') {
  return {
    type, displayName, category, handlerVersion: '1.0', requiredResourceKinds, sideEffect,
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
    return h(WorkflowWorkbench, {
      modelValue: {
        id: 99,
        tenantId: tenantMode ? 1001 : null,
        workflowName: definition.metadata.name,
        workflowCode: definition.metadata.code,
        draftRevision: 6,
        currentPublishedVersionId: 'version-1',
        draftJson: JSON.stringify(definition)
      },
      descriptors,
      canEdit: true,
      canPublish: true,
      canExecute: true,
      appearance: tenantMode ? 'platform' : 'admin'
    })
  }
})

Object.entries(ElementPlusIconsVue).forEach(([key, component]) => app.component(key, component))
app.use(ElementPlus, {locale: zhCn})
app.mount('#app')
