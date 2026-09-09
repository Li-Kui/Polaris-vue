import test from 'node:test'
import assert from 'node:assert/strict'
import {resolveViewModule} from '../src/utils/routeView.js'

const workflowLoader = () => import('../src/views/ai/workflow/index.vue')
const chatLoader = () => import('../src/views/ai/chat.vue')
const modules = {
  './../../views/ai/workflow/index.vue': workflowLoader,
  './../../views/ai/chat.vue': chatLoader
}

test('目录页面兼容省略 index 的菜单组件配置', () => {
  assert.equal(resolveViewModule('ai/workflow', modules), workflowLoader)
  assert.equal(resolveViewModule('ai/workflow/index', modules), workflowLoader)
})

test('单文件页面继续精确匹配并规范化路径', () => {
  assert.equal(resolveViewModule('ai/chat', modules), chatLoader)
  assert.equal(resolveViewModule('/views/ai/chat.vue', modules), chatLoader)
})

test('不存在的菜单组件不会返回空的路由组件', () => {
  assert.equal(resolveViewModule('ai/missing', modules), undefined)
  assert.equal(resolveViewModule('', modules), undefined)
})
