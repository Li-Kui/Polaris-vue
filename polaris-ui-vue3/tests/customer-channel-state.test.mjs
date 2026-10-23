import test from 'node:test'
import assert from 'node:assert/strict'
import {
    customerChannelFrameAllowed,
    customerChannelLinks,
    customerChannelSettings
} from '../src/utils/customerChannelState.js'
import {isPlatformConsolePath} from '../src/utils/consoleRoute.js'

const code = 'a'.repeat(32)

test('独立链接和嵌入只含随机入口码，不嵌入租户身份或令牌', () => {
  const links = customerChannelLinks('https://service.example.com', '/prod-api', code)
  assert.equal(links.entry, `https://service.example.com/platform/customer/${code}`)
  assert.equal(links.embed, `https://service.example.com/prod-api/platform/runtime/customer-channels/${code}/embed`)
  assert.ok(links.html.includes(`src="${links.embed}"`))
  assert.ok(links.html.includes('allow="microphone"'))
  assert.doesNotMatch(links.html, /token|userId|tenantId|postMessage/i)
  assert.throws(() => customerChannelLinks('https://service.example.com', 'https://api.example.com', code), /同源/)
  for (const bad of ['', '../escape', `${code}"`]) assert.throws(() => customerChannelLinks('https://service.example.com', '/', bad))
})

test('拒绝外站直接嵌入前端页，只接受当前渠道的同源后端容器', () => {
  const origin = 'https://service.example.com'
  assert.equal(customerChannelFrameAllowed(code, origin, origin, `/prod-api/platform/runtime/customer-channels/${code}/embed`), true)
  assert.equal(customerChannelFrameAllowed(code, origin, origin, `/dev-api/platform/runtime/customer-channels/${code}/embed`), true)
  assert.equal(customerChannelFrameAllowed(code, origin, 'https://host.example.com', `/platform/runtime/customer-channels/${code}/embed`), false)
  assert.equal(customerChannelFrameAllowed(code, origin, origin, `/platform/runtime/customer-channels/${'b'.repeat(32)}/embed`), false)
  assert.equal(customerChannelFrameAllowed(code, origin, origin, '/platform/console/customer-service'), false)
  assert.equal(customerChannelFrameAllowed(code, origin, origin, `/platform/runtime/customer-channels/${code}/embed/evil`), false)
})

test('渠道配置保持空能力为空，只序列化可编辑字段', () => {
  assert.deepEqual(customerChannelSettings({ name: ' 渠道 ', origins: ' https://a.example.com\n\nhttp://localhost:4173 ', capabilityCodes: [], expiresAt: '2026-10-25T00:00:00.000Z', userId: 999 }), {
    name: '渠道', allowedOrigins: ['https://a.example.com', 'http://localhost:4173'], capabilityCodes: [], expiresAt: '2026-10-25T00:00:00.000Z'
  })
  assert.throws(() => customerChannelSettings({ expiresAt: 'not-a-date' }), /到期/)
})

test('接待页始终选择平台认证，不误用后台管理身份', () => {
  assert.equal(isPlatformConsolePath(`/platform/customer/${code}`), true)
  assert.equal(isPlatformConsolePath('/platform/console/customer-service'), true)
  assert.equal(isPlatformConsolePath('/platformManage/customer'), false)
  assert.equal(isPlatformConsolePath('/app/legacy-share'), false)
})
