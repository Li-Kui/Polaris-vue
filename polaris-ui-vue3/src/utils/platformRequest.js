import axios from 'axios'
import {ElMessage, ElMessageBox, ElNotification} from 'element-plus'
import {getPlatformToken, removePlatformToken} from '@/utils/auth'
import errorCode from '@/utils/errorCode'
import {tansParams} from '@/utils/ruoyi'
import usePlatformUserStore from '@/store/modules/platformUser'

let downloadLoadingInstance
export let isRelogin = { show: false }

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'
// 创建中台 axios 实例
const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 15000
})

function showReloginDialog() {
  if (isRelogin.show) return
  isRelogin.show = true
  ElMessageBox.confirm('登录状态已过期，请重新登录后继续操作', '系统提示', {
    confirmButtonText: '重新登录',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    usePlatformUserStore().$reset()
    removePlatformToken()
    isRelogin.show = false
    location.href = '/platform/login'
  }).catch(() => {
    isRelogin.show = false
  })
}

// request 拦截器
service.interceptors.request.use(config => {
  const isToken = (config.headers || {}).isToken === false
  const platformToken = getPlatformToken()
  if (platformToken && !isToken) {
    config.headers['Platform-Token'] = platformToken
  }
  if (config.method === 'get' && config.params) {
    let url = config.url + '?' + tansParams(config.params)
    url = url.slice(0, -1)
    config.params = {}
    config.url = url
  }
  return config
}, error => {
  return Promise.reject(error)
})

// response 拦截器
service.interceptors.response.use(res => {
  const code = res.data.code || 200
  const msg = errorCode[code] || res.data.msg || errorCode['default']
  if (res.request.responseType === 'blob' || res.request.responseType === 'arraybuffer') {
    return res.data
  }
  if (code === 401) {
    showReloginDialog()
    return Promise.reject('无效的会话，或者会话已过期，请重新登录。')
  } else if (code === 500) {
    ElMessage({ message: msg, type: 'error' })
    return Promise.reject(new Error(msg))
  } else if (code === 601) {
    ElMessage({ message: msg, type: 'warning' })
    return Promise.reject(new Error(msg))
  } else if (code !== 200) {
    ElNotification.error({ title: msg })
    return Promise.reject('error')
  } else {
    return Promise.resolve(res.data)
  }
}, error => {
  if (error.response?.status === 401) {
    showReloginDialog()
    const message = error.response?.data?.msg || '登录状态已过期，请重新登录。'
    return Promise.reject(new Error(message))
  }
  let { message } = error
  if (message == "Network Error") {
    message = "后端接口连接异常"
  } else if (message.includes("timeout")) {
    message = "系统接口请求超时"
  } else if (message.includes("Request failed with status code")) {
    message = "系统接口" + message.substr(message.length - 3) + "异常"
  }
  ElMessage({ message: message, type: 'error', duration: 5 * 1000 })
  return Promise.reject(error)
})

export default service
