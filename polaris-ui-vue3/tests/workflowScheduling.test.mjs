import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'

const component = name => readFile(
  new URL(`../src/components/workflow/${name}`, import.meta.url), 'utf8')

const [monitor, workbench, subWorkflowTest] = await Promise.all([
  component('WorkflowExecutionMonitor.vue'),
  component('WorkflowWorkbench.vue'),
  component('WorkflowSubWorkflowTest.vue')
])

test('执行页面由 SSE 驱动且不保留固定间隔轮询', () => {
  for (const source of [monitor, workbench, subWorkflowTest]) {
    assert.match(source, /streamWorkflowExecutionEvents/)
    assert.doesNotMatch(source, /setInterval\s*\(/)
  }
  assert.match(monitor, /onOpen:[\s\S]*refreshSelectedExecution\(executionId\)/)
  assert.match(workbench, /onOpen:[\s\S]*this\.pollExecution\(\)/)
  assert.match(subWorkflowTest, /onOpen:\(\)=>\{reconnectAttempt=0;refresh\(\)\}/)
})

test('断线重连使用带抖动的指数退避', () => {
  for (const source of [monitor, workbench, subWorkflowTest]) {
    assert.match(source, /1000,\s*2000,\s*5000,\s*10000,\s*30000/)
    assert.match(source, /Math\.random\(\)/)
  }
})

test('节点试运行只在可见对话框中自适应查询', () => {
  assert.match(workbench, /this\.nodeTestDialogOpen && !document\.hidden/)
  assert.match(workbench, /Math\.min\(5000, Math\.max\(800, delay \* 2\)\)/)
  assert.doesNotMatch(workbench, /setInterval\s*\(/)
})
