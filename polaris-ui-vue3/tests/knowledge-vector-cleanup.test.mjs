import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import * as sass from 'sass'

const source = readFileSync(new URL('../src/components/knowledge/KnowledgeVectorCleanup.vue', import.meta.url), 'utf8')
const {descriptor} = parse(source), compiled = compileScript(descriptor, {id: 'vector-cleanup'})
const script = descriptor.script.content.replace(/^import .*$/gm, '').replace('export default {', 'const component = {')
const row = () => ({indexVersion: 1, buildGeneration: 2, state: 'RETIRED', reason: 'LOCAL_CANDIDATE'})
const report = () => ({items: [row()], localCandidateCount: 1, batchLimit: 20, blocked: false, truncated: false})
function state(preview = async () => ({data: report()}), verify = async (id, generation) => ({data: {indexVersion: 1, buildGeneration: generation, state: 'MATCHED'}})) {
  let previews = 0, verifications = 0
  const component = vm.runInNewContext(`${script}\ncomponent`, {KnowledgeVectorCleanupExecution: {}, previewKnowledgeVectorCleanup: (...args) => {previews++;return preview(...args)},
    verifyKnowledgeVectorOwnership: (...args) => {verifications++;return verify(...args)}})
  const context = {...component.data(), knowledge: {id: 10}, canManage: true}
  for (const [key, method] of Object.entries(component.methods)) context[key] = method.bind(context)
  return {component, context, previews: () => previews, verifications: () => verifications}
}
function deferred() {let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {resolve,reject,promise}}

