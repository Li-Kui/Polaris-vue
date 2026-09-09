import request from '@/utils/platformRequest'

export function listTenantUser(query) {
  return request({
    url: '/platform/console/user/list',
    method: 'get',
    params: query
  })
}

export function getTenantUser(id) {
  return request({
    url: '/platform/console/user/' + id,
    method: 'get'
  })
}

export function addTenantUser(data) {
  return request({
    url: '/platform/console/user',
    method: 'post',
    data: data
  })
}

export function updateTenantUser(data) {
  return request({
    url: '/platform/console/user',
    method: 'put',
    data: data
  })
}

export function delTenantUser(id) {
  return request({
    url: '/platform/console/user/' + id,
    method: 'delete'
  })
}
