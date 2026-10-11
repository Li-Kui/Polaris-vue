import request from '@/utils/platformRequest'

export function listMcpConnection(query) {
  return request({ url: '/platform/console/mcp/list', method: 'get', params: query })
}

export function getMcpConnection(id) {
  return request({ url: `/platform/console/mcp/${id}`, method: 'get' })
}

export function saveMcpConnection(data) {
  return request({ url: '/platform/console/mcp', method: data.id ? 'put' : 'post', data })
}

export function deleteMcpConnection(id, revision) {
  return request({ url: `/platform/console/mcp/${id}`, method: 'delete', params: { revision } })
}

export function probeMcpConnection(id, discover = false) {
  // 后端总时限最多60秒，额外留出清理与网关开销。
  return request({
    url: `/platform/console/mcp/${id}/${discover ? 'discover' : 'test'}`,
    method: 'post',
    timeout: 70000
  })
}

export function listMcpTools(connectionId, query) {
  return request({ url: `/platform/console/mcp/${connectionId}/tools`, method: 'get', params: query })
}

export function refreshMcpCatalog(connectionId) {
  return request({ url: `/platform/console/mcp/${connectionId}/refresh`, method: 'post', timeout: 70000 })
}

export function changeMcpToolPolicy(toolId, data) {
  return request({ url: `/platform/console/mcp/tools/${toolId}/policy`, method: 'put', data })
}

export function invokeMcpTool(toolId, data) {
  return request({ url: `/platform/console/mcp/tools/${toolId}/invoke`, method: 'post', data, timeout: 70000 })
}

export function listMcpInvocations(query) {
  return request({ url: '/platform/console/mcp/invocations/list', method: 'get', params: query })
}