test('集合治理组件可编译，仅显式只读预览，不生成删除计划或自动核验', () => {
  assert.deepEqual(compileTemplate({source: descriptor.template.content,filename:'KnowledgeVectorCleanup.vue',id:'vector-cleanup',compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
  for(const style of descriptor.styles)assert.ok(sass.compileString(style.content).css.length)
  for(const text of ['24 小时','只读预览不会执行删除','不是删除计划或授权','不调用模型','不扫描或认领历史集合','结果已截断','仅代表该时刻'])assert.ok(source.includes(text))
  assert.doesNotMatch(source,/v-html|setInterval|setTimeout|mounted\(|immediate: true|planId|confirmed|executeKnowledge|tenantId|ownershipMarker|collectionName/)
  const {previews,verifications}=state();assert.equal(previews(),0);assert.equal(verifications(),0)
})
test('深浅色悬停焦点禁用文字可读，不依赖全局白色 primary', () => {
  assert.match(source,/html\.dark \.knowledge-vector-cleanup/);assert.match(source,/focus-visible/);assert.match(source,/color: inherit !important/)
  assert.doesNotMatch(source,/color: var\(--el-color-primary\)/)
  function lum(hex){const n=parseInt(hex.slice(1),16);return [n>>16,(n>>8)&255,n&255].map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((s,v,i)=>s+v*[.2126,.7152,.0722][i],0)}
  for(const [fg,bg]of [['#4338ca','#eef2ff'],['#93c5fd','#172554'],['#475569','#f1f5f9'],['#94a3b8','#1e293b']]){const a=lum(fg),b=lum(bg);assert.ok((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5)}
})
test('集合保留期按服务端实际配置显示，分钟测试窗口不再误显示为 24 小时',()=>{
  const {context}=state();assert.equal(context.retentionText(24),'24 小时');assert.equal(context.retentionText(5/60),'5 分钟');assert.equal(context.retentionText(1.5),'90 分钟')
  for(const value of [undefined,null,NaN,0,-1])assert.equal(context.retentionText(value),'以各登记的最早保留时间为准')
  assert.match(source,/retentionText\(report.retentionHours\)/);assert.match(source,/已缩短保留期，用于测试验收/)
})
test('按实际面板宽度切换表格和卡片，窄侧栏也直接展示保护原因及核验状态',()=>{
  assert.match(source,/container-type: inline-size/);assert.match(source,/@container \(max-width: 1000px\)/)
  assert.match(source,/\.vector-preview-table \{display: none\}/);assert.match(source,/\.vector-preview-cards \{display: block\}/)
  assert.match(source,/aria-label="集合清理检查结果"/);assert.match(source,/<dt>本地检查<\/dt><dd>\{\{ reasonText\(row.reason\) \}\}<\/dd>/)
  assert.match(source,/v-else class="vector-preview-cards" role="status"/);assert.doesNotMatch(source,/window\.innerWidth|resize|setInterval/)
})
test('无管理员或父库时不发起预览和核验',async()=>{
  const {context,previews,verifications}=state();context.report=report();context.canManage=false;await context.preview();await context.verify(context.report.items[0]);context.canManage=true;context.knowledge={};await context.preview();await context.verify(context.report.items[0]);assert.equal(previews(),0);assert.equal(verifications(),0)
})
test('预览不自动核验，本地候选不转化为删除授权',async()=>{
  const {context,previews,verifications}=state();await context.preview();assert.equal(previews(),1);assert.equal(verifications(),0);assert.equal(context.report.localCandidateCount,1)
  assert.match(context.reasonText('LOCAL_CANDIDATE'),/仍不可据此删除/);await context.verify(context.report.items[0]);assert.equal(verifications(),1);assert.match(context.verificationText(context.checks[2]),/非删除授权/)
})
test('未知／写入中、历史、无创建证据或路由异常行不能核验，禁止传入未展示行',async()=>{
  const {context,verifications}=state();context.report=report();await context.verify(row());assert.equal(verifications(),0)
  for(const status of ['UNKNOWN','RESERVED','CREATING','WRITING'])assert.equal(context.canVerify({...row(),state:status}),false)
  for(const reason of ['LEGACY_UNVERIFIED','CREATE_UNCONFIRMED','BACKEND_UNSUPPORTED','ROUTE_CHANGED','DEFINITION_INVALID'])assert.equal(context.canVerify({...row(),reason}),false)
  assert.equal(context.canVerify({...row(),buildGeneration:0}),false);assert.equal(context.canVerify({...row(),buildGeneration:Number.MAX_SAFE_INTEGER+1}),false)
})
test('双击和预览核验互斥，不同时派发第二次请求',async()=>{
  const wait=deferred(),first=state(()=>wait.promise);const loading=first.context.preview();await first.context.preview();wait.resolve({data:report()});await loading;assert.equal(first.previews(),1)
  const remote=deferred(),second=state(undefined,()=>remote.promise);second.context.report=report();const checking=second.context.verify(second.context.report.items[0]);await second.context.verify(second.context.report.items[0]);await second.context.preview();assert.equal(second.verifications(),1);assert.equal(second.previews(),0);remote.resolve({data:{indexVersion:1,buildGeneration:2,state:'MATCHED'}});await checking
})
test('切库、索引变化、权限变化及卸载会丢弃预览和核验状态',async()=>{
  const {context,component}=state();await context.preview();await context.verify(context.report.items[0]);context.reset();assert.equal(context.report,null);assert.equal(Object.keys(context.checks).length,0)
  for(const field of ['knowledge.id','knowledge.indexVersion','knowledge.vectorCollection','knowledge.indexStatus','canManage'])assert.equal(typeof component.watch[field],'function')
  assert.equal(typeof component.beforeUnmount,'function')
})
test('换库后的慢预览和远端核验响应不覆盖新库',async()=>{
  const wait=deferred(),{context}=state(()=>wait.promise);const loading=context.preview();context.knowledge={id:11};context.reset();wait.resolve({data:report()});await loading;assert.equal(context.report,null)
  const remote=deferred(),other=state(undefined,()=>remote.promise).context;other.report=report();const checking=other.verify(other.report.items[0]);other.knowledge={id:11};other.reset();remote.resolve({data:{indexVersion:1,buildGeneration:2,state:'MATCHED'}});await checking;assert.equal(Object.keys(other.checks).length,0);assert.equal(other.checking,null)
})
test('预览失败清空旧状态，不冒充零候选，也不自动重试',async()=>{
  const {context,previews}=state(async()=>{throw new Error('unavailable')});context.report=report();context.checks[2]='MATCHED';await context.preview();assert.equal(context.report,null);assert.equal(Object.keys(context.checks).length,0);assert.match(context.error,/没有执行删除/);assert.equal(previews(),1)
})
test('核验失败丢弃先前匹配结果，服务端范围变化要求重新预览',async()=>{
  const {context,verifications}=state(undefined,async()=>{throw new Error('429')});context.report=report();context.checks[2]='MATCHED';await context.verify(context.report.items[0]);assert.equal(context.checks[2],'REQUEST_FAILED');assert.equal(verifications(),1)
  for(const data of [{indexVersion:2,buildGeneration:2,state:'MATCHED'},{indexVersion:1,buildGeneration:3,state:'MATCHED'},{indexVersion:1,buildGeneration:2,state:'CHANGED'}]){
    const other=state(undefined,async()=>({data})).context;other.report=report();await other.verify(other.report.items[0]);assert.equal(other.report,null);assert.match(other.error,/重新预览/)
  }
})
test('状态和原因中文映射，未知返回不直接输出英文／服务端错误正文',()=>{
  const {context}=state();assert.equal(context.stateText('UNKNOWN'),'远端结果待确认');assert.match(context.reasonText('ACTIVE_REFERENCE'),/软删除/)
  assert.equal(context.stateText('<unsafe>'),'状态未知');assert.match(context.reasonText('<unsafe>'),/未知/);assert.equal(context.verificationText('<unsafe>'),'核验状态未知');assert.equal(context.time('invalid'),'时间未知')
})
test('只读 API 不接受客户端路由／标记／租户／删除参数，组件接入管理员区域',()=>{
  const api=readFileSync(new URL('../src/api/ai/knowledge.js',import.meta.url),'utf8'),block=api.slice(api.indexOf('// 集合预览'),api.indexOf('// 预览只读'))
  assert.match(block,/cleanup-preview`, method: 'get'/);assert.match(block,/verify-ownership`, method: 'post'/);assert.match(block,/repeatSubmit: false/)
  assert.doesNotMatch(block,/data:|params:|tenantId|ownershipMarker|collectionName|execute|delete/)
  const page=readFileSync(new URL('../src/views/ai/knowledge.vue',import.meta.url),'utf8');assert.match(page,/<KnowledgeVectorCleanup :knowledge="currentKb" :can-manage="canTestKnowledge"/)
})
