import request from '@/utils/request'

/**
 * 分页条件查询智能体列表
 */
export function listAgent(query) {
  return request({
    url: '/ai/agent/list',
    method: 'get',
    params: query
  })
}

/**
 * 获取所有启用的智能体列表（工作流节点编排使用）
 */
export function listAllAgents() {
  return request({
    url: '/ai/agent/list/all',
    method: 'get'
  })
}

/**
 * 获取智能体详情
 */
export function getAgent(id) {
  return request({
    url: '/ai/agent/' + id,
    method: 'get'
  })
}

/**
 * 新增智能体
 */
export function addAgent(data) {
  return request({
    url: '/ai/agent',
    method: 'post',
    data: data
  })
}

/**
 * 修改智能体
 */
export function updateAgent(data) {
  return request({
    url: '/ai/agent',
    method: 'put',
    data: data
  })
}

/**
 * 删除智能体
 */
export function delAgent(id) {
  return request({
    url: '/ai/agent/' + id,
    method: 'delete'
  })
}

/**
 * 获取系统中所有可用的工具类列表
 */
export function getAvailableTools() {
  return request({
    url: '/ai/agent/tools',
    method: 'get'
  })
}
