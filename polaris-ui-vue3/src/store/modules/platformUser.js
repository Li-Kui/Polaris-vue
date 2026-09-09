import {defineStore} from 'pinia'
import {getInfo, login, logout} from '@/api/platform/auth'
import {getPlatformToken, removePlatformToken, setPlatformToken} from '@/utils/auth'

const usePlatformUserStore = defineStore('platformUser', {
  state: () => ({
    token: getPlatformToken(),
    user: {},
    tenant: {},
    permissions: []
  }),
  actions: {
    // 登录
    login(userInfo) {
      const tenantCode = userInfo.tenantCode.trim()
      const username = userInfo.username.trim()
      const password = userInfo.password
      return new Promise((resolve, reject) => {
        login(tenantCode, username, password).then(res => {
          const data = res.data
          setPlatformToken(data.token)
          this.token = data.token
          this.user = data.user
          resolve()
        }).catch(error => {
          reject(error)
        })
      })
    },
    // 获取用户信息
    getInfo() {
      return new Promise((resolve, reject) => {
        getInfo().then(res => {
          const data = res.data
          this.user = data.user
          this.tenant = data.tenant
          this.permissions = data.permissions || []
          resolve(res)
        }).catch(error => {
          reject(error)
        })
      })
    },
    // 退出系统
    logOut() {
      return new Promise((resolve, reject) => {
        logout(this.token).then(() => {
          this.token = ''
          this.user = {}
          this.tenant = {}
          this.permissions = []
          removePlatformToken()
          resolve()
        }).catch(error => {
          this.token = ''
          this.user = {}
          this.tenant = {}
          removePlatformToken()
          resolve()
        })
      })
    }
  }
})

export default usePlatformUserStore
