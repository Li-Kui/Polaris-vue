import assert from 'node:assert/strict'
import {existsSync, readFileSync} from 'node:fs'
import test from 'node:test'

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')

test('撤除独立内部应用入口，不删除原工作流分享页面', () => {
  const router = read('../src/router/index.js')
  const layout = read('../src/layout/PlatformLayout.vue')
  assert.doesNotMatch(router, /workflow-apps|views\/platform\/workflowApp/)
  assert.doesNotMatch(layout, /workflow-apps|内部应用试运行/)
  assert.match(router, /app\/:shareCode/)
  assert.ok(existsSync(new URL('../src/views/workflowApp/RuntimePage.vue', import.meta.url)))
  assert.ok(existsSync(new URL('../src/api/workflowApp/runtime.js', import.meta.url)))
  assert.ok(!existsSync(new URL('../src/views/platform/workflowApp/index.vue', import.meta.url)))
  assert.ok(!existsSync(new URL('../src/api/platform/workflowApp.js', import.meta.url)))
})

test('工作流定义不再映射已撤除的应用所属字段', () => {
  const model = read('../../polaris-ai-core/src/main/java/com/polaris/ai/workflow/domain/WorkflowDefinition.java')
  assert.doesNotMatch(model, /deliveryType|deliveryOwnerId|delivery_type|delivery_owner_id/)
  assert.match(model, /defaultPageType/)
  assert.match(model, /sharePageConfigJson/)
})
