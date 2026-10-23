import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import * as sass from 'sass'

const source = readFileSync(new URL('../src/components/knowledge/KnowledgeDocumentSnapshot.vue', import.meta.url), 'utf8')
const {descriptor} = parse(source), compiled = compileScript(descriptor, {id: 'document-snapshot'})
const script = descriptor.script.content.replace(/^import .*$/m, '').replace('export default {', 'const component = {')
function state(api, extra = {}) {
  const component = vm.runInNewContext(`${script}\ncomponent`, {getDocumentSnapshot: api})
  const context = {...component.data(), modelValue: true, document: {id: 11, name: '合成'}, knowledgeId: 4, ...extra}
  for (const [key, method] of Object.entries(component.methods)) context[key] = method.bind(context)
  return {component, context}
}
function deferred() {let resolve, reject; const promise = new Promise((a, b) => {resolve = a; reject = b}); return {promise, resolve, reject}}

test('诊断模板可编译、正文仅转义显示，快照缺失与不可用分开', () => {
  assert.deepEqual(compileTemplate({source: descriptor.template.content, filename: 'KnowledgeDocumentSnapshot.vue',
    id: 'document-snapshot', compilerOptions: {bindingMetadata: compiled.bindings}}).errors, [])
  for (const style of descriptor.styles) assert.ok(sass.compileString(style.content).css.length > 0)
  assert.doesNotMatch(source, /v-html|rebuildDocument|testKnowledgeRetrieval/)
  assert.match(source, /LEGACY_NO_SNAPSHOT/); assert.match(source, /UNAVAILABLE/)
  assert.match(source, /位置无法精确确认时不猜页码/)
  assert.match(source, /is-disabled\.is-disabled/); assert.match(source, /color: inherit !important/)
})

