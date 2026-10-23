import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileScript, compileStyle, compileTemplate, parse} from '@vue/compiler-sfc'
import {
    decorateKnowledgeCitations,
    knowledgeSourceGroups,
    referencedKnowledgeCitationIds
} from '../src/utils/knowledgeEvidence.js'

const docs=[{docId:1,docName:'现行手册',citationIds:['S1','S3','S5'],confidence:0.7},
  {docId:2,docName:'旧手册',citationIds:['S2']},{docId:3,docName:'其他参考',citationIds:['S4']}]
const message={id:10,content:'现行规则 [S1]，不采用旧规则 [S2]。',attachedDocs:docs}
const source=readFileSync(new URL('../src/components/knowledge/ChatKnowledgeSources.vue',import.meta.url),'utf8')
const {descriptor}=parse(source)

test('来源折叠组件可编译，默认收起，文档数、片段数与回答标注分开',()=>{
  const compiled=compileScript(descriptor,{id:'knowledge-sources'})
  assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:'ChatKnowledgeSources.vue',id:'knowledge-sources',compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
  for(const style of descriptor.styles)assert.deepEqual(compileStyle({source:style.content,filename:'ChatKnowledgeSources.vue',id:'knowledge-sources'}).errors,[])
  assert.match(descriptor.template.content,/<details class="knowledge-sources">/)
  assert.match(descriptor.template.content,/<summary/);assert.match(source,/份文档/);assert.match(source,/个片段/)
  assert.match(source,/回答标注/);assert.match(source,/本轮其他参考/);assert.match(source,/不代表结论已核实/)
  assert.doesNotMatch(source,/v-html|fetch\(|setInterval|setTimeout/)
})
test('按文档合并实际片段，不重排编号、不修改输入，也不将未标注来源算作已引用',()=>{
  const before=JSON.stringify(docs),groups=knowledgeSourceGroups(docs,message.content)
  assert.equal(groups.length,3);assert.equal(groups.flatMap(d=>d.citationIds).length,5)
  assert.equal(groups.filter(d=>d.referencedIds.length).length,2)
  assert.deepEqual(groups[0].referencedIds,['S1']);assert.deepEqual(groups[2].referencedIds,[])
  assert.equal(JSON.stringify(docs),before)
  assert.equal(knowledgeSourceGroups([...docs,{...docs[0],citationIds:['S3']}],message.content).length,3)
})
test('仅本消息合法编号可点击，伪造、缺失及跨文档歧义编号不生成按钮',()=>{
  const html=decorateKnowledgeCitations('原文 [S1] [S9] [S0] [S51]',{...message,content:'原文 [S1] [S9] [S0] [S51]'})
  assert.equal((html.match(/data-knowledge-citation=/g)||[]).length,1)
  assert.match(html,/data-knowledge-citation="S1"/);assert.match(html,/\[S9\] \[S0\] \[S51\]/)
  const ambiguous=[...docs,{docId:9,docName:'不同文档',citationIds:['S1']}]
  assert.deepEqual(referencedKnowledgeCitationIds('[S1]',ambiguous),[])
  assert.equal(decorateKnowledgeCitations('[S1]',{...message,content:'[S1]',attachedDocs:ambiguous}),'[S1]')
})
test('流式未保存、被拦截、旧记录或无来源的回答不生成可点击引用',()=>{
  for(const options of [{id:null},{streaming:true},{moderationStatus:'INTERRUPTED_BLOCKED'},{attachedDocs:[]},{attachedDocs:[{docName:'旧资料'}]}])
    assert.equal(decorateKnowledgeCitations('[S1]',{...message,...options}),'[S1]')
})
test('不装饰 HTML 属性、代码或链接，编号按钮只有固定属性且不拼接文档名',()=>{
  const html='<pre><code>[S1]</code></pre><code>[S1]</code><a href="https://example.com/[S1]">[S1]</a><b title="[S1]">正文 [S1]</b>'
  const out=decorateKnowledgeCitations(html,{...message,content:'正文 [S1]'})
  assert.equal((out.match(/data-knowledge-citation=/g)||[]).length,1)
  assert.match(out,/<pre><code>\[S1\]<\/code><\/pre>/);assert.match(out,/title="\[S1\]"/)
  assert.match(out,/<a href="https:\/\/example.com\/\[S1\]">\[S1\]<\/a>/)
  assert.doesNotMatch(out,/现行手册|onclick|javascript:/)
  assert.deepEqual(referencedKnowledgeCitationIds('`[S1]`\n```txt\n[S2]\n```\n[S3](https://example.com)',docs),[])
})
test('旧记录保留文档名称但无查看编号，匹配分不冒充置信度',()=>{
  const groups=knowledgeSourceGroups([{docId:9,docName:'旧记录',confidence:0.65}],message.content)
  assert.deepEqual(groups[0].citationIds,[]);assert.equal(groups[0].confidence,0.65)
  assert.match(source,/旧记录暂无片段绑定/);assert.match(source,/!canOpen \|\| !doc.citationIds.length/)
})
test('损坏的旧来源数组不影响聊天渲染，也不生成虚假文档或按钮',()=>{
  assert.deepEqual(knowledgeSourceGroups([null,undefined,1,'资料',[]],message.content),[])
  assert.equal(decorateKnowledgeCitations('[S1]',{...message,attachedDocs:[null,1]}),'[S1]')
})
test('主页面打开引用再次校验当前消息绑定，不接受伪造、流式或拦截来源',()=>{
  const chat=readFileSync(new URL('../src/views/ai/chat.vue',import.meta.url),'utf8')
  const methods=chat.slice(chat.indexOf('    renderKnowledgeMarkdown(message)'),chat.indexOf('    getBoundConversationModel()'))
  const actions=vm.runInNewContext('({'+methods+'})',{decorateKnowledgeCitations,knowledgeSourceGroups})
  const context={...actions,currentConvId:77,citationSelection:null,renderMarkdown:text=>text}
  context.openKnowledgeCitation(message,'S9');assert.equal(context.citationSelection,null)
  context.openKnowledgeCitation({...message,moderationStatus:'INTERRUPTED_BLOCKED'},'S1');assert.equal(context.citationSelection,null)
  context.openKnowledgeCitation({...message,streaming:true},'S1');assert.equal(context.citationSelection,null)
  context.openKnowledgeCitation(message,'S1');assert.equal(context.citationSelection.conversationId,77)
  assert.equal(context.citationSelection.messageId,10);assert.deepEqual([...context.citationSelection.citationIds],['S1','S3','S5'])
})
test('来源折叠状态与权限只影响展示，不请求模型或改变现有后端接口',()=>{
  const component=vm.runInNewContext(descriptor.scriptSetup.content.replace(/^import .*$/gm,'')+'\n;({groups,fragmentCount,referencedCount,canOpen})',{
    defineProps:()=>({message}),defineEmits:()=>()=>{},computed:fn=>({get value(){return fn()}}),knowledgeSourceGroups,
    knowledgeCitationNotice:()=>null
  })
  assert.equal(component.fragmentCount.value,5);assert.equal(component.referencedCount.value,2);assert.equal(component.canOpen.value,true)
})
