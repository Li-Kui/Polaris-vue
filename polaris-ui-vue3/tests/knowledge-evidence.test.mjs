import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import {formatKnowledgeCitations} from '../src/utils/knowledgeEvidence.js'

test('显示实际分配的引用编号，保留顺序且去重，不按文档顺序编造',()=>{
  assert.equal(formatKnowledgeCitations({citationIds:['S1','S3','S1']}),'[S1] [S3]');assert.equal(formatKnowledgeCitations({citationIds:['S2']}),'[S2]')
  assert.equal(formatKnowledgeCitations({docId:1,docName:'资料'}),'')
})
test('忽略缺失、错误类型、非法编号和注入字符串，限制展示数量',()=>{
  for(const value of [null,undefined,{}, {citationIds:'S1'}])assert.equal(formatKnowledgeCitations(value),'')
  assert.equal(formatKnowledgeCitations({citationIds:['S0','S01','S51','<img>','[S1]',null,1,'S50']}),'[S50]')
  assert.equal(formatKnowledgeCitations({citationIds:Array.from({length:1000},(_,i)=>`S${i+1}`)}).split(' ').length,50)
})
test('页面将真实停止标记与旧EMPTY事件兼容，匹配分不冒充答案支持度',()=>{
  const source=readFileSync(new URL('../src/views/ai/chat.vue',import.meta.url),'utf8')
  assert.match(source,/aiMessage\.knowledgeAnswerStopped = payload\.answerStopped === true/);assert.match(source,/msg\.knowledgeAnswerStopped \?/)
  assert.match(source,/已停止回答生成/);assert.match(source,/取消知识库关联后使用普通聊天/)
  const panel=readFileSync(new URL('../src/components/knowledge/ChatKnowledgeSources.vue',import.meta.url),'utf8')
  assert.match(panel,/不代表片段足以支持答案/)
  assert.match(source,/ChatKnowledgeSources v-if="msg.attachedDocs\?\.length"/)
  assert.match(panel,/formatKnowledgeCitations\(\{citationIds: doc.referencedIds\}\)/);assert.match(panel,/formatKnowledgeCitations,/)
})