test('Teleport 诊断弹窗独立适配深浅色背景、标题和高优先级悬停按钮', () => {
  assert.match(descriptor.template.content, /class="knowledge-snapshot-dialog"/)
  const theme = descriptor.styles.find(style => !style.scoped)
  assert.ok(theme)
  const css = sass.compileString(theme.content).css
  assert.match(css, /html \.el-dialog\.knowledge-snapshot-dialog \{/)
  assert.match(css, /background: var\(--el-bg-color\) !important/)
  assert.match(css, /html\.dark \.el-dialog\.knowledge-snapshot-dialog/)
  assert.match(css, /--snapshot-accent: #93c5fd/)
  assert.match(css, /\.el-dialog__title[\s\S]*color: var\(--el-text-color-primary\) !important/)
  assert.match(css, /\.el-button\.el-button\.el-button\.el-button:not\(\.is-disabled\):is\(:hover, :focus-visible, :active\)/)
  assert.match(css, /background: var\(--snapshot-accent-soft\) !important/)
  assert.match(css, /\.el-alert--warning[\s\S]*color: var\(--snapshot-warning\) !important/)
  assert.match(css, /--snapshot-warning: #92400e/); assert.match(css, /--snapshot-warning: #fcd34d/)
  assert.match(css, /\.el-alert__title[\s\S]*color: inherit !important/)
  assert.match(css, /outline: 2px solid var\(--snapshot-accent\)/)
})

test('只请求当前文档快照与分页，不传正文、租户或 collection', async () => {
  let request
  const {context} = state(async (id, params) => {request = {id, params}; return {data: {state: 'READY', chunks: []}}})
  await context.load(); assert.equal(request.id, 11); assert.equal(JSON.stringify(request.params), JSON.stringify({page: 1, pageSize: 10}))
  assert.equal(context.result.state, 'READY'); assert.equal(context.loading, false)
  const api = readFileSync(new URL('../src/api/ai/knowledge.js', import.meta.url), 'utf8')
  assert.match(api, /document\/\$\{id\}\/snapshot.*method: 'get'/)
})

test('换文档后慢旧响应不得覆盖新文档正文', async () => {
  const pending = deferred()
  const {component, context} = state(() => pending.promise)
  const running = context.load(); context.document = {id: 12}; context.modelValue = false
  component.watch['document.id'].call(context)
  pending.resolve({data: {state: 'READY', chunks: [{text: '过时正文'}]}}); await running
  assert.equal(context.result, null); assert.equal(context.loading, false)
})

test('关闭对话框后清除正文与错误，迟到的失败不重新显示', async () => {
  const pending = deferred(); const {context} = state(() => pending.promise)
  const running = context.load(); context.modelValue = false; context.clear()
  pending.reject(new Error('旧故障')); await running
  assert.equal(context.result, null); assert.equal(context.error, ''); assert.equal(context.page, 1)
})

test('失败明确显示，不能伪装成空快照或自动调用模型', async () => {
  let calls = 0
  const {context} = state(async () => {calls++; throw new Error('快照不可用')})
  await context.load(); assert.equal(calls, 1); assert.equal(context.error, '快照不可用'); assert.equal(context.result, null)
})

test('无效文档或关闭状态不派发；入口限管理员且服务端仍独立鉴权', async () => {
  let calls = 0
  for (const extra of [{modelValue: false}, {document: null}]) {const {context} = state(async () => {calls++}, extra); await context.load()}
  assert.equal(calls, 0)
  const page = readFileSync(new URL('../src/views/ai/knowledge.vue', import.meta.url), 'utf8')
  assert.match(page, /v-if="canTestKnowledge" link class="op-btn-edit" @click="openDocumentSnapshot/)
  assert.match(page, /if \(!this\.canTestKnowledge\) return/)
})

function rebuildMethods(api) {
  const page = readFileSync(new URL('../src/views/ai/knowledge.vue', import.meta.url), 'utf8')
  const {descriptor} = parse(page), compiled = compileScript(descriptor, {id: 'knowledge-page'})
  const object = compiled.scriptAst.find(node => node.type === 'ExportDefaultDeclaration').declaration
  const methods = object.properties.find(node => node.key.name === 'methods').value.properties
  const source = methods.filter(node => ['handleRebuildDoc', 'handleRebuildKb'].includes(node.key.name))
    .map(node => descriptor.script.content.slice(node.start, node.end)).join(',')
  return vm.runInNewContext(`({${source}})`, {rebuildDocument: api, rebuildKnowledge: api})
}

test('上传提示和边界与 B1 的 10MiB 解析上限一致，超限不上传', async () => {
  const page = readFileSync(new URL('../src/views/ai/knowledge.vue', import.meta.url), 'utf8')
  assert.match(page, /单文件解析上限 10MB/); assert.doesNotMatch(page, /20MB/)
  const {descriptor} = parse(page), compiled = compileScript(descriptor, {id: 'knowledge-page'})
  const object = compiled.scriptAst.find(node => node.type === 'ExportDefaultDeclaration').declaration
  const node = object.properties.find(node => node.key.name === 'methods').value.properties.find(node => node.key.name === 'beforeUpload')
  let errors = 0
  const methods = vm.runInNewContext(`({${descriptor.script.content.slice(node.start, node.end)}})`, {newKnowledgeRebuildRequestKey: () => 'synthetic-upload-key'})
  const context = {canTestKnowledge: true, currentKbId: 7, currentKb: {indexStatus: 'READY'}, $modal: {msgError() {errors++}}}
  assert.equal(await methods.beforeUpload.call(context, {size: 10 * 1024 * 1024 + 1}), false)
  assert.equal(context.uploadPending, undefined); assert.equal(errors, 1)
  assert.equal(await methods.beforeUpload.call(context, {size: 10 * 1024 * 1024}), true)
  assert.equal(context.uploadPending.knowledgeId, 7); assert.equal(context.uploadPending.key, 'synthetic-upload-key')
})

test('文档行重建明确提示全库范围与费用，确认前不派发', async () => {
  const pending = deferred(); let calls = 0, notice
  const methods = rebuildMethods(async () => {calls++})
  const context = {canTestKnowledge: true, currentKbId: 4, currentKb: {name: '合成库'}, rebuildKey: () => 'synthetic-key', acceptRebuildTask() {}, $modal: {confirm: text => {notice = text; return pending.promise}, msgSuccess() {}}, startStatusPolling() {}}
  methods.handleRebuildDoc.call(context, {id: 11, knowledgeBaseId: 4})
  assert.equal(calls, 0); assert.match(notice, /全部文档/); assert.match(notice, /费用/)
  pending.resolve(); await new Promise(resolve => setImmediate(resolve)); assert.equal(calls, 1)
})

test('等待重建确认期间切库，不误重建新库或继续旧库请求', async () => {
  for (const name of ['handleRebuildDoc', 'handleRebuildKb']) {
    const pending = deferred(); let calls = 0
    const methods = rebuildMethods(async () => {calls++})
    const context = {canTestKnowledge: true, currentKbId: 4, currentKb: {name: '合成库'}, $modal: {confirm: () => pending.promise, msgSuccess() {}}, startStatusPolling() {}}
    methods[name].call(context, {id: 11, knowledgeBaseId: 4}); context.currentKbId = 5
    pending.resolve(); await new Promise(resolve => setImmediate(resolve)); assert.equal(calls, 0)
  }
})
