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
