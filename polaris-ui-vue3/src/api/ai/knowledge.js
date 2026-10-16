import request from '@/utils/request'

export function getKnowledgeRetrievalCapabilities(id) {
  return request({url: `/ai/knowledge/${id}/retrieval-capabilities`, method: 'get', silentError: true})
}

// 查询知识库列表
export function listKnowledge(query) {
  return request({
    url: '/ai/knowledge/list',
    method: 'get',
    params: query
  })
}

// 查询知识库详细信息
export function getKnowledge(id) {
  return request({
    url: `/ai/knowledge/${id}`,
    method: 'get'
  })
}

// 新增知识库
export function addKnowledge(data) {
  return request({
    url: '/ai/knowledge',
    method: 'post',
    data: data
  })
}

// 修改知识库
export function updateKnowledge(data) {
  return request({
    url: '/ai/knowledge',
    method: 'put',
    data: data
  })
}

// 删除知识库
export function delKnowledge(id) {
  return request({
    url: `/ai/knowledge/${id}`,
    method: 'delete'
  })
}

// 查询指定知识库下的文档列表
export function listDocuments(query) {
  return request({
    url: '/ai/knowledge/document/list',
    method: 'get',
    params: query
  })
}

// 删除指定文档
export function delDocument(id) {
  return request({
    url: `/ai/knowledge/document/${id}`,
    method: 'delete'
  })
}

// 幂等键不是认证凭证；旧浏览器缺少 randomUUID 时仍可生成本次请求标识。
export function newKnowledgeRebuildRequestKey() {
  return globalThis.crypto?.randomUUID?.()
    || `krb_${Date.now()}_${Math.random().toString(36).slice(2)}_${Math.random().toString(36).slice(2)}`
}

// 文档行入口实际重建所属全库。
export function rebuildDocument(id, requestKey = newKnowledgeRebuildRequestKey()) {
  return request({
    url: `/ai/knowledge/document/${id}/rebuild`,
    method: 'post',
    headers: {'Idempotency-Key': requestKey, repeatSubmit: false}
  })
}

// 重建整个知识库向量索引
export function rebuildKnowledge(id, requestKey = newKnowledgeRebuildRequestKey()) {
  return request({
    url: `/ai/knowledge/${id}/rebuild`,
    method: 'post',
    headers: {'Idempotency-Key': requestKey, repeatSubmit: false}
  })
}

export function getLatestKnowledgeRebuildTask(id) {
  return request({url: `/ai/knowledge/${id}/rebuild-tasks/latest`, method: 'get'})
}

export function uploadKnowledgeDocument(data, requestKey) {
  return request({url: '/ai/knowledge/document/upload', method: 'post', data,
    headers: {'Content-Type': 'multipart/form-data', 'Idempotency-Key': requestKey, repeatSubmit: false}, timeout: 60000})
}

export function recoverKnowledgeRebuildTask(id, taskId, requestKey) {
  return request({url: `/ai/knowledge/${id}/rebuild-tasks/${taskId}/recover`, method: 'post',
    headers: {'Idempotency-Key': requestKey, repeatSubmit: false}})
}

// 问题只放在 POST 正文；不写入通用防重复提交的 sessionStorage 缓存。
export function testKnowledgeRetrieval(id, data) {
  return request({
    url: `/ai/knowledge/${id}/retrieval-test`,
    method: 'post',
    data,
    headers: {repeatSubmit: false},
    timeout: 70000
  })
}

// 读取已保存的真实入库分块，不触发解析、Embedding 或重建。
export function getDocumentSnapshot(id, params) {
  return request({url: `/ai/knowledge/document/${id}/snapshot`, method: 'get', params})
}

// 单集合执行只接受服务端计划和两项显式确认，不接受名称/路由/标记等输入。
export function planKnowledgeVectorCleanup(id, generation) {
  return request({url: `/ai/knowledge/${id}/vector-collections/${generation}/cleanup/plan`, method: 'post', headers: {repeatSubmit: false}})
}
export function getKnowledgeVectorCleanup(id, generation) {
  return request({url: `/ai/knowledge/${id}/vector-collections/${generation}/cleanup`, method: 'get'})
}
export function executeKnowledgeVectorCleanup(id, generation, planId) {
  return request({url: `/ai/knowledge/${id}/vector-collections/${generation}/cleanup/execute`, method: 'post',
    data: {planId, confirmed: true, controlledStorageConfirmed: true}, headers: {repeatSubmit: false}, timeout: 45000})
}
export function stopExpiredKnowledgeVectorCleanup(id, generation) {
  return request({url: `/ai/knowledge/${id}/vector-collections/${generation}/cleanup/stop-expired`, method: 'post',
    data: {confirmed: true}, headers: {repeatSubmit: false}})
}

// 集合预览只有本地元数据读取，不生成删除计划；核验必须由用户单独点击。
export function previewKnowledgeVectorCleanup(id) {
  return request({url: `/ai/knowledge/${id}/vector-collections/cleanup-preview`, method: 'get'})
}

export function verifyKnowledgeVectorOwnership(id, buildGeneration) {
  return request({url: `/ai/knowledge/${id}/vector-collections/${buildGeneration}/verify-ownership`, method: 'post',
    headers: {repeatSubmit: false}, timeout: 30000})
}

// 预览只读；执行仅接受服务端计划摘要与明确确认，不提交文件路径/租户/collection。
export function previewKnowledgeSnapshotCleanup(id) {
  return request({url: `/ai/knowledge/${id}/snapshot-cleanup/preview`, method: 'post', headers: {repeatSubmit: false}})
}
export function getKnowledgeSnapshotCleanupStatus(id, beforeId) {
  return request({url: `/ai/knowledge/${id}/snapshot-cleanup/status`, method: 'get', params: beforeId == null ? {} : {beforeId}})
}

export function executeKnowledgeSnapshotCleanup(id, planId) {
  return request({url: `/ai/knowledge/${id}/snapshot-cleanup/execute`, method: 'post',
    data: {planId, confirmed: true}, headers: {repeatSubmit: false}})
}
