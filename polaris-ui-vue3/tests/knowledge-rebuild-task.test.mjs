import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import * as sass from 'sass'

const source = readFileSync(new URL('../src/components/knowledge/KnowledgeRebuildTask.vue', import.meta.url), 'utf8')
const {descriptor} = parse(source), compiled = compileScript(descriptor, {id: 'rebuild-task'})
const script = descriptor.script.content.replace(/^import .*$/m, '').replace('export default {', 'const component = {')
function state(api = async () => ({data: null}), recovery = async () => ({data: null})) {
  let timers = 0, cleared = 0
  const component = vm.runInNewContext(`${script}\ncomponent`, {getLatestKnowledgeRebuildTask: api, recoverKnowledgeRebuildTask: recovery,
    newKnowledgeRebuildRequestKey: () => 'synthetic-request-key', setInterval: () => {timers++; return timers}, clearInterval: () => {cleared++}})
  const emitted = [], context = {...component.data(), knowledge: {id: 7}, canManage: true,
    $emit: (...args) => emitted.push(args), $modal: {confirm: async () => {}, msgSuccess() {}}}
  for (const [key, method] of Object.entries(component.methods)) context[key] = method.bind(context)
  for (const [key, get] of Object.entries(component.computed)) Object.defineProperty(context, key, {get: () => get.call(context)})
  return {component, context, emitted, timerCount: () => timers, clearedCount: () => cleared}
}
function deferred() {let resolve, reject; const promise = new Promise((a,b) => {resolve=a;reject=b});return {resolve,reject,promise}}

