import request from '@/utils/request'

export function listTenant(query) {
  return request({
    url: '/system/platform/tenant/list',
    method: 'get',
    params: query
  })
}

export function getTenant(tenantId) {
  return request({
    url: '/system/platform/tenant/' + tenantId,
    method: 'get'
  })
}

export function addTenant(data) {
  return request({
    url: '/system/platform/tenant',
    method: 'post',
    data: data
  })
}

export function updateTenant(data) {
  return request({
    url: '/system/platform/tenant',
    method: 'put',
    data: data
  })
}

export function delTenant(tenantId) {
  return request({
    url: '/system/platform/tenant/' + tenantId,
    method: 'delete'
  })
}
