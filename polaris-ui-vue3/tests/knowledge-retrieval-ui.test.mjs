import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import * as sass from 'sass'

const source = readFileSync(new URL('../src/components/knowledge/KnowledgeRetrievalTest.vue', import.meta.url), 'utf8')
const {descriptor} = parse(source)
const compiled = compileScript(descriptor, {id: 'knowledge-retrieval-test'})
const script = compiled.scriptAst.filter(node => node.type === 'ImportDeclaration')
  .sort((a, b) => b.start - a.start)
  .reduce((value, node) => value.slice(0, node.start) + value.slice(node.end), descriptor.script.content)
  .replace('export default {', 'const component = {')

function component(api = async () => ({data: {state: 'EMPTY', results: []}})) {
  return vm.runInNewContext(`${script}\ncomponent`, {testKnowledgeRetrieval: api, KnowledgeRelatedDocumentPicker: {}})
}
function state(options = {}, api) {
  const definition = component(api)
  const context = {...definition.data(), knowledge: {id: 4, indexStatus: 'READY', indexVersion: 2}, canTest: true,
    query: '合成问题', embeddingConfirmed: true, ...options}
  for (const [name, method] of Object.entries(definition.methods)) context[name] = method.bind(context)
  Object.defineProperty(context, 'canRun', {get: () => definition.computed.canRun.call(context)})
  return {definition, context}
}
function deferred() {
  let resolve, reject
  const promise = new Promise((accept, fail) => {resolve = accept; reject = fail})
  return {promise, resolve, reject}
}

test('检索测试模板与主题样式可编译，正文只使用转义文本', () => {
  assert.deepEqual(compileTemplate({source: descriptor.template.content, filename: 'KnowledgeRetrievalTest.vue',
    id: 'knowledge-retrieval-test', compilerOptions: {bindingMetadata: compiled.bindings}}).errors, [])
  assert.ok(sass.compileString(descriptor.styles[0].content).css.length > 0)
  assert.doesNotMatch(descriptor.template.content, /v-html/)
  assert.match(descriptor.template.content, /不代表答案正确率/)
  assert.match(descriptor.template.content, /可能发送至外部模型服务并产生费用/)
  assert.match(descriptor.template.content, /不是全文解析预览/)
  assert.match(descriptor.styles[0].content, /is-disabled\.is-disabled/)
  assert.match(descriptor.styles[0].content, /color: inherit !important/)
})

