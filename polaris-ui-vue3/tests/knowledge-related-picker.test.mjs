import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'

const source = readFileSync(new URL('../src/components/knowledge/KnowledgeRelatedDocumentPicker.vue', import.meta.url), 'utf8')
const {descriptor} = parse(source)
const compiled = compileScript(descriptor, {id: 'related-picker'})
const script = descriptor.script.content.replace(/^import .*$/gm, '').replace('export default {', 'const component = {')
const capabilities = {version: 1, strategy: 'EXPLICIT_RELATED_V1', indexReady: true, defaultResultLimit: 5}
function setup(caps = async () => ({data: capabilities}), docs = async () => ({rows: []})) {
  const definition = vm.runInNewContext(`${script}\ncomponent`, {getKnowledgeRetrievalCapabilities: caps, listDocuments: docs})
  const events = []
  const context = {...definition.data(), knowledgeId: 10, modelValue: [], disabled: false, $emit: (...event) => events.push(event)}
  for (const [name, method] of Object.entries(definition.methods)) context[name] = method.bind(context)
  for (const [name, getter] of Object.entries(definition.computed)) Object.defineProperty(context, name, {get: () => getter.call(context)})
  return {context, definition, events}
}

test('关联选择器可编译，错误状态仍提供显式清空，不自动改用其他策略', () => {
  assert.deepEqual(compileTemplate({source: descriptor.template.content, filename: 'RelatedPicker.vue', id: 'related-picker', compilerOptions: {bindingMetadata: compiled.bindings}}).errors, [])
  assert.match(source, /@click="\$emit\('update:modelValue', \[\]\)">清空选择/)
  assert.doesNotMatch(source, /testKnowledgeRetrieval|chatStream|v-html/)
})

test('能力与文档都是只读查询，只展示当前安全入库文档并限制数量', async () => {
  let request
  const {context} = setup(undefined, async params => {request = params; return {rows: [
    {id: 1, name: '合成资料', status: '2', moderationStatus: 'SAFE'},
    {id: 2, name: '隔离资料', status: '2', moderationStatus: 'QUARANTINED'},
    {id: 3, name: '构建中', status: '1', moderationStatus: 'SAFE'}]}})
  await context.load()
  assert.equal(context.ready, true); assert.equal(context.maxAllowed, 3)
  assert.equal(context.options.length, 1); assert.equal(context.options[0].id, 1)
  assert.equal(request.knowledgeBaseId, 10); assert.equal(request.pageSize, 100)
  context.resultLimit = 2; assert.equal(context.maxAllowed, 1)
  context.modelValue = ['1', '2']; assert.equal(context.ready, false)
})

test('旧服务、不可信能力及列表错误都禁止带选择发送，不会清除选择掩盖失败', async () => {
  for (const invalid of [{}, {...capabilities, version: 0}, {...capabilities, defaultResultLimit: null}, {...capabilities, defaultResultLimit: 51}]) {
    let calls = 0
    const {context} = setup(async () => ({data: invalid}), async () => {calls++; return {rows: []}})
    context.modelValue = ['1']; await context.load()
    assert.equal(context.ready, false); assert.equal(calls, 0); assert.equal(context.modelValue.length, 1)
  }
  const {context} = setup(undefined, async () => {throw new Error('private provider message')})
  context.modelValue = ['1']; await context.load()
  assert.equal(context.ready, false); assert.match(context.error, /显式清空/)
  assert.doesNotMatch(context.error, /private/)
})

test('切库及卸载后的迟到只读响应不污染新状态', async () => {
  let complete
  const {context, definition, events} = setup(() => new Promise(resolve => {complete = resolve}))
  const pending = context.load()
  context.knowledgeId = null
  definition.watch.knowledgeId.handler.call(context)
  complete({data: capabilities}); await pending
  assert.equal(context.available, false); assert.equal(context.options.length, 0)
  assert.equal(events.at(-1)[0], 'update:modelValue')
  definition.beforeUnmount.call(context); assert.deepEqual(events.at(-1), ['ready', false])
})
