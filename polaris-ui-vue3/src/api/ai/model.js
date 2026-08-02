import request from '@/utils/request'

// 查询模型配置列表
export function listModel(query) {
  return request({
    url: '/ai/model/list',
    method: 'get',
    params: query
  })
}

// 查询模型配置详情
export function getModel(id) {
  return request({
    url: '/ai/model/' + id,
    method: 'get'
  })
}

// 新增模型配置
export function addModel(data) {
  return request({
    url: '/ai/model',
    method: 'post',
    data: data
  })
}

// 修改模型配置
export function updateModel(data) {
  return request({
    url: '/ai/model',
    method: 'put',
    data: data
  })
}

// 删除模型配置
export function delModel(id) {
  return request({
    url: '/ai/model/' + id,
    method: 'delete'
  })
}

// 设为默认聊天模型
export function setDefaultChat(id) {
  return request({
    url: `/ai/model/${id}/default`,
    method: 'put'
  })
}

// 设为默认向量模型
export function setDefaultEmbedding(id) {
  return request({
    url: `/ai/model/${id}/defaultEmbedding`,
    method: 'put'
  })
}

// 设为默认绘图模型
export function setDefaultImage(id) {
  return request({
    url: `/ai/model/${id}/defaultImage`,
    method: 'put'
  })
}

// 查询当前登录用户可用的模型列表
export function listAvailableModel() {
  return request({
    url: '/ai/model/list/available',
    method: 'get'
  })
}

// 根据提供商、API Key、Base URL 拉取远程可用模型列表
export function fetchRemoteModels(data) {
  return request({
    url: '/ai/model/list/remote',
    method: 'post',
    data: data,
    timeout: 15000
  })
}
