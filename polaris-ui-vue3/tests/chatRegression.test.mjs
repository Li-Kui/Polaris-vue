import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'

const chatView = await readFile(new URL('../src/views/ai/chat.vue', import.meta.url), 'utf8')
const floatChat = await readFile(new URL('../src/components/AiFloatChat/index.vue', import.meta.url), 'utf8')
const chatApi = await readFile(new URL('../src/api/ai/chat.js', import.meta.url), 'utf8')

test('悬浮对话使用模型配置 ID 调用会话接口', () => {
  assert.match(floatChat, /v-model="selectedModelConfigId"/)
  assert.match(floatChat, /:value="item\.id"/)
  assert.match(floatChat, /createConversation\(this\.selectedModelConfigId, this\.selectedKbId\)/)
  assert.doesNotMatch(floatChat, /createConversation\(this\.selectedModelName/)
})

test('普通对话恢复推理内容并忽略过期的会话请求', () => {
  assert.match(chatView, /reasoningContent: m\.reasoningContent \|\| ''/)
  assert.match(chatView, /loadVersion !== this\.messageLoadVersion/)
  assert.match(chatView, /Number\(this\.currentConvId\) !== Number\(convId\)/)
})

test('普通对话和悬浮对话都处理异常断流并支持 AbortController', () => {
  for (const source of [chatView, floatChat]) {
    assert.match(source, /chatAbortController/)
    assert.match(source, /signal: chatController\.signal/)
    assert.match(source, /连接已中断，未收到完成信号/)
  }
})

test('停止生成会通知后端释放会话后再恢复输入', () => {
  assert.match(chatApi, /conversations\/\$\{conversationId\}\/cancel/)
  for (const source of [chatView, floatChat]) {
    assert.match(source, /await cancelChatGeneration\(conversationId\)/)
    assert.match(source, /abortStream\(false\)/)
  }
})

test('取消响应读取器会处理异步拒绝', () => {
  assert.match(chatView, /Promise\.resolve\(this\.currentReader\.cancel\(\)\)\.catch\(\(\) => \{\}\)/)
})
