import request from '@/utils/request'

// 查询当前登录用户可用的模型列表
export function listAvailableModel() {
  return request({
    url: '/ai/model/list/available',
    method: 'get'
  })
}

// 查询当前用户可用于知识库的向量模型列表
export function listAvailableEmbeddingModel() {
  return request({
    url: '/ai/model/list/availableEmbedding',
    method: 'get'
  })
}

// Model Center V2：稳定列表、Editor 上下文与 Aggregate。
export function listModelAggregates() {
  return request({ url: '/ai/model-center/models', method: 'get' })
}

export function getModelEditorContext() {
  return request({ url: '/ai/model-center/editor-context', method: 'get' })
}

export function getModelAggregate(id) {
  return request({ url: `/ai/model-center/models/${id}`, method: 'get' })
}

export function createModelAggregate(data) {
  return request({ url: '/ai/model-center/models', method: 'post', data })
}

export function updateModelAggregate(id, data) {
  return request({ url: `/ai/model-center/models/${id}`, method: 'put', data })
}

export function deleteModelAggregate(id) {
  return request({ url: `/ai/model-center/models/${id}`, method: 'delete' })
}

export function setCapabilityDefault(capabilityCode, modelId) {
  return request({
    url: `/ai/model-center/defaults/${capabilityCode}/${modelId}`,
    method: 'put'
  })
}

export function getCapabilityDefault(capabilityCode) {
  return request({
    url: `/ai/model-center/defaults/${capabilityCode}`,
    method: 'get',
    silentError: true
  })
}

export function testModelDraft(data) {
  return request({ url: '/ai/model-center/model-test', method: 'post', data })
}

export function resolveSchemaOptions(data) {
  return request({ url: '/ai/model-center/options', method: 'post', data })
}
