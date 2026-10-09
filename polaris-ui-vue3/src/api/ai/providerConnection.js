import request from '@/utils/request'

export function listProviderConnections(params) {
  return request({ url: '/ai/provider-connection/list', method: 'get', params })
}

export function getProviderConnection(id) {
  return request({ url: `/ai/provider-connection/${id}`, method: 'get' })
}

export function createProviderConnection(data) {
  return request({ url: '/ai/provider-connection', method: 'post', data })
}

export function updateProviderConnection(id, data) {
  return request({ url: `/ai/provider-connection/${id}`, method: 'put', data })
}

export function changeProviderConnectionStatus(id, data) {
  return request({ url: `/ai/provider-connection/${id}/status`, method: 'put', data })
}

export function deleteProviderConnection(id) {
  return request({ url: `/ai/provider-connection/${id}`, method: 'delete' })
}

export function discoverProviderModels(id) {
  return request({
    url: `/ai/provider-connection/${id}/models`,
    method: 'get',
    silentError: true
  })
}
