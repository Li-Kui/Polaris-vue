import request from '@/utils/platformRequest'

export function listConnector(query) {
  return request({
    url: '/platform/console/connector/list',
    method: 'get',
    params: query
  })
}

export function getConnector(id) {
  return request({
    url: '/platform/console/connector/' + id,
    method: 'get'
  })
}

export function addConnector(data) {
  return request({
    url: '/platform/console/connector',
    method: 'post',
    data: data
  })
}

export function updateConnector(data) {
  return request({
    url: '/platform/console/connector',
    method: 'put',
    data: data
  })
}

export function delConnector(id) {
  return request({
    url: '/platform/console/connector/' + id,
    method: 'delete'
  })
}

export function getConnectorUsages(id) {
  return request({
    url: `/platform/console/connector/${id}/usages`,
    method: 'get'
  })
}

export function invokeConnector(id, data) {
  return request({
    url: `/platform/console/connector/${id}/invoke`,
    method: 'post',
    data: data
  })
}
