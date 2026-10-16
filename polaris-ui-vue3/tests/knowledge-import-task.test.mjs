import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'

const page = readFileSync(new URL('../src/views/ai/knowledge.vue', import.meta.url), 'utf8')
const {descriptor} = parse(page), compiled = compileScript(descriptor, {id: 'import-page'})
const object = compiled.scriptAst.find(n => n.type === 'ExportDefaultDeclaration').declaration
const methods = object.properties.find(n => n.key.name === 'methods').value.properties
function state(upload = async () => ({data: {documentId: 33, task: {knowledgeBaseId: 7, taskId: 'task', taskType: 'IMPORT', state: 'QUEUED'}}})) {
  const sent = [], accepted = [], notices = [], forms = []
  class FormData {constructor() {this.entries = []; forms.push(this)} append(...args) {this.entries.push(args)}}
  const sandbox = {FormData, newKnowledgeRebuildRequestKey: () => 'synthetic-upload-key', uploadKnowledgeDocument: async (...args) => {sent.push(args); return upload(...args)}}
  const context = {canTestKnowledge: true, currentKbId: 7, currentKb: {indexStatus: 'READY'}, uploadBusy: false, uploadPending: null,
    uploadRetry: false, rebuildTaskActive: false, rebuildTaskState: null, docList: [], $refs: {rebuildTaskPanel: {accept: t => accepted.push(t), reload() {}}},
    refreshAfterRebuild() {}, $modal: {msgError: t => notices.push(t), msgWarning: t => notices.push(t), msgSuccess: t => notices.push(t), confirm: async () => {}}}
  for (const name of ['beforeUpload', 'uploadDocumentRequest', 'retryUpload', 'sendUpload', 'forgetUpload', 'shouldPollStatus']) {
    const node = methods.find(n => n.key.name === name)
    context[name] = vm.runInNewContext(`({${descriptor.script.content.slice(node.start, node.end)}}).${name}`, sandbox).bind(context)
  }
  return {context, sent, accepted, notices, forms}
}
const file = {size: 100, name: 'synthetic.txt'}
function deferred() {let resolve; const promise = new Promise(r => {resolve = r}); return {resolve, promise}}

