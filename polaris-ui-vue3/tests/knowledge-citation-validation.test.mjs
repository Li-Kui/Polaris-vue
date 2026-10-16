import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import {knowledgeCitationNotice} from '../src/utils/knowledgeEvidence.js'

const result=(state,referencedCount=1,invalidCount=0)=>({version:1,state,referencedCount,invalidCount})

test('生成中的来源不能提前展示核对通过，普通聊天不冒出引用判断',()=>{
  assert.equal(knowledgeCitationNotice(result('IDS_VALID'),true,true).kind,'pending')
  assert.equal(knowledgeCitationNotice(null,false,false),null)
  assert.equal(knowledgeCitationNotice(null,false,true).kind,'warning')
})
test('最终编号通过只表示来源集合成员关系，不冒充答案支持度或当前授权',()=>{
  const notice=knowledgeCitationNotice(result('IDS_VALID'),false,true)
  assert.equal(notice.kind,'checked');assert.match(notice.text,/未核验证据支持度或当前访问状态/)
})
test('无效编号和未标注编号都有明确提示，不从模型正文自己判定通过',()=>{
  assert.match(knowledgeCitationNotice(result('INVALID_IDS',1,1),false,true).text,/无效或不属于本轮来源/)
  assert.match(knowledgeCitationNotice(result('MISSING_IDS',0),false,true).text,/未标注/)
})
test('旧事件、未知版本、损坏状态和不一致计数不能伪装核对通过',()=>{
  for(const value of [null,{},result('UNKNOWN'),{...result('IDS_VALID'),version:2},result('IDS_VALID',0),result('IDS_VALID',1,1),result('MISSING_IDS',1),result('INVALID_IDS',1,0),result('IDS_VALID',51),result('IDS_VALID','1'),result('INVALID_IDS',1,100001)])
    assert.equal(knowledgeCitationNotice(value,false,true).kind,'warning')
})
test('提示组件可编译且只展示固定文本，不注入模型HTML或主动请求',()=>{
  const source=readFileSync(new URL('../src/components/knowledge/KnowledgeCitationValidationNotice.vue',import.meta.url),'utf8')
  const {descriptor}=parse(source),compiled=compileScript(descriptor,{id:'citation-validation'})
  assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:'KnowledgeCitationValidationNotice.vue',id:'citation-validation',compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
  assert.doesNotMatch(source,/v-html|fetch\(|setInterval|setTimeout/)
})