test('对话来源标签显示向量匹配分，不展示为答案置信度百分比', () => {
  const chat = readFileSync(new URL('../src/views/ai/chat.vue', import.meta.url), 'utf8')
  const panel = readFileSync(new URL('../src/components/knowledge/ChatKnowledgeSources.vue', import.meta.url), 'utf8')
  assert.match(panel, /匹配分 \{\{ Number\(doc\.confidence\)\.toFixed\(3\) \}\}/)
  assert.doesNotMatch(panel, /Math\.round\(doc\.confidence \* 100\)/)
  assert.match(chat, /v-if="msg\.knowledgeRetrievalEmpty"/)
  assert.match(chat, /以下回答不应视为知识库事实/)
  assert.match(chat, /aiMessage\.knowledgeRetrievalEmpty = payload\.state === 'EMPTY'/)
  const backend = readFileSync(new URL('../../polaris-ai/src/main/java/com/polaris/ai/service/impl/AiChatServiceImpl.java', import.meta.url), 'utf8')
  assert.doesNotMatch(backend, /EmbeddingStoreContentRetriever|builder\.contentRetriever\(/)
  assert.doesNotMatch(backend, /SystemMessage\.from\(recResult/)
  assert.doesNotMatch(backend, /log\.info\([^\n]*jsonDocs/)
})

test('未授权、未确认、未就绪及无效参数都不派发请求', async () => {
  let calls = 0
  for (const options of [{canTest: false}, {embeddingConfirmed: false}, {knowledge: {id: 4, indexStatus: 'FAILED'}},
    {query: ' '}, {topK: 21}, {minScore: NaN}]) {
    const {context} = state(options, async () => {calls++; return {data: {state: 'EMPTY', results: []}}})
    await context.runTest()
  }
  assert.equal(calls, 0)
})

test('只提交本次测试参数，不保存知识库或携带租户/collection', async () => {
  let request
  const {context} = state({}, async (id, payload) => {request = {id, payload}; return {data: {state: 'EMPTY', results: []}}})
  await context.runTest()
  assert.equal(request.id, 4)
  assert.equal(JSON.stringify(request.payload), JSON.stringify({query: '合成问题', topK: 5, minScore: 0.5, embeddingConfirmed: true}))
  assert.equal(context.result.state, 'EMPTY')
  assert.equal(context.loading, false)
})

test('修改问题后丢弃慢响应，旧调用完成前禁止重复派发', async () => {
  const pending = deferred()
  let calls = 0
  const {context} = state({}, () => {calls++; return pending.promise})
  const first = context.runTest()
  context.query = '新问题'; context.invalidateResult()
  await context.runTest()
  assert.equal(calls, 1); assert.equal(context.loading, true)
  pending.resolve({data: {state: 'FOUND', results: [{content: '过时资料'}]}})
  await first
  assert.equal(context.result, null); assert.equal(context.loading, false)
})

test('切换知识库会清空问题、确认与旧结果，旧异常不污染新页面', async () => {
  const pending = deferred()
  const {definition, context} = state({}, () => pending.promise)
  const first = context.runTest()
  context.knowledge = {id: 5, indexStatus: 'READY', retrievalTopK: 8, retrievalMinScore: 0.4}
  definition.watch['knowledge.id'].handler.call(context)
  assert.equal(context.query, ''); assert.equal(context.embeddingConfirmed, false); assert.equal(context.topK, 8)
  pending.reject(new Error('旧错误')); await first
  assert.equal(context.error, ''); assert.equal(context.result, null); assert.equal(context.loading, false)
})

test('本次错误持久显示，不冒充空结果，不自动重试', async () => {
  let calls = 0
  const {context} = state({}, async () => {calls++; throw new Error('索引已变化，请刷新')})
  await context.runTest()
  assert.equal(context.error, '索引已变化，请刷新'); assert.equal(context.result, null)
  assert.equal(calls, 1); assert.equal(context.loading, false)
})

test('参数变化会将既有结果标记过时，清空后不保留正文', () => {
  const {context} = state({result: {state: 'FOUND', results: [{content: '合成片段'}]}})
  context.invalidateResult(); assert.equal(context.stale, true)
  context.clearTest(); assert.equal(context.result, null); assert.equal(context.query, ''); assert.equal(context.embeddingConfirmed, false)
})

test('诊断 API 不把问题放 URL 或写入通用 sessionStorage 防重复缓存', () => {
  const api = readFileSync(new URL('../src/api/ai/knowledge.js', import.meta.url), 'utf8')
  const start = api.indexOf('export function testKnowledgeRetrieval')
  const next = api.indexOf('\nexport function ', start + 1)
  const method = api.slice(start, next < 0 ? undefined : next)
  assert.match(method, /method: 'post'/); assert.match(method, /repeatSubmit: false/)
  assert.doesNotMatch(method, /params:|sessionStorage|localStorage/)
  const page = readFileSync(new URL('../src/views/ai/knowledge.vue', import.meta.url), 'utf8')
  assert.match(page, /user\.role === 'admin'/)
  assert.match(page, /Number\(useUserStore\(\)\.id\) === 1/)
})

test('显式关联计划未核对或容量不足时不派发，后端不确认策略时不冒充补全成功', async () => {
  let calls = 0
  for (const options of [{relatedReady: false}, {relatedReady: true, topK: 1}]) {
    const {context} = state({relatedDocumentIds: ['12'], ...options}, async () => {calls++; return {data: {state: 'FOUND', results: []}}})
    await context.runTest()
  }
  assert.equal(calls, 0)
  let payload
  const {context} = state({relatedDocumentIds: ['12'], relatedReady: true}, async (id, body) => {
    payload = body; calls++; return {data: {strategy: 'VECTOR_V1', state: 'FOUND', results: []}}
  })
  await context.runTest()
  assert.equal(JSON.stringify(payload.relatedDocumentIds), '["12"]')
  assert.equal(calls, 1); assert.equal(context.result, null); assert.match(context.error, /未确认关联补全策略/)
})
