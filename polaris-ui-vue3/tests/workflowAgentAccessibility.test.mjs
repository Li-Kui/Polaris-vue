import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'

const component = name => readFileSync(
  new URL(`../src/components/workflow/${name}`, import.meta.url),
  'utf8'
)

test('智能体选择和配置控件具备可读名称与选中语义', () => {
  const picker = component('WorkflowAgentPicker.vue')
  const editor = component('WorkflowAgentEditor.vue')

  assert.match(picker, /aria-label="搜索智能体名称或用途"/)
  assert.match(picker, /aria-label="筛选智能体"/)
  assert.match(picker, /:aria-pressed="resource\.resourceId === selectedResourceId"/)
  assert.match(editor, /aria-label="本次任务要求"/)
  assert.match(editor, /aria-label="使用内部只读工具"/)
  assert.match(editor, /aria-label="最长等待时间"/)
})

test('智能体步骤和试运行弹窗暴露当前位置并适配小窗口', () => {
  const workbench = component('WorkflowWorkbench.vue')

  assert.match(workbench, /aria-label="AI 智能体节点配置步骤"/)
  assert.equal((workbench.match(/\? 'step' : undefined/g) || []).length >= 3, true)
  assert.match(workbench, /class="workflow-dialog node-test-dialog"/)
  assert.match(workbench, /max-height: calc\(100vh - 24px\)/)
  assert.match(workbench, /\.node-test-dialog \.el-dialog__body/)
  assert.match(workbench, /overflow-y: auto/)
})

test('输入映射搜索框具有明确用途名称', () => {
  assert.match(
    component('WorkflowInputMappingEditor.vue'),
    /aria-label="搜索上游节点或字段"/
  )
})
