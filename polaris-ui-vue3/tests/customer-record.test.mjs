import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import {readFile} from 'node:fs/promises'
import {compileScript, parse} from '@vue/compiler-sfc'
import {transformWithEsbuild} from 'vite'
import * as Vue from 'vue'
import * as recordState from '../src/utils/customerRecordState.js'
import * as serviceState from '../src/utils/customerServiceState.js'

const source = await readFile(new URL('../src/views/platform/customerService/CustomerRecordPanel.vue', import.meta.url), 'utf8')
const compiled = await transformWithEsbuild(compileScript(parse(source).descriptor, { id: 'customer-record' }).content, 'record.js', { format: 'cjs', target: 'esnext' })
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const tick = () => new Promise(resolve => setImmediate(resolve))
function fixture() {
  let unmount
  const reads = [], lists = [], modules = {
    vue: { ...Vue, onMounted() {}, onBeforeUnmount: fn => { unmount = fn } },
    '@/utils/customerRecordState': recordState, '@/utils/customerServiceState': serviceState,
    '@/api/platform/customerService': {
      listCustomerRecords: (app, before, signal) => { const value = deferred(); lists.push({ app, before, signal, ...value }); return value.promise },
      getCustomerRecord: (app, session, signal) => { const value = deferred(); reads.push({ app, session, signal, ...value }); return value.promise }
    }
  }
  const module = { exports: {} }
  vm.runInNewContext(compiled.code, { module, exports: module.exports, AbortController, require: name => { assert.ok(name in modules,name); return modules[name] } })
  const scope = Vue.effectScope(), state = scope.run(() => module.exports.default.setup({ app: { id: 6, config: { name: '测试客服' } } }, { emit() {}, expose() {} }))
  return { state, reads, lists, dispose() { unmount(); scope.stop() } }
}
test('不同会话快速切换时取消旧读取，迟到详情不能覆盖当前选择', async () => {
  const f = fixture(), first = f.state.select({ id: 'first' }), second = f.state.select({ id: 'second' })
  assert.equal(f.reads[0].signal.aborted,true); assert.equal(f.reads[1].app,6)
  f.reads[1].resolve({ data: { session: { id: 'second' }, contentAvailable: false, turns: [] } }); await second
  f.reads[0].resolve({ data: { session: { id: 'first' }, answer: '旧正文' } }); await first
  assert.equal(f.state.selected.value.id,'second'); assert.equal(f.state.detail.value.session.id,'second')
  f.dispose()
})
test('翻页与刷新清空旧正文，读取失败不保留上一条详情，关闭后迟到结果不可恢复', async () => {
  const f = fixture(), first = f.state.select({ id: 'first' })
  const listing = f.state.load('cursor',['previous'])
  assert.equal(f.reads[0].signal.aborted,true); assert.equal(f.state.selected.value,null)
  f.lists[0].resolve({ data: { items: [{ id: 'row' }], nextCursor: 'next' } }); await listing
  f.reads[0].resolve({ data: { answer: '不能恢复' } }); await first
  assert.equal(f.state.detail.value,null); assert.equal(f.state.cursor.value,'cursor'); assert.deepEqual([...f.state.previous.value],['previous'])
  const failed = f.state.select({ id: 'row' }); f.reads[1].reject(new Error('private endpoint')); await failed
  assert.equal(f.state.detail.value,null); assert.ok(f.state.detailError.value); assert.ok(!f.state.detailError.value.includes('private'))
  const pending = f.state.select({ id: 'last' }); f.dispose()
  assert.equal(f.reads[2].signal.aborted,true); f.reads[2].resolve({ data: { answer: '不能恢复' } }); await pending
  assert.equal(f.state.detail.value,null)
})
test('并发列表只采用最新结果，失败不会把旧页当作当前记录', async () => {
  const f = fixture(), first = f.state.load(), latest = f.state.load('older')
  assert.equal(f.lists[0].signal.aborted,true)
  f.lists[1].resolve({ data: { items: [{ id: 'latest' }], nextCursor: null } }); await latest
  f.lists[0].resolve({ data: { items: [{ id: 'old' }], nextCursor: 'old' } }); await first
  assert.equal(f.state.rows.value[0].id,'latest')
  const failed = f.state.load(); f.lists[2].reject(new Error('403')); await failed
  assert.equal(f.state.rows.value.length,0); assert.equal(f.state.nextCursor.value,null); assert.ok(f.state.listError.value)
  f.dispose(); await tick()
})
test('耗时未知不冒充零，实时识别明确含录音，账本超时不声称已结束', () => {
  for (const value of [null, undefined, '1000', -1, NaN, Infinity]) assert.equal(recordState.customerRecordDuration(value),'未记录')
  assert.equal(recordState.customerRecordDuration(0),'0 ms'); assert.equal(recordState.customerRecordDuration(2395),'2.40 秒')
  assert.match(recordState.customerSpeechStage('STREAM'),/含录音/); assert.match(recordState.customerSpeechStage('STT'),/不含录音/)
  assert.match(recordState.customerSpeechStage('TTS'),/不含播放/)
  assert.equal(recordState.customerSpeechStage('TTS', 2),'第 2 段合成（不含播放）')
  assert.equal(recordState.customerSpeechStage('TTS', 9),recordState.customerSpeechStage('TTS'))
  assert.equal(recordState.customerRecordStatus({ status: 'RUNNING', overdue: true }),'超时待核定')
})

test('收起详情会取消读取并使迟到结果失效，不恢复旧正文', async () => {
  const f = fixture(), reading = f.state.select({ id: 'closing' })
  f.state.closeDetail(); assert.equal(f.reads[0].signal.aborted, true)
  f.reads[0].resolve({ data: { answer: '迟到详情' } }); await reading
  assert.equal(f.state.selected.value, null); assert.equal(f.state.detail.value, null); assert.equal(f.state.detailLoading.value, false)
  f.dispose()
})
