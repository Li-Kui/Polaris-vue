import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import {readFile} from 'node:fs/promises'
import {compileScript, parse} from '@vue/compiler-sfc'
import {transformWithEsbuild} from 'vite'
import * as Vue from 'vue'
import * as avatarUtils from '../src/utils/customerAvatarState.js'

const source = await readFile(new URL('../src/views/platform/customerService/CustomerAvatarEditor.vue', import.meta.url), 'utf8')
const script = compileScript(parse(source).descriptor, { id: 'avatar-editor', inlineTemplate: true }).content
const compiled = await transformWithEsbuild(script, 'avatar-editor.js', { format: 'cjs', target: 'esnext' })
const tick = async () => { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() }
function fixture(config) {
  const updates = [], requests = [], groups = [], avatars = []
  const element = type => ({ type, children: [], parent: null })
  const renderer = Vue.createRenderer({
    createElement: element, createText: value => ({ text: value }), createComment: value => ({ comment: value }),
    insert(node, parent, anchor) { if (node.parent) { const old = node.parent.children; old.splice(old.indexOf(node), 1) } node.parent = parent; const index = parent.children.indexOf(anchor); parent.children.splice(index < 0 ? parent.children.length : index, 0, node) },
    remove(node) { if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1) },
    setText(node, text) { node.text = text }, setElementText(node, text) { node.text = text },
    patchProp(node, key, previous, value) { node[key] = value }, parentNode: node => node.parent,
    nextSibling: node => node.parent?.children[node.parent.children.indexOf(node) + 1]
  })
  const pass = { setup(_, { slots }) { return () => Vue.h('div', slots.default?.()) } }
  const RadioGroup = { props: ['modelValue'], emits: ['update:modelValue'], setup(props, { emit, slots }) {
    groups.push({ select: value => emit('update:modelValue', value), props }); return () => Vue.h('div', slots.default?.())
  } }
  const Avatar = { props: ['config', 'state', 'active'], setup(props) { avatars.push(props); return () => Vue.h('div', props.state) } }
  const api = { listCustomerAvatarAssets: async () => { requests.push('list'); return { data: [] } }, uploadCustomerAvatarAsset: () => assert.fail('预览不能上传素材') }
  const module = { exports: {} }, modules = { vue: Vue, 'element-plus': { ElMessage: {} }, './CustomerAvatar.vue': { default: Avatar, __esModule: true }, '@/utils/customerAvatarState': avatarUtils, '@/api/platform/customerService': api }
  vm.runInNewContext(compiled.code, { module, exports: module.exports, require: name => { assert.ok(name in modules, name); return modules[name] } })
  const app = renderer.createApp(module.exports.default, { modelValue: config, 'onUpdate:modelValue': value => updates.push(value) })
  for (const name of ['ElDivider', 'ElFormItem', 'ElSwitch', 'ElInput', 'ElSelect', 'ElOption', 'ElButton', 'ElRadio']) app.component(name, pass)
  app.component('ElRadioGroup', RadioGroup)
  app.mount(element('root'))
  return { groups, avatars, updates, requests, dispose: () => app.unmount() }
}
test('真实编辑器渲染绑定五种预览状态，切换不会写回配置或发起语音请求', async () => {
  const config = { name: '小美', coverAssetId: 'cover', idleAssetId: 'idle', listeningAssetId: 'listen', thinkingAssetId: 'think', speakingAssetId: 'speak', fit: 'CONTAIN' }
  const original = structuredClone(config), f = fixture(config); await tick()
  assert.equal(f.avatars.length, 1); assert.equal(f.groups.length, 1)
  for (const [state, asset] of [['IDLE', 'idle'], ['LISTENING', 'listen'], ['THINKING', 'think'], ['SPEAKING', 'speak'], ['PAUSED', 'cover']]) {
    f.groups[0].select(state); await tick()
    assert.equal(f.avatars[0].state, state); assert.equal(f.avatars[0].active, true)
    assert.equal(avatarUtils.customerAvatarAsset(f.avatars[0].config, f.avatars[0].state), asset)
  }
  assert.equal(f.updates.length, 0); assert.deepEqual(config, original); assert.deepEqual(f.requests, ['list'])
  f.dispose()
})
test('仅封面的旧配置也可逐项预览，未启用人物时不挂载播放器', async () => {
  const f = fixture({ name: '小美', coverAssetId: 'cover' }); await tick()
  for (const state of ['LISTENING', 'THINKING', 'SPEAKING', 'PAUSED']) {
    f.groups[0].select(state); await tick()
    assert.equal(avatarUtils.customerAvatarAsset(f.avatars[0].config, f.avatars[0].state), 'cover')
  }
  f.dispose()
  const disabled = fixture(null); await tick()
  assert.equal(disabled.avatars.length, 0); assert.equal(disabled.groups.length, 0); disabled.dispose()
})
