import test from 'node:test'
import assert from 'node:assert/strict'
import {
    createCustomerAvatarMedia,
    customerAvatarAsset,
    customerAvatarConfiguration,
    customerAvatarState
} from '../src/utils/customerAvatarState.js'

const config = { name: '小美', coverAssetId: 'cover', idleAssetId: 'idle', listeningAssetId: 'listen', thinkingAssetId: 'think', speakingAssetId: 'speak' }
const blob = type => new Blob(['synthetic media'], { type })
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
function fixture(load = async id => blob(id === 'cover' ? 'image/png' : 'video/mp4')) {
  const loaded = [], urls = [], revoked = [], views = []
  const player = createCustomerAvatarMedia({ load: (id, signal) => { loaded.push({ id, signal }); return load(id, signal) },
    createUrl: value => { const url = `blob:test-${urls.length}`; urls.push({ url, value }); return url },
    revokeUrl: url => revoked.push(url), onMedia: value => views.push(value) })
  return { player, loaded, urls, revoked, views }
}
test('形象状态以实际录音和播报为准，合成等待使用思考素材', () => {
  const base = { enabled: true, visible: true, voiceState: 'OFF' }
  assert.equal(customerAvatarState(base), 'IDLE')
  assert.equal(customerAvatarState({ ...base, voiceState: 'SPEAKING', speechState: 'SYNTHESIZING' }), 'THINKING')
  assert.equal(customerAvatarState({ ...base, voiceState: 'SPEAKING' }), 'IDLE')
  assert.equal(customerAvatarState({ ...base, speaking: true }), 'SPEAKING')
  assert.equal(customerAvatarState({ ...base, speechState: 'RECORDING' }), 'LISTENING')
  assert.equal(customerAvatarState({ ...base, thinking: true }), 'THINKING')
  assert.equal(customerAvatarState({ ...base, voiceState: 'PAUSED' }), 'PAUSED')
  assert.equal(customerAvatarState({ ...base, speechState: 'UNKNOWN' }), 'PAUSED')
  for (const disabled of [{ visible: false }, { enabled: false }, { closing: true }]) assert.equal(customerAvatarState({ ...base, speaking: true, ...disabled }), 'STOPPED')
})
test('缺失状态沿用待机或封面，暂停／减少动态效果只选静态封面', () => {
  assert.equal(customerAvatarAsset(config, 'SPEAKING'), 'speak')
  assert.equal(customerAvatarAsset({ coverAssetId: 'cover', idleAssetId: 'idle' }, 'THINKING'), 'idle')
  assert.equal(customerAvatarAsset({ coverAssetId: 'cover' }, 'THINKING'), 'cover')
  for (const state of ['STOPPED', 'PAUSED']) assert.equal(customerAvatarAsset(config, state), 'cover')
  assert.equal(customerAvatarAsset(config, 'SPEAKING', true), 'cover')
  assert.equal(customerAvatarConfiguration().fit, 'CONTAIN')
})
test('切换状态保留封面、释放旧媒体，最多保留两个Blob且相同素材不重取', async () => {
  const f = fixture()
  await f.player.show(config, 'IDLE', true)
  assert.deepEqual(f.loaded.map(item => item.id), ['cover', 'idle'])
  await f.player.show({ ...config }, 'IDLE', true)
  assert.equal(f.loaded.length, 2)
  await f.player.show(config, 'SPEAKING', true)
  assert.deepEqual(f.loaded.map(item => item.id), ['cover', 'idle', 'speak'])
  assert.deepEqual(f.revoked, ['blob:test-1'])
  assert.equal(f.views.at(-1).coverUrl, 'blob:test-0')
  await f.player.show(config, 'PAUSED', true)
  assert.deepEqual(f.revoked, ['blob:test-1', 'blob:test-2'])
  assert.equal(f.views.at(-1).id, 'cover')
  f.player.dispose()
  assert.deepEqual(f.revoked, ['blob:test-1', 'blob:test-2', 'blob:test-0'])
})
test('停止、隐藏或卸载后迟到加载不创建Blob或恢复人物', async () => {
  for (const dispose of [false, true]) {
    const wait = deferred(), f = fixture(id => id === 'cover' ? blob('image/png') : wait.promise)
    const showing = f.player.show(config, 'IDLE', true)
    await new Promise(resolve => setImmediate(resolve))
    if (dispose) f.player.dispose(); else await f.player.show(config, 'STOPPED', false)
    assert.equal(f.loaded.at(-1).signal.aborted, true)
    wait.resolve(blob('video/mp4')); await showing
    assert.equal(f.urls.length, 1); assert.equal(f.views.at(-1).url, undefined)
    assert.deepEqual(f.revoked, ['blob:test-0'])
  }
})
test('旧状态响应晚于新状态时不能替换新人物素材', async () => {
  const wait = deferred(), f = fixture(id => id === 'idle' ? wait.promise : blob(id === 'cover' ? 'image/png' : 'image/gif'))
  const old = f.player.show(config, 'IDLE', true)
  await new Promise(resolve => setImmediate(resolve))
  await f.player.show(config, 'LISTENING', true)
  wait.resolve(blob('video/mp4')); await old
  assert.equal(f.views.at(-1).id, 'listen'); assert.equal(f.urls.length, 2)
  f.player.dispose()
})
test('错误响应、素材不可用和封面类型不符时不创建可执行URL', async () => {
  for (const type of ['application/json', 'text/html', 'image/svg+xml']) {
    const f = fixture(async () => blob(type)); await f.player.show(config, 'IDLE', true)
    assert.equal(f.views.at(-1).error, true); assert.equal(f.urls.length, 0)
  }
  const f = fixture(id => { if (id === 'cover') return blob('image/jpeg'); throw new Error('private') })
  await f.player.show(config, 'IDLE', true)
  assert.equal(f.views.at(-1).id, 'cover'); assert.equal(f.views.at(-1).error, true)
  const animated = fixture(async () => blob('image/gif')); await animated.player.show(config, 'IDLE', true)
  assert.equal(animated.urls.length, 0)
})
test('新会话或重新开启必须重新读取，旧URL全部释放', async () => {
  const f = fixture(); await f.player.show(config, 'IDLE', true)
  f.player.clear(); await f.player.show(config, 'IDLE', true)
  assert.deepEqual(f.loaded.map(item => item.id), ['cover', 'idle', 'cover', 'idle'])
  assert.equal(f.revoked.length, 2)
  f.player.dispose()
})
