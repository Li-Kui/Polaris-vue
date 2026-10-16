import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import * as sass from 'sass'

const source = readFileSync(new URL('../src/components/knowledge/KnowledgeSnapshotCleanup.vue', import.meta.url), 'utf8')
const {descriptor} = parse(source), compiled = compileScript(descriptor, {id: 'snapshot-cleanup'})
const script = descriptor.script.content.replace(/^import .*$/gm, '').replace('export default {', 'const component = {')
const valid = {planId: 'synthetic-plan', eligibleCount: 1, blocked: false}
function state(preview = async () => ({data: valid}), execute = async () => ({data: {attempted: 1, cleaned: 1, retryWaiting: 0, manualRequired: 0}})) {
  let previews = 0, executions = 0
  const component = vm.runInNewContext(`${script}\ncomponent`, {KnowledgeSnapshotCleanupStatus:{},previewKnowledgeSnapshotCleanup: (...args) => {previews++;return preview(...args)},
    executeKnowledgeSnapshotCleanup: (...args) => {executions++;return execute(...args)}})
  const context = {...component.data(), knowledge: {id: 7}, canManage: true, $modal: {confirm: async () => {}}}
  for (const [key, method] of Object.entries(component.methods)) context[key] = method.bind(context)
  return {component,context,previews: () => previews,executions: () => executions}
}
function deferred() {let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {resolve,reject,promise}}

test('清理模板与样式可编译，危险范围和不可恢复提示明确，不自动请求', () => {
  assert.deepEqual(compileTemplate({source: descriptor.template.content,filename:'KnowledgeSnapshotCleanup.vue',id:'snapshot-cleanup',compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
  for(const style of descriptor.styles)assert.ok(sass.compileString(style.content).css.length)
  for(const text of ['24 小时','最多 20 条','文件删除不可恢复','不调用模型','不自动执行或重试','不删除原文件'])assert.ok(source.includes(text))
  assert.doesNotMatch(source,/v-html|setInterval|setTimeout|mounted\(|immediate: true|fileUrl|ownerToken|tenantId/)
  const {previews,executions}=state();assert.equal(previews(),0);assert.equal(executions(),0)
})
test('按钮悬停焦点及禁用文字使用可读的独立深浅色配色', () => {
  assert.match(source,/color: var\(--cleanup-accent\) !important/);assert.match(source,/html\.dark \.knowledge-cleanup/);assert.match(source,/focus-visible/)
  assert.doesNotMatch(source,/color: var\(--el-color-primary\)/)
  function lum(hex){const n=parseInt(hex.slice(1),16);return [n>>16,(n>>8)&255,n&255].map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((s,v,i)=>s+v*[.2126,.7152,.0722][i],0)}
  for(const [fg,bg]of [['#4338ca','#eef2ff'],['#93c5fd','#172554'],['#78350f','#fef3c7'],['#78350f','#fde68a'],['#475569','#f1f5f9'],['#94a3b8','#1e293b']]){const a=lum(fg),b=lum(bg);assert.ok((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5)}
})
test('无管理员或父库时不预览也不执行',async()=>{
  const {context,previews,executions}=state();context.canManage=false;context.plan=valid;await context.preview();await context.execute();context.canManage=true;context.knowledge={};await context.preview();await context.execute();assert.equal(previews(),0);assert.equal(executions(),0)
})
test('预览只读，不直接删除；零候选和活动保护不能执行',async()=>{
  const {context,previews,executions}=state();await context.preview();assert.equal(context.plan.planId,valid.planId);assert.equal(previews(),1);assert.equal(executions(),0)
  context.plan={...valid,eligibleCount:0};await context.execute();context.plan={...valid,blocked:true};await context.execute();assert.equal(executions(),0)
})
test('执行必须等待不可恢复确认，双击确认期间不会再派发',async()=>{
  const wait=deferred(),{context,executions}=state();let notice;context.plan=valid;context.$modal.confirm=t=>{notice=t;return wait.promise}
  const running=context.execute();await context.execute();assert.equal(executions(),0);assert.match(notice,/不可恢复/);assert.match(notice,/不会删除原文件/)
  wait.resolve();await running;assert.equal(executions(),1);assert.equal(context.result.cleaned,1);assert.equal(context.plan,null)
})
test('用户取消时保留预览，不删除也不显示执行错误',async()=>{
  const {context,executions}=state();context.plan=valid;context.$modal.confirm=async()=>{throw 'cancel'};await context.execute();assert.equal(executions(),0);assert.equal(context.plan,valid);assert.equal(context.error,'')
})
test('确认期间切库或改索引不会误删旧库或新库',async()=>{
  const wait=deferred(),{context,executions}=state();context.plan=valid;context.$modal.confirm=()=>wait.promise;const running=context.execute()
  context.knowledge={id:8};context.reset();wait.resolve();await running;assert.equal(executions(),0);assert.equal(context.plan,null)
})
test('换库后的慢预览和执行响应都不能覆盖新页面',async()=>{
  const wait=deferred(),{context}=state(()=>wait.promise);const running=context.preview();context.knowledge={id:8};context.reset();wait.resolve({data:valid});await running;assert.equal(context.plan,null)
  const execution=deferred(),other=state(undefined,()=>execution.promise).context;other.plan=valid;const sent=other.execute();await Promise.resolve();other.knowledge={id:8};other.reset();execution.resolve({data:{attempted:1,cleaned:1}});await sent;assert.equal(other.result,null)
})
test('预览失败清空旧计划，不伪装成零候选或自动重试',async()=>{
  const {context,previews,executions}=state(async()=>{throw new Error('unavailable')});context.plan=valid;await context.preview();assert.equal(context.plan,null);assert.match(context.error,/没有删除文件/);assert.equal(previews(),1);assert.equal(executions(),0)
})
test('执行结果不明清空计划并要求重新预览，不自动再次删除',async()=>{
  const {context,executions}=state(undefined,async()=>{throw new Error('ambiguous')});context.plan=valid;await context.execute();assert.equal(context.plan,null);assert.equal(context.result,null);assert.match(context.error,/不会自动再次执行/);assert.equal(executions(),1)
})
test('API只提交服务端计划与确认，不传文件路径或租户，保留服务端再鉴权',()=>{
  const api=readFileSync(new URL('../src/api/ai/knowledge.js',import.meta.url),'utf8'),block=api.slice(api.indexOf('// 预览只读'))
  assert.match(block,/data: \{planId, confirmed: true\}/);assert.match(block,/repeatSubmit: false/);assert.doesNotMatch(block,/fileUrl|snapshotKey|tenantId|vectorCollection/)
  const page=readFileSync(new URL('../src/views/ai/knowledge.vue',import.meta.url),'utf8');assert.match(page,/<KnowledgeSnapshotCleanup :knowledge="currentKb" :can-manage="canTestKnowledge"/)
})
