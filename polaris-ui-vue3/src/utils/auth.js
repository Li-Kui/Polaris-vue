import Cookies from 'js-cookie'

const TokenKey = 'Admin-Token'
const PlatformTokenKey = 'Platform-Token'

export function getToken() {
  return Cookies.get(TokenKey)
}

export function setToken(token) {
  return Cookies.set(TokenKey, token)
}

export function removeToken() {
  return Cookies.remove(TokenKey)
}

// === 中台 Token 管理 ===
export function getPlatformToken() {
  return Cookies.get(PlatformTokenKey)
}

export function setPlatformToken(token) {
  return Cookies.set(PlatformTokenKey, token)
}

export function removePlatformToken() {
  return Cookies.remove(PlatformTokenKey)
}

/**
 * 获取当前页面对应的认证请求头。
 * 中台与管理端复用 AI 页面时，原生 fetch / 上传组件也必须与 axios 使用相同的令牌选择规则。
 */
export function getAuthHeaders() {
  const platformToken = getPlatformToken()
  const adminToken = getToken()
  const isPlatform = window.location.pathname.startsWith('/platform')

  if (isPlatform && platformToken) {
    return { 'Platform-Token': platformToken }
  }
  if (!isPlatform && adminToken) {
    return { 'Authorization': 'Bearer ' + adminToken }
  }
  if (platformToken) {
    return { 'Platform-Token': platformToken }
  }
  if (adminToken) {
    return { 'Authorization': 'Bearer ' + adminToken }
  }
  return {}
}
