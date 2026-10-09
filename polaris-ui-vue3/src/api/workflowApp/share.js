import request from '@/utils/request'

export function getWorkflowShareDefinition(definitionId) {
  return request({
    url: `/platform/workflow-shares/definition/${definitionId}`,
    method: 'get'
  })
}

export function updateWorkflowShareDefaults(definitionId, data) {
  return request({
    url: `/platform/workflow-shares/definition/${definitionId}`,
    method: 'put',
    data
  })
}

export function listWorkflowShares(workflowDefinitionId) {
  return request({
    url: '/platform/workflow-shares',
    method: 'get',
    params: { workflowDefinitionId }
  })
}

export function createWorkflowShare(data) {
  return request({
    url: '/platform/workflow-shares',
    method: 'post',
    data
  })
}

export function updateWorkflowShare(data) {
  return request({
    url: '/platform/workflow-shares',
    method: 'put',
    data
  })
}

export function deleteWorkflowShare(id) {
  return request({
    url: `/platform/workflow-shares/${id}`,
    method: 'delete'
  })
}
