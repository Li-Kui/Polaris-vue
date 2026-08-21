import request from '@/utils/platformRequest'

// 登录
export function login(tenantCode, username, password) {
  return request({
    url: '/platform/login',
    headers: { isToken: false },
    method: 'post',
    data: { tenantCode, username, password }
  })
}

// 获取用户详细信息
export function getInfo() {
  return request({
    url: '/platform/getInfo',
    method: 'get'
  })
}

// 退出方法
export function logout() {
  return request({
    url: '/platform/logout',
    method: 'post'
  })
}
