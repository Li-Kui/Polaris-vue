import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {
    normalizeWaitConfig,
    WAIT_DEFAULT_CONFIG,
    waitConfigurationState,
    waitMaximumLabel,
    waitSummary
} from '../src/components/workflow/workflowWait.js'

const executionMonitor = await readFile(
  new URL('../src/components/workflow/WorkflowExecutionMonitor.vue', import.meta.url), 'utf8')

test('等待节点默认五分钟且包含明确安全上限', () => {
  assert.deepEqual(normalizeWaitConfig(), WAIT_DEFAULT_CONFIG)
  assert.equal(waitSummary(WAIT_DEFAULT_CONFIG), '等待 5 分钟')
  assert.equal(waitMaximumLabel(WAIT_DEFAULT_CONFIG.safety.maxWaitSeconds), '30 天')
})

test('动态等待必须选择对应类型的上游字段', () => {
  const duration = normalizeWaitConfig({schedule: {kind: 'AFTER', source: {kind: 'INPUT', unit: 'HOUR'}}})
  assert.equal(waitConfigurationState({config: duration}).code, 'INCOMPLETE')
  assert.equal(waitConfigurationState({config: duration, inputMapping: {duration: {expression: '$.input.hours'}}}).code, 'READY')

  const target = normalizeWaitConfig({schedule: {kind: 'AT', source: {kind: 'INPUT', timezone: 'UTC'}}})
  assert.equal(waitConfigurationState({config: target, inputMapping: {duration: {expression: '$.input.hours'}}}).code, 'INCOMPLETE')
  assert.equal(waitConfigurationState({config: target, inputMapping: {targetAt: {expression: '$.input.runAt'}}}).code, 'READY')
})

test('指定时间摘要明确展示业务时区', () => {
  const config = normalizeWaitConfig({schedule: {kind: 'AT', source: {
    kind: 'FIXED', localDateTime: '2030-01-02T09:30:00', timezone: 'Asia/Shanghai'
  }}})
  assert.equal(waitConfigurationState({config}).code, 'READY')
  assert.equal(waitSummary(config), '等到 2030-01-02 09:30:00（Asia/Shanghai）')
})

test('已结束的等待节点不会继续显示为处理中', () => {
  assert.match(executionMonitor, /sideEffectStatus === 'PENDING' && !this\.terminal\(row\.status\)/)
  assert.match(executionMonitor, /'无需外部写入'/)
})

test('窄窗口使用执行卡片避免状态、时间与操作互相遮挡', () => {
  assert.match(executionMonitor, /class="execution-cards"/)
  assert.match(executionMonitor, /:aria-label="`执行 \$\{row\.executionId\}`"/)
  assert.match(executionMonitor, /@media \(max-width: 1100px\)/)
  assert.match(executionMonitor, /\.execution-table \{\s*display: none;/)
})
