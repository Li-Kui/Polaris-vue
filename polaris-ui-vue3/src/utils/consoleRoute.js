/**
 * 判断是否为租户中台页面。
 *
 * 管理端的「中台管理」菜单使用 /platformManage/**，不能仅按
 * /platform 前缀判断，否则会误用租户中台的认证令牌。
 */
export function isPlatformConsolePath(pathname = '') {
  return pathname === '/platform/login'
    || pathname === '/platform/console'
    || pathname.startsWith('/platform/console/')
}