test('任务模板及深浅色变量可编译，状态中文且仅显示文档进度', () => {
  assert.deepEqual(compileTemplate({source: descriptor.template.content, filename: 'KnowledgeRebuildTask.vue',id:'rebuild-task',compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
  for (const style of descriptor.styles) assert.ok(sass.compileString(style.content).css.length)
  for (const text of ['等待执行','构建中','构建成功','构建失败','来源已变化','任务已中断','未进入队列']) assert.ok(source.includes(text))
  assert.doesNotMatch(source,/v-html|ownerToken|document_ids_json|tenantId/)
  assert.match(source,/不是续跑/);assert.match(source,/不能撤回/);assert.match(source,/再次产生/)
})

test('悬停和键盘焦点不依赖可能为白色的全局 primary，深浅色文字对比合格', () => {
  assert.match(source,/color: var\(--rebuild-accent\) !important/)
  assert.match(source,/background: var\(--rebuild-soft\) !important/)
  assert.doesNotMatch(source,/color: var\(--el-color-primary\)/)
  assert.match(source,/html\.dark \.knowledge-rebuild-task/)
  function lum(hex) {const n=parseInt(hex.slice(1),16);return [n>>16,(n>>8)&255,n&255].map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((s,v,i)=>s+v*[.2126,.7152,.0722][i],0)}
  for(const [fg,bg]of [['#4338ca','#eef2ff'],['#93c5fd','#172554'],['#78350f','#fef3c7'],['#78350f','#fde68a'],['#166534','#f0fdf4']]){const a=lum(fg),b=lum(bg);assert.ok((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5)}
})

test('无管理员权限或没有父库时不读取任务', async () => {
  let calls=0;const {context}=state(async()=>{calls++});context.canManage=false;await context.reload();context.canManage=true;context.knowledge={};await context.reload();assert.equal(calls,0)
})

test('上传任务使用导入状态文案，仍保留手动和启动构建状态语义', () => {
  const {context}=state();context.task={taskType:'IMPORT',state:'RUNNING'};assert.equal(context.stateText,'导入处理中');assert.equal(context.taskKind,'上传导入')
  context.task.state='FAILED';assert.equal(context.stateText,'导入失败');assert.match(context.hint,/不会自动重试模型/);assert.match(context.hint,/不影响已有成功文档/)
  context.task.state='SUCCEEDED';assert.equal(context.stateText,'导入处理完成');assert.match(context.hint,/隔离/)
  context.task.taskType='REBUILD';context.task.state='FAILED';assert.equal(context.stateText,'构建失败');assert.equal(context.taskKind,'手动重建')
  context.task.taskType='STARTUP';assert.equal(context.stateText,'构建失败');assert.equal(context.taskKind,'启动重建')
})

test('任务查询只读；读到活动任务才轮询且同一时刻只允许一个请求', async () => {
  const pending=deferred();let calls=0;const {context,timerCount}=state(()=>{calls++;return pending.promise})
  const loading=context.reload();await context.reload();assert.equal(calls,1)
  pending.resolve({data:{taskId:'task',state:'RUNNING'}});await loading;assert.equal(context.active,true);assert.equal(timerCount(),1)
  context.accept({taskId:'task',state:'RUNNING'});assert.equal(timerCount(),1)
})

test('任务终态停止轮询并通知父页刷新，不伪造知识库 BUILDING', () => {
  const {context,emitted,clearedCount}=state();context.accept({taskId:'task',state:'RUNNING'});context.accept({taskId:'task',state:'SUCCEEDED'})
  assert.equal(context.active,false);assert.equal(clearedCount(),1);assert.ok(emitted.some(e=>e[0]==='settled'))
  const page=readFileSync(new URL('../src/views/ai/knowledge.vue',import.meta.url),'utf8');assert.doesNotMatch(page,/this\.currentKb\.indexStatus = "BUILDING"/)
})

test('换库后的旧任务查询响应不覆盖新库', async () => {
  const pending=deferred();const {context}=state(()=>pending.promise);const loading=context.reload();context.knowledge={id:8};context.reset()
  pending.resolve({data:{taskId:'old',state:'RUNNING'}});await loading;assert.equal(context.task,null);assert.equal(context.timer,null)
})

test('查询失败停止轮询，不重建、不恢复、不伪装成功', async () => {
  let recoveries=0;const {context}=state(async()=>{throw new Error('unavailable')},async()=>{recoveries++});context.accept({state:'RUNNING'});await context.reload()
  assert.equal(context.loadFailed,true);assert.equal(context.timer,null);assert.equal(context.task.state,'RUNNING');assert.equal(recoveries,0)
})

test('恢复必须在费用确认后派发，网络失败保留相同幂等键', async () => {
  let calls=0, notice;const keys=[];const pending=deferred()
  const {context}=state(undefined,async(id,task,key)=>{calls++;keys.push(key);throw new Error('ambiguous')})
  context.task={taskId:'old',state:'INTERRUPTED',canRecover:true};context.$modal.confirm=text=>{notice=text;return pending.promise}
  const running=context.recover();assert.equal(calls,0);assert.match(notice,/费用/);pending.resolve();await running
  context.$modal.confirm=async()=>{};await context.recover();assert.equal(calls,2);assert.equal(keys[0],keys[1]);assert.equal(context.task.taskId,'old')
})

test('恢复确认期间切库，不向原库或新库误派发', async () => {
  const pending=deferred();let calls=0;const {context}=state(undefined,async()=>{calls++})
  context.task={taskId:'old',state:'INTERRUPTED',canRecover:true};context.$modal.confirm=()=>pending.promise
  const running=context.recover();context.knowledge={id:8};context.reset();pending.resolve();await running;assert.equal(calls,0)
})

test('同库重建 API 提交幂等键且不依赖防重复提交缓存', () => {
  const api=readFileSync(new URL('../src/api/ai/knowledge.js',import.meta.url),'utf8')
  assert.match(api,/rebuildKnowledge\(id, requestKey/);assert.match(api,/'Idempotency-Key': requestKey, repeatSubmit: false/)
})

test('缺少 randomUUID 时生成有效的非凭证幂等键，不阻断提交', () => {
  const api=readFileSync(new URL('../src/api/ai/knowledge.js',import.meta.url),'utf8')
  const source=api.match(/export function newKnowledgeRebuildRequestKey\(\) \{([\s\S]*?)\n\}/)[0].replace('export ','')
  const fallback=vm.runInNewContext(`${source};newKnowledgeRebuildRequestKey()`,{crypto:{}})
  assert.match(fallback,/^[A-Za-z0-9_-]{8,64}$/)
  const native=vm.runInNewContext(`${source};newKnowledgeRebuildRequestKey()`,{crypto:{randomUUID:()=> 'native-key'}})
  assert.equal(native,'native-key')
})

test('已失效或失败的库不因残留解析状态永久轮询', () => {
  const page=readFileSync(new URL('../src/views/ai/knowledge.vue',import.meta.url),'utf8'), {descriptor}=parse(page)
  const compiled=compileScript(descriptor,{id:'page'}), object=compiled.scriptAst.find(n=>n.type==='ExportDefaultDeclaration').declaration
  const node=object.properties.find(n=>n.key.name==='methods').value.properties.find(n=>n.key.name==='shouldPollStatus')
  const method=vm.runInNewContext(`({${descriptor.script.content.slice(node.start,node.end)}}).shouldPollStatus`)
  const context={currentKb:{indexStatus:'FAILED'},docList:[{status:'1'}],rebuildTaskActive:false};assert.equal(method.call(context),false)
  context.currentKb.indexStatus='STALE';assert.equal(method.call(context),false);context.rebuildTaskActive=true;assert.equal(method.call(context),true)
})
