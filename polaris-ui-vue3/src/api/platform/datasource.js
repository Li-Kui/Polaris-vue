import request from '@/utils/platformRequest'

export function listDatasource(query) {
  return request({
    url: '/platform/console/datasource/list',
    method: 'get',
    params: query
  })
}

export function getDatasource(id) {
  return request({
    url: '/platform/console/datasource/' + id,
    method: 'get'
  })
}

export function addDatasource(data) {
  return request({
    url: '/platform/console/datasource',
    method: 'post',
    data: data
  })
}

export function updateDatasource(data) {
  return request({
    url: '/platform/console/datasource',
    method: 'put',
    data: data
  })
}

export function delDatasource(id) {
  return request({
    url: '/platform/console/datasource/' + id,
    method: 'delete'
  })
}

export function testDatasource(data) {
  return request({
    url: '/platform/console/datasource/test',
    method: 'post',
    data: data
  })
}

export function queryDatasource(id, data) {
  return request({
    url: `/platform/console/datasource/${id}/query`,
    method: 'post',
    data: data
  })
}
