import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import {knowledgeCitationIds} from '../src/utils/knowledgeEvidence.js'

const source=readFileSync(new URL('../src/components/knowledge/ChatKnowledgeCitationDialog.vue',import.meta.url),'utf8')
const {descriptor}=parse(source)
function state(fetch,initialSelection={conversationId:77,messageId:102,citationId:'S1'}) {
  let watchCallback,unmount,pending
  let selection=initialSelection
  const calls=[]
  const context=vm.runInNewContext(descriptor.scriptSetup.content.replace(/^import .*$/gm,'')+
    '\n;({loading,error,preview,activeCitation,selectCitation})',{
      defineProps:()=>({get selection(){return selection}}),defineEmits:()=>()=>{},ref:value=>({value}),
      watch:(_getter,callback)=>{watchCallback=callback;pending=callback(selection)},
      onBeforeUnmount:callback=>{unmount=callback},
      getKnowledgeCitation:(...args)=>{calls.push(args);return fetch(...args)}
    })
  return {context,calls,pending,load:value=>{selection=value;return watchCallback(value)},unmount:()=>unmount()}
}
test('实际编号可用于点击，拒绝模型伪造编号且不修改服务端数组',()=>{
  const refs=['S1','S3','S1','S51','<S2>'];assert.deepEqual(knowledgeCitationIds({citationIds:refs}),['S1','S3'])
  assert.equal(refs.length,5);assert.deepEqual(knowledgeCitationIds({citationIds:'S1'}),[])
})
test('片段弹窗可编译，正文作为文本而非HTML渲染',()=>{
  const compiled=compileScript(descriptor,{id:'chat-citation'})
  assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:'ChatKnowledgeCitationDialog.vue',id:'chat-citation',compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
  assert.doesNotMatch(descriptor.template.content,/v-html/)
  assert.match(descriptor.template.content,/<el-drawer/);assert.doesNotMatch(descriptor.template.content,/<el-dialog/)
  assert.match(descriptor.template.content,/size="min\(560px, 100vw\)"/)
})
test('抽屉只切换本消息同文档绑定编号，同一编号和伪造编号不重复请求',async()=>{
  const s=state(async(_conv,_message,id)=>({code:200,data:{citationId:id,text:'合成片段'}}),{conversationId:77,messageId:102,citationId:'S1',citationIds:['S1','S3']})
  await s.pending;await s.context.selectCitation('S1');await s.context.selectCitation('S2');await s.context.selectCitation('<S3>')
  assert.equal(s.calls.length,1)
  await s.context.selectCitation('S3');assert.deepEqual(s.calls,[[77,102,'S1'],[77,102,'S3']]);assert.equal(s.context.preview.value.citationId,'S3')
})
test('抽屉切换片段时清空旧内容，迟到的前一片段不能覆盖当前片段',async()=>{
  const resolvers=[],s=state(()=>new Promise(done=>resolvers.push(done)),{conversationId:77,messageId:102,citationId:'S1',citationIds:['S1','S3']})
  const next=s.context.selectCitation('S3');assert.equal(s.context.preview.value,null)
  resolvers[0]({code:200,data:{text:'旧'}});await s.pending;assert.equal(s.context.preview.value,null)
  resolvers[1]({code:200,data:{text:'新'}});await next;assert.equal(s.context.preview.value.text,'新')
})
test('显式打开只取消息绑定片段，显示真实片段及预算裁剪标记',async()=>{
  const preview={citationId:'S1',text:'<script>合成原文</script>',truncated:true,locations:['第 2 页']}
  const {context,calls,pending}=state(async()=>({code:200,data:preview}));await pending
  assert.deepEqual(calls,[[77,102,'S1']]);assert.equal(context.preview.value,preview);assert.equal(context.loading.value,false)
})
test('失效和网络异常清空旧正文，隐藏提供商细节且不自动重试',async()=>{
  const {context,calls,pending}=state(async()=>{throw new Error('/private/secret')});await pending
  assert.equal(context.preview.value,null);assert.match(context.error.value,/来源已失效/);assert.doesNotMatch(context.error.value,/secret/);assert.equal(calls.length,1)
})
test('切换引用和关闭弹窗后迟到响应不能重新显示正文',async()=>{
  const resolvers=[];const s=state(()=>new Promise(done=>resolvers.push(done)))
  const newer=s.load({conversationId:88,messageId:103,citationId:'S2'})
  resolvers[0]({code:200,data:{text:'旧片段'}});await s.pending;assert.equal(s.context.preview.value,null)
  resolvers[1]({code:200,data:{text:'新片段'}});await newer;assert.equal(s.context.preview.value.text,'新片段')
  const closing=s.load({conversationId:88,messageId:103,citationId:'S3'});s.unmount()
  resolvers[2]({code:200,data:{text:'关闭后迟到'}});await closing;assert.equal(s.context.preview.value,null)
})
