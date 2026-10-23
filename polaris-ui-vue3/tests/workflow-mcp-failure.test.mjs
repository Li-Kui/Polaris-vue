import assert from 'node:assert/strict'
import test from 'node:test'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
import {parse} from '@vue/compiler-sfc'

const {descriptor} = parse(readFileSync(new URL('../src/components/workflow/WorkflowExecutionMonitor.vue', import.meta.url), 'utf8'))
const source = descriptor.script.content.match(/    nodeWriteStatusLabel\(row\) \{[\s\S]*?\n    \},\n    artifactSourceLabel/)[0]
  .replace(/,\n    artifactSourceLabel$/, '')
const label = vm.runInNewContext(`({${source}}).nodeWriteStatusLabel`)
const context = {terminal: status => ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status)}

test('MCP 未派发、确认完成及结果未知在执行详情中明确区分', () => {
  for (const [status, expected] of [['NOT_DISPATCHED', '未派发'], ['CONFIRMED', '调用已确认'],
    ['UNCONFIRMED', '结果未确认'], ['NONE', '未执行外部写入']]) {
    assert.equal(label.call(context, {sideEffect: 'WRITE', sideEffectStatus: status, status: 'FAILED'}), expected)
  }
})

test('不改变已有 API 写入和内部产物状态的含义', () => {
  assert.equal(label.call(context, {sideEffect: 'WRITE', sideEffectStatus: 'COMMITTED'}), '外部写入已确认')
  assert.equal(label.call(context, {sideEffect: 'WRITE', sideEffectStatus: 'PENDING', status: 'RUNNING'}), '外部写入待确认')
  assert.equal(label.call(context, {sideEffect: 'DURABLE_INTERNAL', sideEffectStatus: 'COMMITTED'}), '产物已保存')
  assert.equal(label.call(context, {sideEffect: 'READ'}), '无写入')
})
