import request from '@/utils/platformRequest'

export function listApiKey(query) {
  return request({
    url: '/platform/console/apikey/list',
    method: 'get',
    params: query
  })
}

export function getApiKey(id) {
  return request({
    url: '/platform/console/apikey/' + id,
    method: 'get'
  })
}

export function addApiKey(data) {
  return request({
    url: '/platform/console/apikey',
    method: 'post',
    data: data
  })
}

export function updateApiKey(data) {
  return request({
    url: '/platform/console/apikey',
    method: 'put',
    data: data
  })
}

export function delApiKey(id) {
  return request({
    url: '/platform/console/apikey/' + id,
    method: 'delete'
  })
}
