import Cookies from 'js-cookie'
import {isPlatformConsolePath} from '@/utils/consoleRoute'

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
  const token = Cookies.get(PlatformTokenKey)
  if (!token) return token

  // 中台 JWT 到期后立即清理，避免页面继续携带失效令牌请求业务接口。
  if (isJwtExpired(token)) {
    Cookies.remove(PlatformTokenKey)
    return undefined
  }
  return token
}

export function setPlatformToken(token) {
  return Cookies.set(PlatformTokenKey, token)
}

export function removePlatformToken() {
  return Cookies.remove(PlatformTokenKey)
}

function isJwtExpired(token) {
  try {
    const parts = String(token).split('.')
    if (parts.length !== 3) return true
    const normalized = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    const bytes = Uint8Array.from(atob(padded), char => char.charCodeAt(0))
    const payload = JSON.parse(new TextDecoder().decode(bytes))
    return !Number.isFinite(payload.exp) || payload.exp * 1000 <= Date.now()
  } catch (error) {
    return true
  }
}

/**
 * 获取当前页面对应的认证请求头。
 * 中台与管理端复用 AI 页面时，原生 fetch / 上传组件也必须与 axios 使用相同的令牌选择规则。
 */
export function getAuthHeaders() {
  const platformToken = getPlatformToken()
  const adminToken = getToken()
  const isPlatform = isPlatformConsolePath(window.location.pathname)

  // 两套控制台严格使用各自令牌，防止中台会话失效后回退到管理端身份。
  if (isPlatform) {
    return platformToken ? { 'Platform-Token': platformToken } : {}
  }
  return adminToken ? { 'Authorization': 'Bearer ' + adminToken } : {}
}
