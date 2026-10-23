// C1 工作流联调：仅合成资料检索及确定性复制，不含 LLM／MCP／API／写入节点。
export function definition({code = 'qa_c1_related_20261016', name = 'QA · C1 关联检索工作流', related = true,
  multiple = false, resultLimit = 3, mode = 'BALANCED', degradation = 'DENY'} = {}) {
  const properties = {query: {type: 'string', minLength: 1, maxLength: 2000}}
  if (related) {
    properties.relatedDocumentIds = {type: 'array', maxItems: 3, uniqueItems: true, items: {type: 'string', pattern: '^[1-9][0-9]{0,18}$'}}
    properties.filters = {type: 'object', properties: {documentIds: {type: 'array', maxItems: 100, items: {type: 'string'}}}, additionalProperties: false}
  }
  const mapping = {query: {expression: '$.input.query'}}
  if (related) {mapping.relatedDocumentIds = {expression: '$.input.relatedDocumentIds'}; mapping.filters = {expression: '$.input.filters'}}
  const resources = [{kind: 'KNOWLEDGE_BASE', key: 'qa.docs', required: true}]
  if (multiple) resources.push({kind: 'KNOWLEDGE_BASE', key: 'qa.other', required: true})
  return {
    schemaVersion: '2.0', metadata: {code, name, description: 'C1 独立合成只读验收；无回答模型或外部写入。', tags: ['QA', 'C1']},
    inputs: {type: 'object', properties, required: Object.keys(properties), additionalProperties: false},
    policies: {timeoutSeconds: 120, maxNodeRuns: 10, maxParallelism: 1},
    nodes: [
      {id: 'retrieve', type: 'knowledge_retrieval', typeVersion: '2.0', name: '检索合成资料', inputMapping: mapping,
        config: {configVersion: '2.0', retrievalMode: mode, resultLimit, contextBudgetMode: 'MANUAL', maxContextTokens: 4000,
          maxChunksPerDocument: 1, emptyPolicy: 'CONTINUE', degradationPolicy: degradation},
        timeoutSeconds: 60, onError: 'FAIL', retryPolicy: {maxAttempts: 1, backoff: 'FIXED', initialDelayMs: 0, maxDelayMs: 0}, resourceRefs: resources, ui: {x: 320, y: 240}},
      {id: 'inspect', type: 'transform', typeVersion: '1.0', name: '核对下游引用', inputMapping: {source: {expression: '$.nodes.retrieve.output'}},
        config: {}, timeoutSeconds: 30, onError: 'FAIL', retryPolicy: {maxAttempts: 1, backoff: 'FIXED', initialDelayMs: 0, maxDelayMs: 0}, resourceRefs: [], ui: {x: 680, y: 240}}
    ],
    edges: [{id: 'start', source: '__start__', target: 'retrieve', kind: 'NORMAL'},
      {id: 'downstream', source: 'retrieve', target: 'inspect', kind: 'NORMAL'},
      {id: 'finish', source: 'inspect', target: '__end__', kind: 'NORMAL'}],
    outputs: {report: {expression: '$.nodes.inspect.output'}, references: {expression: '$.nodes.retrieve.output.results'},
      knowledgeContext: {expression: '$.nodes.retrieve.output.knowledgeContext'}}
  }
}

export const query = '沧岚资料站2027年常规资料交接要提前多久预约？白榆协作中心4月8日算服务日吗？不要用试运行期限。'
export const missingQuery = '合成控制问题：量子陀螺仪的真空镀膜设备出口编码是什么？'
export const variants = [
  {id: 'legacy', options: {related: false}},
  {id: 'related', options: {}},
  {id: 'precise', options: {mode: 'PRECISE'}},
  {id: 'capacity', options: {resultLimit: 1}},
  {id: 'multiple', options: {multiple: true, degradation: 'ALLOW'}}
]
