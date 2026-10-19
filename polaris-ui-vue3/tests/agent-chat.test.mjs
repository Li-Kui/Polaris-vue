import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import {
    agentChatInput,
    chatHistoryOptions,
    consumeAgentChatStream,
    decorateAgentHistoryCitations,
    freshChatOptions,
    isActiveChatRequest
} from '../src/components/agent/agentChat.js'

const source = path => readFileSync(new URL(path, import.meta.url), 'utf8')
const input = options => agentChatInput({conversationId: 3, question: '追问', knowledgeBaseId: 4,
  options: {...freshChatOptions(), agentId: 9, ...options}, requestKey: 'test-key'})
const prepared = {requestId: 'request-1', agentId: 9, status: 'PREPARED', answerMode: 'QUERY_EVIDENCE', expiresAt: '2099-01-01T00:00:00Z'}
function fixture(overrides = {}) {
  const calls = [], storage = new Map(), notices = []
  const store = {tenant: {id: 6}, user: {id: 8, role: 'admin'}}
  const api = {
    prepareAgentChat: async data => {calls.push(['prepare', data]); return {code: 200, data: prepared}},
    cancelAgentChat: async id => {calls.push(['cancel', id]); return {code: 200, data: {...prepared, status: 'CANCELLED'}}},
    getAgentChatRequest: async id => {calls.push(['get', id]); return {code: 200, data: {...prepared, status: 'SUCCEEDED'}}},
    getAgentChatRun: async id => {calls.push(['run', id]); return {code: 200, data: {...prepared, status: 'SUCCEEDED'}}},
    streamAgentChat: async () => {calls.push(['stream'])}, ...overrides
  }
  const mixin = vm.runInNewContext(source('../src/components/agent/agentChatMixin.js').replace(/^import .*$/gm, '').replace('export default', 'mixin ='), {
    mixin: null, usePlatformUserStore: () => store, isPlatformConsolePath: path => path.startsWith('/platform/console/'),
    freshChatOptions, agentChatInput, isActiveChatRequest, chatStageLabel: s => s, ...api,
    sessionStorage: {setItem: (key, value) => storage.set(key, value), getItem: key => storage.get(key), removeItem: key => storage.delete(key)},
    crypto: {randomUUID: () => 'test-key'}, AbortController, Date
  })
  const ctx = {...mixin.data(), collaborationAllowed: true, collaborationPending: false, currentConvId: 3, agents: [],
    selectedKbId: 4, inputText: '追问', attachments: [], relatedDocumentIds: [], uploadingAttachment: false,
    messages: [], isStreaming: false, $message: {warning: m => notices.push(m)}, $nextTick: f => f(), scrollToBottom() {}, focusInput() {},
    loadMessageList: async id => {calls.push(['messages', id])}, loadConvList: async () => {calls.push(['conversations'])}}
  for(const [name, method] of Object.entries(mixin.methods)) ctx[name] = method.bind(ctx)
  ctx.collaboration = {...freshChatOptions(), enabled: true, agentId: 9}
  return {ctx, mixin, calls, storage, notices, store}
}
test('请求白名单排除附件、身份、覆盖参数和任意前文', () => {
  const value = input({attachments: ['secret'], ownerToken: 'secret', messages: ['secret'], temperature: 1})
  assert.deepEqual(Object.keys(value), ['conversationId','requestKey','agentId','question','knowledgeBaseId','externalQuery',
    'allowExternalTools','answerMode','contextStartMessageId','inheritPreviousContext'])
  assert.equal(Object.isFrozen(value), true)
})
test('继续前文关闭外发，查询需独立的外发内容', () => {
  assert.equal(input({answerMode: 'CONTINUE_HISTORY', allowExternalTools: true, externalQuery: 'secret'}).externalQuery, '')
  assert.equal(input({answerMode: 'CONTINUE_HISTORY', allowExternalTools: true}).allowExternalTools, false)
  assert.throws(() => input({allowExternalTools: true}), /外发/)
  assert.equal(input({allowExternalTools: true, externalQuery: '  公开查询  '}).externalQuery, '公开查询')
  assert.throws(() => agentChatInput({conversationId: 3, question: 'q', options: {...freshChatOptions(), agentId: 1}, requestKey: 'x'}), /知识库/)
})
test('长整数 ID 不丢精度，问题长度和无效模式在准备前拦截', () => {
  const id = '9223372036854775807'
  assert.equal(input({agentId: id}).agentId, id)
  assert.throws(() => input({agentId: '1<script>'}), /智能体/)
  assert.throws(() => input({answerMode: 'UNKNOWN'}), /方式/)
  assert.throws(() => agentChatInput({conversationId: 3, question: 'a'.repeat(4001), options: {agentId: 1}}), /4000/)
})
test('前文只显示同 run 已保存的完整轮次', () => {
  assert.deepEqual(chatHistoryOptions([{id: 1, role: 'user', content: 'q', agentRunId: 'a'}, {id: 2, role: 'assistant', agentRunId: 'a'},
    {id: 3, role: 'user', agentRunId: 'b'}, {id: 4, role: 'assistant', agentRunId: 'c'}, {id: 5, role: 'user'}]), [{id: 1, label: 'q'}])
})
function byteStream(text) {
  const bytes = new TextEncoder().encode(text)
  return new ReadableStream({start(controller) {for(const byte of bytes)controller.enqueue(new Uint8Array([byte])); controller.close()}})
}
test('单字节 SSE 分片保持中文、CRLF、多行 data 与终态', async () => {
  const events = []
  await consumeAgentChatStream(byteStream('event: message\r\ndata: {"text":\r\ndata: "中文🙂"}\r\n\r\nevent: done\r\ndata: {"status":"SUCCEEDED"}\r\n\r\n'), (name, value) => events.push([name,value]))
  assert.deepEqual(events, [['message', {text: '中文🙂'}], ['done', {status: 'SUCCEEDED'}]])
})
test('EOF 与坏 JSON 不伪造完成，existing 只读返回', async () => {
  await assert.rejects(consumeAgentChatStream(byteStream('event: message\ndata: {"text":"q"}\n\n'), () => {}), /不会自动重试/)
  await assert.rejects(consumeAgentChatStream(byteStream('event: done\ndata: broken\n\n'), () => {}), /格式异常/)
  const events = []
  await consumeAgentChatStream(byteStream('event: existing\ndata: {"status":"RUNNING"}\n\n'), name => events.push(name))
  assert.deepEqual(events, ['existing'])
})
test('历史链接仅来自可访问指针，不装饰代码、属性和现有链接', () => {
  const message = {id: 5, collaborationSources: {history: [{available: true, reference: {messageId: 2, citationId: 'S1'}, knowledge: [{}]},
    {available: false, reference: {messageId: 3, citationId: 'S1'}, knowledge: [{}]}]}}
  const decorated = decorateAgentHistoryCitations('<p title="[H2:S1]">[H2:S1] [H3:S1] [H2:T1]</p><code>[H2:S1]</code><a href="x">[H2:S1]</a>', message)
  assert.equal((decorated.match(/data-agent-history=/g) || []).length, 1)
  assert.match(decorated, /<code>\[H2:S1\]<\/code>/)
  assert.match(decorated, /title="\[H2:S1\]"/)
  assert.equal(decorateAgentHistoryCitations('[H2:S1]', {...message, streaming: true}), '[H2:S1]')
})
test('准备只冻结和确认；返回修改仅取消，不执行或清空问题', async () => {
  const {ctx, calls, storage} = fixture()
  await ctx.prepareCollaboration()
  assert.deepEqual(calls.map(c => c[0]), ['prepare'])
  assert.equal(ctx.inputText, '追问'); assert.equal(ctx.messages.length, 0); assert.equal(ctx.isStreaming, true)
  assert.deepEqual([...storage.values()], ['request-1']); assert.equal([...storage.keys()][0], 'polaris.agent-chat:6:8:3')
  await ctx.dismissCollaboration()
  assert.deepEqual(calls.map(c => c[0]), ['prepare', 'cancel'])
  assert.equal(ctx.inputText, '追问'); assert.equal(ctx.isStreaming, false)
})
test('附件或指定资料不被静默丢弃后执行', async () => {
  const {ctx, calls} = fixture()
  ctx.attachments = [{name: 'private'}]; await ctx.prepareCollaboration()
  ctx.attachments = []; ctx.relatedDocumentIds = [1]; await ctx.prepareCollaboration()
  assert.equal(calls.length, 0)
})
test('审核保存事件前不插入用户消息，之后真实消息 ID 只插入一次', async () => {
  const {ctx, calls} = fixture({streamAgentChat: async (id, input, emit) => {
    calls.push(['stream', id]); assert.equal(ctx.messages.filter(m => m.role === 'user').length, 0)
    await emit('started', {requestId: id, runId: 'run-1'})
    await emit('user_message_saved', {messageId: 10}); await emit('user_message_saved', {messageId: 10})
    assert.equal(ctx.messages.filter(m => m.role === 'user').length, 1); assert.equal(ctx.messages[0].agentRunId, 'run-1')
    await emit('message_saved', {messageId: 11}); await emit('message', {text: '已保存回答'})
    assert.equal(ctx.messages[1].id, 11); await emit('done', {...prepared, status: 'SUCCEEDED'})
  }})
  ctx.collaboration.allowExternalTools = true; ctx.collaboration.externalQuery = '公开查询'
  await ctx.prepareCollaboration(); await ctx.sendConfirmedCollaboration()
  assert.equal(calls.filter(c => c[0] === 'stream').length, 1); assert.equal(ctx.collaboration.answerMode, 'CONTINUE_HISTORY')
  assert.equal(ctx.collaboration.allowExternalTools, false); assert.equal(ctx.collaboration.externalQuery, '')
})
test('断线只核对，不重发；未保存的问题回到输入框', async () => {
  const {ctx, calls} = fixture({streamAgentChat: async () => {calls.push(['stream']); throw new Error('EOF')},
    getAgentChatRequest: async id => {calls.push(['get', id]); return {code: 200, data: {...prepared, status: 'NEEDS_ATTENTION'}}}})
  await ctx.prepareCollaboration(); await ctx.sendConfirmedCollaboration()
  assert.equal(calls.filter(c => c[0] === 'stream').length, 1); assert.equal(calls.filter(c => c[0] === 'get').length, 1)
  assert.equal(ctx.collaborationObserved.status, 'NEEDS_ATTENTION'); assert.equal(ctx.inputText, '追问')
})
test('刷新从主体与会话定位符只读恢复；未知状态阻止重复发送', async () => {
  const {ctx, calls, storage} = fixture(); storage.set('polaris.agent-chat:6:8:3', 'request-1')
  await ctx.restoreCollaboration(3, [])
  assert.deepEqual(calls.map(c => c[0]), ['get']); assert.equal(ctx.collaboration.answerMode, 'CONTINUE_HISTORY')
  const other = fixture({getAgentChatRequest: async () => {throw new Error('offline')}})
  other.storage.set('polaris.agent-chat:6:8:3', 'request-1'); await other.ctx.restoreCollaboration(3, [])
  assert.equal(other.ctx.collaborationObserved.status, 'UNKNOWN'); assert.equal(isActiveChatRequest(other.ctx.collaborationObserved), true)
})
test('无定位符从最后已保存的 run 恢复，普通回答不激活协作', async () => {
  const {ctx, calls} = fixture(); await ctx.restoreCollaboration(3, [{role: 'assistant', id: 4, agentRunId: 'run-1'}])
  assert.deepEqual(calls.map(c => c[0]), ['run'])
  const other = fixture(); await other.ctx.restoreCollaboration(3, [{role: 'assistant', id: 4}]); assert.equal(other.calls.length, 0)
})
test('新标签页无定位符时从最后一条失败用户消息恢复，不能显示旧成功状态', async () => {
  const {ctx, calls} = fixture({getAgentChatRun: async id => {
    calls.push(['run', id]); return {code: 200, data: {...prepared, runId: id, status: 'NEEDS_ATTENTION'}}
  }})
  await ctx.restoreCollaboration(3, [{id: 4, role: 'assistant', agentRunId: 'old-success'},
    {id: 5, role: 'user', agentRunId: 'latest-failed'}])
  assert.deepEqual(calls, [['run', 'latest-failed']])
  assert.equal(ctx.collaborationObserved.status, 'NEEDS_ATTENTION')
})
test('最后一条普通用户消息不能被跳过以恢复更早协作', async () => {
  const {ctx, calls} = fixture()
  await ctx.restoreCollaboration(3, [{id: 4, role: 'assistant', agentRunId: 'old-success'}, {id: 5, role: 'user'}])
  assert.equal(calls.length, 0)
})
test('观察者卸载不取消运行，持有本地流时才取消原请求', () => {
  const {ctx, calls} = fixture(); ctx.collaborationObserved = {...prepared, status: 'RUNNING'}
  ctx.abortCollaboration(); assert.equal(calls.length, 0)
  ctx.collaborationController = new AbortController(); const signal = ctx.collaborationController.signal
  ctx.abortCollaboration(); assert.equal(signal.aborted, true); assert.deepEqual(calls.map(c => c.slice(0,2)), [['cancel','request-1']])
})
test('切换会话后旧停止响应不覆盖新会话状态或刷新新消息', async () => {
  let finish
  const {ctx, calls} = fixture({cancelAgentChat: () => new Promise(resolve => {finish = resolve})})
  ctx.collaborationObserved = {...prepared, status: 'RUNNING'}
  const pending = ctx.stopCollaboration()
  ctx.currentConvId = 7; ctx.collaborationVersion++; ctx.collaborationObserved = null
  finish({code: 200, data: {...prepared, status: 'CANCELLED'}}); await pending
  assert.equal(ctx.collaborationObserved, null); assert.equal(calls.length, 0)
})
test('切换协作不会静默清空已选择的关联资料', () => {
  const {ctx, mixin} = fixture(); ctx.relatedDocumentIds = [11]
  mixin.watch['collaboration.enabled'].call(ctx, true)
  assert.deepEqual(ctx.relatedDocumentIds, [11])
})
test('准备期间停止使迟到确认失效并取消迟到请求', async () => {
  let finish
  const {ctx, calls} = fixture({prepareAgentChat: () => new Promise(resolve => {finish = resolve})})
  const pending = ctx.prepareCollaboration(); await ctx.stopCollaboration(); finish({code: 200, data: prepared}); await pending
  assert.equal(ctx.collaborationConfirmation, null); assert.equal(ctx.isStreaming, false); assert.deepEqual(calls.map(c => c[0]), ['cancel'])
})
test('协作控件、来源卡片、引用抽屉和现有聊天页面编译通过', () => {
  for(const file of ['../src/views/ai/chat.vue','../src/components/agent/AgentChatComposer.vue',
    '../src/components/agent/AgentChatMessage.vue','../src/components/knowledge/ChatKnowledgeCitationDialog.vue']) {
    const {descriptor, errors} = parse(source(file), {filename: file}); assert.deepEqual(errors, [])
    const script = compileScript(descriptor, {id: 'g32'})
    assert.deepEqual(compileTemplate({source: descriptor.template.content, filename: file, id:'g32', compilerOptions: {bindingMetadata: script.bindings}}).errors, [])
  }
})
