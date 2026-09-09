import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'

const component = name => readFileSync(
  new URL(`../src/components/workflow/${name}`, import.meta.url),
  'utf8'
)

test('知识库选择卡片暴露多选状态和清晰的搜索提示', () => {
  const picker = component('WorkflowKnowledgeSourcePicker.vue')
  assert.match(picker, /placeholder="搜索知识库名称或说明"/)
  assert.match(picker, /:aria-pressed="isSelected\(resource\)"/)
  assert.match(picker, /只看可用/)
})

test('试查结果说明向量分数的适用边界', () => {
  const result = component('WorkflowKnowledgeTestResult.vue')
  assert.match(result, /向量匹配分表示文字语义接近程度/)
  assert.match(result, /不代表资料中的对象和结论一定适用/)
})

test('知识库节点提供三步引导和自然语言试查输入', () => {
  const workbench = component('WorkflowWorkbench.vue')
  assert.match(workbench, /aria-label="知识库检索节点配置步骤"/)
  assert.match(workbench, /<strong>选择知识库<\/strong>/)
  assert.match(workbench, /<strong>设置检索<\/strong>/)
  assert.match(workbench, /<strong>输入与试查<\/strong>/)
  assert.match(workbench, /placeholder="例如：退款申请需要满足哪些条件？"/)
  assert.match(workbench, /class="node-palette-toggle"/)
  assert.match(workbench, /aria-label="关闭节点库"/)
})
