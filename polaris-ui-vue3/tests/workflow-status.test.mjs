import assert from 'node:assert/strict'
import test from 'node:test'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
import {compileTemplate, parse} from '@vue/compiler-sfc'
import {workflowStatusLabel} from '../src/components/workflow/workflowStatus.js'

const readComponent = name => parse(readFileSync(new URL(`../src/components/workflow/${name}.vue`, import.meta.url), 'utf8')).descriptor
const monitor = readComponent('WorkflowExecutionMonitor')
const workbench = readComponent('WorkflowWorkbench')
const childTest = readComponent('WorkflowSubWorkflowTest')

test('后端执行及节点状态均有中文映射，且不修改状态码', () => {
  for (const type of ['WorkflowExecutionStatus', 'WorkflowNodeRunStatus']) {
    const source = readFileSync(new URL(`../../polaris-ai-core/src/main/java/com/polaris/ai/workflow/contract/${type}.java`, import.meta.url), 'utf8')
    const codes = [...source.matchAll(/^    ([A-Z_]+)[,;]/gm)].map(match => match[1])
    assert.ok(codes.length >= 10)
    for (const code of codes) {
      const label = workflowStatusLabel(code)
      assert.match(label, /[\u4e00-\u9fff]/, code)
      assert.notEqual(label, code)
      assert.ok(source.includes(code))
    }
  }
  assert.equal(workflowStatusLabel('SUCCEEDED'), '执行成功')
  assert.equal(workflowStatusLabel('FAILED'), '执行失败')
  assert.equal(workflowStatusLabel('NEEDS_ATTENTION'), '待人工确认')
  assert.equal(workflowStatusLabel('WAITING_APPROVAL'), '等待审批')
  assert.equal(workflowStatusLabel('RETRY_WAIT'), '等待重试')
  assert.equal(workflowStatusLabel('SKIPPED'), '已跳过')
})

test('未知状态保留原码以便排查，空状态使用明确占位', () => {
  assert.equal(workflowStatusLabel('FUTURE_STATE'), 'FUTURE_STATE')
  assert.equal(workflowStatusLabel('constructor'), 'constructor')
  assert.equal(workflowStatusLabel('__proto__'), '__proto__')
  for (const code of [null, undefined, '']) assert.equal(workflowStatusLabel(code), '未知状态')
})

test('桌面列表、移动卡片、详情与节点徽标均使用中文展示和原码提示', () => {
  const badges = monitor.template.content.match(/<span\b[^>]*execution-status[^>]*>[\s\S]*?<\/span>/g)
  assert.equal(badges.length, 5)
  for (const badge of badges) {
    const status = badge.match(/:title="((?:row|selected)\.status)"/)
    assert.ok(status, '悬停仍可查看原始状态码')
    assert.ok(badge.includes(`{{ statusLabel(${status[1]}) }}`))
  }
  assert.ok(monitor.template.content.includes('label="尝试次数"'))
  assert.doesNotMatch(monitor.template.content, /\{\{\s*(?:row|selected)\.status\s*\}\}/)
})

test('真实组件方法共享映射，原有终态和重试判断仍基于英文状态码', () => {
  const statusSource = monitor.script.content.match(/    statusLabel\(status\) \{[\s\S]*?\n    \},/)[0].replace(/,$/, '')
  const label = vm.runInNewContext(`({${statusSource}}).statusLabel`, {workflowStatusLabel})
  const terminalSource = monitor.script.content.match(/    terminal\(status\) \{[\s\S]*?\n    \},/)[0].replace(/,$/, '')
  const terminal = vm.runInNewContext(`({${terminalSource}}).terminal`)
  const retrySource = monitor.script.content.match(/    retryable\(status\) \{[\s\S]*?\n    \},/)[0].replace(/,$/, '')
  const retryable = vm.runInNewContext(`({${retrySource}}).retryable`)
  assert.equal(label('NEEDS_ATTENTION'), '待人工确认')
  assert.equal(terminal('FAILED'), true)
  assert.equal(terminal('NEEDS_ATTENTION'), false)
  assert.equal(retryable('FAILED'), true)
  assert.equal(retryable('NEEDS_ATTENTION'), false)
  assert.equal(retryable('执行失败'), false)
})

test('工作台和子工作流试运行共享状态展示且模板编译通过', () => {
  const source = workbench.script.content.match(/    executionStatusLabel\(\) \{[\s\S]*?\n    \}/)[0]
  const label = vm.runInNewContext(`({${source}}).executionStatusLabel`, {workflowStatusLabel})
  assert.equal(label.call({debugExecution: {status: 'FAILED'}}), '执行失败')
  assert.equal(label.call({debugExecution: null}), '准备运行')
  const childSource = childTest.scriptSetup.content.match(/^const statusLabel=computed\([^\n]+/m)[0]
  const childLabel = vm.runInNewContext(`${childSource};statusLabel`, {
    computed: fn => fn, execution: {value: {status: 'NEEDS_ATTENTION'}}, workflowStatusLabel
  })
  assert.equal(childLabel(), '待人工确认')
  assert.ok(workbench.template.content.includes(':title="debugExecution.status"'))
  assert.ok(childTest.template.content.includes(':title="execution.status"'))
  for (const [name, descriptor] of [['monitor', monitor], ['workbench', workbench], ['child', childTest]]) {
    assert.deepEqual(compileTemplate({source: descriptor.template.content, filename: `${name}.vue`, id: name}).errors, [])
  }
})
