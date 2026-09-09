import router from './router'
import {ElMessage} from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import {getPlatformToken, getToken} from '@/utils/auth'
import {isPlatformConsolePath} from '@/utils/consoleRoute'
import {isHttp, isPathMatch} from '@/utils/validate'
import {isRelogin} from '@/utils/request'
import useUserStore from '@/store/modules/user'
import usePlatformUserStore from '@/store/modules/platformUser'
import useLockStore from '@/store/modules/lock'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

NProgress.configure({ showSpinner: false })

const whiteList = ['/login', '/register', '/login-demo']

const isWhiteList = (path) => {
  return whiteList.some(pattern => isPathMatch(pattern, path))
}

router.beforeEach(async (to, from) => {
  NProgress.start()

  // ==========================================
  // 分支 1：中台控制台路由处理 (/platform/login 或 /platform/console/**)
  // ==========================================
  const isPlatformRoute = isPlatformConsolePath(to.path)
  if (isPlatformRoute) {
    const platformToken = getPlatformToken()
    if (to.path === '/platform/login') {
      if (platformToken) {
        NProgress.done()
        return { path: '/platform/console/dashboard' }
      }
      return true
    }

    if (platformToken) {
      const platformUserStore = usePlatformUserStore()
      if (!platformUserStore.user.id) {
        try {
          await platformUserStore.getInfo()
        } catch (err) {
          await platformUserStore.logOut()
          NProgress.done()
          return `/platform/login?redirect=${to.fullPath}`
        }
      }
      return true
    } else {
      NProgress.done()
      return `/platform/login?redirect=${to.fullPath}`
    }
  }

  // ==========================================
  // 分支 2：管理后台路由处理 (原有逻辑)
  // ==========================================
  if (getToken()) {
    to.meta.title && useSettingsStore().setTitle(to.meta.title)
    const isLock = useLockStore().isLock
    if (to.path === '/login') {
      NProgress.done()
      return { path: '/' }
    }
    if (isWhiteList(to.path)) {
      return true
    }
    if (isLock && to.path !== '/lock') {
      NProgress.done()
      return { path: '/lock' }
    }
    if (!isLock && to.path === '/lock') {
      NProgress.done()
      return { path: '/' }
    }
    if (useUserStore().roles.length === 0) {
      isRelogin.show = true
      try {
        // 拉取user_info信息
        await useUserStore().getInfo()
        isRelogin.show = false
        // 根据roles权限生成可访问的路由
        const accessRoutes = await usePermissionStore().generateRoutes()
        accessRoutes.forEach(route => {
          if (!isHttp(route.path)) {
            router.addRoute(route)
          }
        })
        // 重新导航到目标路由，确保动态路由已注册
        return { ...to, replace: true }
      } catch (err) {
        await useUserStore().logOut()
        ElMessage.error(err)
        return { path: '/' }
      }
    }
    return true
  } else {
    // 没有token
    if (isWhiteList(to.path)) {
      // 在免登录白名单，直接进入
      return true
    }
    NProgress.done()
    return `/login?redirect=${to.fullPath}` // 否则全部重定向到登录页
  }
})

router.afterEach(() => {
  NProgress.done()
})