test('知识库页面模板可编译，单文件上传与费用提示明确，走统一认证请求', () => {
  assert.deepEqual(compileTemplate({source: descriptor.template.content, filename: 'knowledge.vue', id: 'import-page', compilerOptions: {bindingMetadata: compiled.bindings}}).errors, [])
  assert.match(page, /:http-request="uploadDocumentRequest"/);assert.doesNotMatch(page, /\bmultiple\b|uploadHeaders|uploadUrl/)
  assert.match(page, /每次上传一个文件/);assert.match(page, /索引不可用时会构建全库/);assert.match(page, /向量模型费用/)
  const api = readFileSync(new URL('../src/api/ai/knowledge.js', import.meta.url), 'utf8')
  assert.match(api, /uploadKnowledgeDocument\(data, requestKey\)/);assert.match(api, /'Content-Type': 'multipart\/form-data', 'Idempotency-Key': requestKey, repeatSubmit: false/)
})
test('成员、忙碌、待确认及活动任务不接受第二次上传', async () => {
  for (const change of [{canTestKnowledge: false}, {uploadBusy: true}, {uploadPending: {}}, {rebuildTaskActive: true}, {currentKb: {indexStatus: 'BUILDING'}}]) {
    const {context,sent} = state();Object.assign(context, change);assert.equal(await context.beforeUpload(file), false);assert.equal(sent.length, 0)
  }
})
test('成功回执交给任务卡片，不把上传成功伪装成解析成功', async () => {
  const {context,sent,accepted,forms} = state();assert.equal(await context.beforeUpload(file), true);await context.uploadDocumentRequest()
  assert.equal(sent.length, 1);assert.equal(sent[0][1], 'synthetic-upload-key');assert.equal(forms[0].entries[0][1], 7);assert.equal(forms[0].entries[1][1], file)
  assert.equal(accepted[0].state, 'QUEUED');assert.equal(context.uploadPending, null);assert.equal(context.uploadBusy, false)
})
test('网络结果不明仅保留内存中的原库文件和键，用户确认后才再次请求', async () => {
  let calls = 0
  const {context,sent} = state(async () => {if (++calls === 1) throw new Error('network ambiguous');return {data: {task: {knowledgeBaseId: 7, state: 'QUEUED'}}}})
  await context.beforeUpload(file);const pending = context.uploadPending;await context.uploadDocumentRequest()
  assert.equal(context.uploadRetry, true);assert.equal(context.uploadPending, pending);assert.equal(sent.length, 1)
  await context.retryUpload();assert.equal(sent.length, 2);assert.equal(sent[0][1], sent[1][1]);assert.equal(context.uploadPending, null)
  assert.doesNotMatch(page, /sessionStorage|localStorage/)
})
test('上传中切库后不会把旧库任务或刷新回写到新库', async () => {
  const wait = deferred(), {context,sent,accepted,notices} = state(() => wait.promise)
  await context.beforeUpload(file);const sending = context.uploadDocumentRequest();context.currentKbId = 8
  wait.resolve({data: {task: {knowledgeBaseId: 7, state: 'QUEUED'}}});await sending
  assert.equal(sent.length, 1);assert.equal(accepted.length, 0);assert.equal(notices.length, 0);assert.equal(context.uploadPending, null)
})
test('切库后不能重试原上传，回执所属库不符也不接受', async () => {
  const {context,sent,accepted} = state(async () => ({data: {task: {knowledgeBaseId: 8, state: 'SUCCEEDED'}}}))
  await context.beforeUpload(file);await context.uploadDocumentRequest();assert.equal(context.uploadRetry, true);assert.equal(accepted.length, 0)
  context.currentKbId = 8;await context.retryUpload();assert.equal(sent.length, 1)
})
test('队列拒绝提示文件已保存，不谎称上传失败或自动重派', async () => {
  const {context,sent,accepted,notices} = state(async () => ({data: {task: {knowledgeBaseId: 7, state: 'REJECTED'}}}))
  await context.beforeUpload(file);await context.uploadDocumentRequest();assert.equal(sent.length, 1);assert.equal(accepted[0].state, 'REJECTED')
  assert.match(notices[0], /文件已保存/);assert.match(notices[0], /不会自动调用模型/);assert.equal(context.uploadPending, null)
})
test('放弃本页重试不操作服务端，迟到确认不能清除另一笔上传', async () => {
  const {context,sent} = state();await context.beforeUpload(file);const wait = deferred();context.$modal.confirm = () => wait.promise
  const forgetting = context.forgetUpload();const newer = {knowledgeId: 7, key: 'new-key', file};context.uploadPending = newer;wait.resolve();await forgetting
  assert.equal(context.uploadPending, newer);assert.equal(sent.length, 0);assert.match(page, /不会撤销服务端已保存/)
})
test('已有终态任务时待解析文档不触发无限轮询', () => {
  const {context} = state();context.docList = [{status: '0'}];context.rebuildTaskState = 'REJECTED';assert.equal(context.shouldPollStatus(), false)
  context.rebuildTaskState = null;assert.equal(context.shouldPollStatus(), true);context.rebuildTaskActive = true;assert.equal(context.shouldPollStatus(), true)
})
test('导入和启动任务中文标识，导入完成仍提示核对安全隔离', () => {
  const component = readFileSync(new URL('../src/components/knowledge/KnowledgeRebuildTask.vue', import.meta.url), 'utf8')
  assert.match(component, /IMPORT: '上传导入'/);assert.match(component, /STARTUP: '启动重建'/);assert.match(component, /导入处理完成/)
  assert.match(component, /安全隔离的文件不会向量化/)
})
