import request from '@/utils/request'

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

// 重新构建单个文档的向量索引
export function rebuildDocument(id) {
  return request({
    url: `/ai/knowledge/document/${id}/rebuild`,
    method: 'post'
  })
}

// 重建整个知识库向量索引
export function rebuildKnowledge(id) {
  return request({
    url: `/ai/knowledge/${id}/rebuild`,
    method: 'post'
  })
}
