import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import vm from 'node:vm'
import {transformWithEsbuild} from 'vite'

// 使用项目已有编译器加载真实请求拦截器，避免依靠正则删 import 后产生无效测试代码。
const source = await readFile(new URL('../src/utils/platformRequest.js', import.meta.url), 'utf8')
const compiled = await transformWithEsbuild(source, 'platformRequest.js', { format: 'cjs', target: 'esnext', define: { 'import.meta.env.VITE_APP_BASE_API': '"/dev-api"' } })
function fixture(pathname) {
  const calls = { removed: 0, reset: 0, dialogs: 0, notices: 0, messages: 0 }
  let token = 'existing-platform-token'
  let response, request
  const service = { interceptors: {
    request: { use: handler => { request = handler } },
    response: { use: (ok, failure) => { response = { ok, failure } } }
  } }
  const modules = {
    axios: { defaults: { headers: {} }, create: () => service, isCancel: error => error?.__CANCEL__ === true },
    'element-plus': { ElMessage: () => { calls.messages++ }, ElNotification: { error: () => { calls.notices++ } }, ElMessageBox: { confirm: () => { calls.dialogs++; return Promise.reject('cancel') } } },
    '@/utils/auth': { getPlatformToken: () => token, removePlatformToken: () => { calls.removed++; token = undefined } },
    '@/utils/errorCode': { default: '接口错误' },
    '@/utils/ruoyi': { tansParams: () => '' },
    '@/store/modules/platformUser': () => ({ $reset: () => { calls.reset++ } })
  }
  const location = { pathname, href: '' }
  vm.runInNewContext(compiled.code, {
    module: { exports: {} }, exports: {}, window: { location }, location,
    require: name => { assert.ok(Object.hasOwn(modules, name), `未声明的请求依赖 ${name}`); return modules[name] }
  })
  return { calls, response, request, location, token: () => token }
}

test('接待页业务 401 清除无效平台会话，不弹出 iframe 内登录跳转', async () => {
  const f = fixture(`/platform/customer/${'a'.repeat(32)}`)
  await assert.rejects(Promise.resolve().then(() => f.response.ok({ data: { code: 401, msg: '登录过期' }, request: {} })))
  assert.equal(f.calls.removed, 1)
  assert.equal(f.calls.reset, 1)
  assert.equal(f.calls.dialogs, 0)
  assert.equal(f.location.href, '')
  assert.equal(f.token(), undefined)
})

test('接待页 HTTP 401 也交回新窗口登录引导', async () => {
  const f = fixture(`/platform/customer/${'a'.repeat(32)}`)
  await assert.rejects(f.response.failure({ response: { status: 401, data: { msg: '已失效' } } }))
  assert.equal(f.calls.removed, 1)
  assert.equal(f.calls.reset, 1)
  assert.equal(f.calls.dialogs, 0)
  assert.equal(f.location.href, '')
})

test('渠道 404 不清除仍有效的登录身份，也不跳转到登录表单', async () => {
  const f = fixture(`/platform/customer/${'a'.repeat(32)}`)
  await assert.rejects(Promise.resolve().then(() => f.response.ok({ data: { code: 404, msg: 'CHANNEL_UNAVAILABLE' }, request: {} })))
  assert.equal(f.calls.removed, 0)
  assert.equal(f.calls.dialogs, 0)
  assert.equal(f.calls.notices, 1)
})

test('管理控制台保持原重新登录确认流程及认证头', async () => {
  const f = fixture('/platform/console/customer-service')
  assert.equal(f.request({ headers: {}, method: 'post' }).headers['Platform-Token'], 'existing-platform-token')
  await assert.rejects(Promise.resolve().then(() => f.response.ok({ data: { code: 401 }, request: {} })))
  assert.equal(f.calls.dialogs, 1)
  assert.equal(f.calls.removed, 0)
  assert.equal(f.calls.reset, 0)
})
test('取消旧人物素材请求不弹出错误，不清除登录状态', async () => {
  const f = fixture('/platform/console/customer-service')
  await assert.rejects(f.response.failure({ __CANCEL__: true, code: 'ERR_CANCELED', config: { silentCancellation: true } }))
  assert.equal(f.calls.messages, 0); assert.equal(f.calls.notices, 0); assert.equal(f.calls.removed, 0)
})
