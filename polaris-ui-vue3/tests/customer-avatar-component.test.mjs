import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import {readFile} from 'node:fs/promises'
import {compileScript, parse} from '@vue/compiler-sfc'
import {transformWithEsbuild} from 'vite'
import * as Vue from 'vue'
import * as avatarUtils from '../src/utils/customerAvatarState.js'

const source = await readFile(new URL('../src/views/platform/customerService/CustomerAvatar.vue', import.meta.url), 'utf8')
const script = compileScript(parse(source).descriptor, { id: 'customer-avatar-test' }).content
const compiled = await transformWithEsbuild(script, 'avatar.js', { format: 'cjs', target: 'esnext' })
const tick = async () => { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() }
function fixture({ blocked = false, gif = false } = {}) {
  const props = Vue.reactive({ config: { name: '小美', coverAssetId: 'cover', idleAssetId: 'idle', speakingAssetId: 'speak', fit: 'CONTAIN' }, sessionId: 's1', state: 'IDLE', active: true })
  const urls = [], revoked = [], requests = [], events = {}, motion = { matches: false, addEventListener: (name, fn) => { events.motion = fn }, removeEventListener() {} }
  const page = { hidden: false, addEventListener: (name, fn) => { events[name] = fn }, removeEventListener() {} }
  let unmount, plays = 0, pauses = 0, loads = 0
  const video = { muted: false, pause() { pauses++ }, removeAttribute() {}, load() { loads++ }, async play() { plays++; if (blocked) throw new Error('NotAllowedError') } }
  const api = { getCustomerSessionAvatarAsset: async (session, id) => {
    requests.push({ session, id }); return new Blob(['synthetic'], { type: id === 'cover' ? 'image/png' : gif ? 'image/gif' : 'video/mp4' })
  }, getCustomerAvatarAsset: () => assert.fail('会话不得读取管理素材预览接口') }
  const modules = { vue: { ...Vue, onBeforeUnmount: fn => { unmount = fn } }, '@/api/platform/customerService': api, '@/utils/customerAvatarState': avatarUtils }
  const module = { exports: {} }
  vm.runInNewContext(compiled.code, { module, exports: module.exports, require: name => modules[name], window: { matchMedia: () => motion }, document: page,
    URL: { createObjectURL: () => { const value = `blob:test-${urls.length}`; urls.push(value); return value }, revokeObjectURL: value => revoked.push(value) }, AbortController })
  const scope = Vue.effectScope(), state = scope.run(() => module.exports.default.setup(props, { expose() {} }))
  state.video.value = video
  return { props, state, requests, events, page, urls, revoked, video, counts: () => ({ plays, pauses, loads }), dispose() { unmount(); scope.stop() } }
}
test('真实人物组件自动静音播放，后台立即停止并释放，回前台重新鉴权加载', async () => {
  const f = fixture(); await tick()
  assert.equal(f.state.media.value.type, 'video/mp4'); assert.equal(f.video.muted, true); assert.ok(f.counts().plays > 0)
  f.page.hidden = true; f.events.visibilitychange(); await tick()
  assert.equal(f.state.media.value.url, undefined); assert.equal(f.revoked.length, 2)
  f.page.hidden = false; f.events.visibilitychange(); await tick()
  assert.equal(f.requests.length, 4); assert.equal(f.state.media.value.type, 'video/mp4')
  f.dispose(); assert.equal(new Set(f.revoked).size, f.urls.length)
})
test('浏览器阻止自动播放时保留点击重试，视频错误时显示静态封面', async () => {
  const f = fixture({ blocked: true }); await tick()
  assert.equal(f.state.playBlocked.value, true)
  await f.state.startVideo(); assert.ok(f.counts().plays >= 2)
  f.state.failVideo(); assert.equal(f.state.imageUrl.value, f.state.media.value.coverUrl)
  f.dispose()
})
test('减少动态效果只读静态封面，GIF加载失败也退回封面', async () => {
  const f = fixture({ gif: true }); await tick()
  f.state.failImage({ currentTarget: { src: f.state.imageUrl.value } })
  assert.equal(f.state.imageUrl.value, f.state.media.value.coverUrl)
  f.events.motion({ matches: true }); await tick()
  assert.equal(f.state.media.value.type, 'image/png')
  assert.equal(f.state.media.value.id, 'cover')
  f.dispose()
})
test('更换会话清空全部旧Blob，新请求使用新会话的媒体入口', async () => {
  const f = fixture(); await tick(); f.props.sessionId = 's2'; await tick()
  assert.equal(f.revoked.length, 2)
  assert.deepEqual(f.requests.map(item => item.session), ['s1', 's1', 's2', 's2'])
  f.props.active = false; await tick(); assert.equal(f.state.media.value.url, undefined)
  f.dispose()
})
