import request from '@/utils/request'

/**
 * 获取所有启用的工作流列表（前端聊天页面下拉选择使用）
 */
export function listActiveWorkflows() {
  return request({
    url: '/ai/workflow/list/active',
    method: 'get'
  })
}

/**
 * 分页条件查询工作流配置列表（管理后台使用）
 */
export function listWorkflow(query) {
  return request({
    url: '/ai/workflow/list',
    method: 'get',
    params: query
  })
}

/**
 * 查询工作流配置详情
 */
export function getWorkflow(id) {
  return request({
    url: '/ai/workflow/' + id,
    method: 'get'
  })
}

/**
 * 新增工作流配置
 */
export function addWorkflow(data) {
  return request({
    url: '/ai/workflow',
    method: 'post',
    data: data
  })
}

/**
 * 修改工作流配置
 */
export function updateWorkflow(data) {
  return request({
    url: '/ai/workflow',
    method: 'put',
    data: data
  })
}

/**
 * 删除工作流配置
 */
export function delWorkflow(id) {
  return request({
    url: '/ai/workflow/' + id,
    method: 'delete'
  })
}

/**
 * 获取工作流的 Mermaid 拓扑图可视化文本
 */
export function getWorkflowGraph(workflowCode) {
  return request({
    url: '/ai/workflow/graph',
    method: 'get',
    params: { workflowCode }
  })
}

/**
 * 实时预览工作流草稿的 Mermaid 拓扑图可视化文本
 */
export function previewWorkflowGraph(graphJson) {
  return request({
    url: '/ai/workflow/preview-mermaid',
    method: 'post',
    data: { graphJson },
    headers: { repeatSubmit: false }
  })
}
