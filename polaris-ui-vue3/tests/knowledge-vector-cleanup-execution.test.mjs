import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import * as sass from 'sass'

const source=readFileSync(new URL('../src/components/knowledge/KnowledgeVectorCleanupExecution.vue',import.meta.url),'utf8')
const {descriptor}=parse(source),compiled=compileScript(descriptor,{id:'vector-execution'})
const script=descriptor.script.content.replace(/^import .*$/gm,'').replace('export default {','const component = {')
const candidate=()=>({indexVersion:1,buildGeneration:2,state:'RETIRED',reason:'LOCAL_CANDIDATE'})
const plan=()=>({indexVersion:1,buildGeneration:2,planId:'synthetic-plan',expiresAt:new Date(Date.now()+300000).toISOString(),blocked:false})
const result=(state='SUCCEEDED')=>({indexVersion:1,buildGeneration:2,state,attempts:1,errorCode:null,leaseExpired:false})
function state(overrides={}){
  const clock={now:Date.now()},timers=new Map();let timerId=0
  class ClockDate extends Date {static now(){return clock.now}}
  const calls={plan:0,status:0,execute:0,stop:0},impl={plan:async()=>({data:plan()}),status:async()=>({data:result('NONE')}),execute:async()=>({data:result()}),stop:async()=>({data:result('MANUAL_REQUIRED')}),...overrides}
  const component=vm.runInNewContext(`${script}\ncomponent`,{
    Date:ClockDate,setTimeout:fn=>{timers.set(++timerId,fn);return timerId},clearTimeout:id=>timers.delete(id),
    planKnowledgeVectorCleanup:(...args)=>{calls.plan++;return impl.plan(...args)},getKnowledgeVectorCleanup:(...args)=>{calls.status++;return impl.status(...args)},
    executeKnowledgeVectorCleanup:(...args)=>{calls.execute++;return impl.execute(...args)},stopExpiredKnowledgeVectorCleanup:(...args)=>{calls.stop++;return impl.stop(...args)}})
  const context={...component.data(),knowledge:{id:10},canManage:true,items:[candidate()],$modal:{confirm:async()=>{}}}
  for(const [name,fn]of Object.entries(component.methods))context[name]=fn.bind(context)
  Object.defineProperty(context,'selected',{get:()=>component.computed.selected.call(context)});context.generation=2
  return {context,component,calls,clock,timers}
}
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {resolve,reject,promise}}

test('受控清理模板样式可编译，明确不可恢复及外部替换风险，不自动请求',()=>{
  assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:'KnowledgeVectorCleanupExecution.vue',id:'vector-execution',compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
  for(const style of descriptor.styles)assert.ok(sass.compileString(style.content).css.length)
  for(const text of ['删除不可恢复','无法阻止外部管理员','独占运维窗口','不会自动重试','不能撤回','最多 5 次'])assert.ok(source.includes(text))
  assert.doesNotMatch(source,/v-html|setInterval|mounted\(|immediate: true|ownershipMarker|collectionName|tenantId/)
  assert.deepEqual(state().calls,{plan:0,status:0,execute:0,stop:0})
})
test('计划和状态读取不删除；受保护行不能生成执行计划',async()=>{
  const {context,calls}=state();await context.refresh();assert.equal(context.result.state,'NONE');await context.prepare();assert.equal(context.controlled,false);assert.equal(calls.execute,0)
  context.items[0].reason='ACTIVE_REFERENCE';await context.prepare();assert.equal(calls.plan,1)
})
test('无权限、未选登记、非安全整数代次时不能请求',async()=>{
  const {context,calls}=state();context.canManage=false;await context.prepare();await context.refresh();await context.execute();await context.stop();context.canManage=true;context.generation=null;await context.prepare();await context.refresh();context.generation=Number.MAX_SAFE_INTEGER+1;await context.refresh();assert.deepEqual(calls,{plan:0,status:0,execute:0,stop:0})
})
test('无计划、计划被阻止或未确认受控窗口时不能执行',async()=>{
  const {context,calls}=state();context.controlled=true;await context.execute();await context.prepare();await context.execute();context.controlled=true;context.plan.blocked=true;await context.execute();assert.equal(calls.execute,0)
})
test('两阶段确认等待不可恢复弹窗，双击不产生第二次请求',async()=>{
  const wait=deferred(),{context,calls}=state();await context.prepare();context.controlled=true;let notice;context.$modal.confirm=text=>{notice=text;return wait.promise}
  const pending=context.execute();await context.execute();assert.equal(calls.execute,0);assert.match(notice,/不可恢复/);assert.match(notice,/独占运维窗口/)
  wait.resolve();await pending;assert.equal(calls.execute,1);assert.equal(context.result.state,'SUCCEEDED');assert.equal(context.plan,null);assert.equal(context.controlled,false)
})
test('取消确认不删除，撤回受控勾选或切库也不删除',async()=>{
  const first=state();await first.context.prepare();first.context.controlled=true;first.context.$modal.confirm=async()=>{throw 'cancel'};await first.context.execute();assert.equal(first.calls.execute,0);assert.ok(first.context.plan)
  for(const change of [ctx=>{ctx.controlled=false},ctx=>{ctx.knowledge={id:11};ctx.reset(true)}]){
    const wait=deferred(),{context,calls}=state();await context.prepare();context.controlled=true;context.$modal.confirm=()=>wait.promise;const pending=context.execute();change(context);wait.resolve();await pending;assert.equal(calls.execute,0)
  }
})
test('执行结果不明清空计划和确认，不自动读取或再次删除',async()=>{
  const {context,calls}=state({execute:async()=>{throw new Error('timeout')}});await context.prepare();context.controlled=true;await context.execute();await context.execute()
  assert.equal(calls.execute,1);assert.equal(calls.status,0);assert.equal(context.plan,null);assert.equal(context.controlled,false);assert.match(context.error,/刷新清理状态/)
})
test('刷新状态会作废旧计划；失败不能继续显示此前成功结果',async()=>{
  const {context,calls}=state({status:async()=>{throw new Error('unavailable')}});await context.prepare();context.controlled=true;context.result=result();await context.refresh()
  assert.equal(context.plan,null);assert.equal(context.controlled,false);assert.equal(context.result,null);assert.match(context.error,/不会重新派发/);assert.equal(calls.execute,0)
})
test('范围变化不采用另一版本或代次的计划和状态',async()=>{
  const first=state({plan:async()=>({data:{...plan(),buildGeneration:3}})});await first.context.prepare();assert.equal(first.context.plan,null);assert.match(first.context.error,/范围已变化/)
  const second=state({status:async()=>({data:{...result(),indexVersion:2}})});await second.context.refresh();assert.equal(second.context.result,null)
})
test('迟到计划、状态、执行响应不能覆盖新库',async()=>{
  for(const method of ['prepare','refresh','execute']){
    const wait=deferred(),overrides={[{prepare:'plan',refresh:'status',execute:'execute'}[method]]:()=>wait.promise},current=state(overrides).context
    if(method==='execute'){await current.prepare();current.controlled=true}
    const pending=current[method]();await Promise.resolve();current.knowledge={id:11};current.reset(true);wait.resolve({data:method==='prepare'?plan():result()});await pending
    assert.equal(current.plan,null);assert.equal(current.result,null);assert.equal(current.controlled,false)
  }
})
test('过期转人工只能用于过期 RUNNING，并明确不撤回远端请求',async()=>{
  const {context,calls}=state();context.result=result('RUNNING');await context.stop();assert.equal(calls.stop,0);context.result.leaseExpired=true;let notice;context.$modal.confirm=async text=>{notice=text};await context.stop()
  assert.equal(calls.stop,1);assert.equal(calls.execute,0);assert.equal(context.result.state,'MANUAL_REQUIRED');assert.match(notice,/不能撤回/);assert.match(notice,/不重新删除/)
})
test('转人工确认期间切库或取消不提交，结果不明只提示刷新',async()=>{
  const wait=deferred(),first=state();first.context.result={...result('RUNNING'),leaseExpired:true};first.context.$modal.confirm=()=>wait.promise;const pending=first.context.stop();first.context.reset(true);wait.resolve();await pending;assert.equal(first.calls.stop,0)
  const second=state({stop:async()=>{throw new Error('unknown')}});second.context.result={...result('RUNNING'),leaseExpired:true};await second.context.stop();assert.equal(second.context.result,null);assert.match(second.context.error,/手动刷新/)
})
test('中文状态兜底且切换身份、索引或登记列表后清空所有确认',()=>{
  const {context,component}=state();assert.equal(context.stateText('SUCCEEDED'),'已确认清理');assert.equal(context.stateText('<unsafe>'),'状态未知');assert.doesNotMatch(context.errorText('<unsafe>'),/unsafe/)
  for(const name of ['knowledge.id','knowledge.indexVersion','knowledge.vectorCollection','knowledge.indexStatus','canManage','items','generation'])assert.equal(typeof component.watch[name],'function')
  context.plan=plan();context.controlled=true;context.reset(true);assert.equal(context.generation,null);assert.equal(context.plan,null);assert.equal(context.controlled,false)
})
test('执行 API 严格白名单与超时，无自动恢复、客户端路径或归属输入',()=>{
  const api=readFileSync(new URL('../src/api/ai/knowledge.js',import.meta.url),'utf8'),block=api.slice(api.indexOf('// 单集合执行'),api.indexOf('// 集合预览'))
  assert.match(block,/data: \{planId, confirmed: true, controlledStorageConfirmed: true\}/);assert.match(block,/timeout: 45000/);assert.match(block,/stop-expired/)
  assert.doesNotMatch(block,/collectionName|ownershipMarker|tenantId|ownerToken|fileUrl/)
  assert.match(source,/background: #fde68a !important/);assert.match(source,/color: #78350f !important/);assert.match(source,/is-disabled/)
})
test('危险按钮覆盖外层中性样式，勾选标记在深浅色均保持可见',()=>{
  assert.match(source,/\.el-button\.el-button\.el-button\.el-button\.el-button--warning/)
  assert.match(source,/html\.dark \.vector-execution/);assert.match(source,/is-checked \.el-checkbox__inner::after/);assert.match(source,/is-focus/)
  function lum(hex){const n=parseInt(hex.slice(1),16);return [n>>16,(n>>8)&255,n&255].map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((s,v,i)=>s+v*[.2126,.7152,.0722][i],0)}
  for(const [fg,bg]of [['#78350f','#fef3c7'],['#78350f','#fde68a'],['#ffffff','#4338ca'],['#0f172a','#93c5fd']]){const a=lum(fg),b=lum(bg);assert.ok((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5)}
})
test('计划到期只清除本地确认并禁用执行，不产生任何后台请求',async()=>{
  const {context,calls,timers}=state();await context.prepare();context.controlled=true
  assert.equal(timers.size,1);const before={...calls};[...timers.values()][0]()
  assert.equal(context.planExpired,true);assert.equal(context.controlled,false);await context.execute();assert.deepEqual(calls,before)
  assert.match(source,/:disabled="busy \|\| planExpired \|\| !controlled"/)
})
test('浏览器挂起未触发定时器时，确认前与弹窗确认后均拦截过期计划',async()=>{
  const first=state();await first.context.prepare();first.context.controlled=true;first.clock.now+=301000
  let prompts=0;first.context.$modal.confirm=async()=>{prompts++};await first.context.execute();assert.equal(prompts,0);assert.equal(first.calls.execute,0);assert.equal(first.context.planExpired,true)
  const second=state(),wait=deferred();await second.context.prepare();second.context.controlled=true;second.context.$modal.confirm=()=>wait.promise
  const pending=second.context.execute();second.clock.now+=301000;wait.resolve();await pending
  assert.equal(second.calls.execute,0);assert.equal(second.context.planExpired,true);assert.equal(second.context.controlled,false);assert.equal(second.context.busy,false)
})
test('缺失、无法识别或已到期的期限不能授权执行；无偏移时间固定按北京时间解释',async()=>{
  for(const expiresAt of [undefined,null,'not-a-date','2026-10-14T20:05:24','2000-01-01 00:00:00']){
    const current=state({plan:async()=>({data:{...plan(),expiresAt}})});await current.context.prepare();current.context.controlled=true;await current.context.execute()
    assert.equal(current.calls.execute,0);assert.equal(current.context.planExpired,true);assert.equal(current.timers.size,0)
  }
  const {context}=state();context.plan={expiresAt:'2026-10-14 20:05:24'};assert.equal(context.deadline(),Date.parse('2026-10-14T12:05:24Z'));assert.match(context.deadlineText(),/20:05:24/)
})
test('重置、卸载、刷新或重新计划清除旧计时器，旧回调不会使新计划过期',async()=>{
  for(const action of ['reset','unmount','refresh','prepare']){
    const {context,component,timers}=state();await context.prepare();const old=[...timers.values()][0]
    if(action==='reset')context.reset(true)
    else if(action==='unmount')component.beforeUnmount.call(context)
    else if(action==='prepare')await context.prepare()
    else await context.refresh()
    if(action!=='prepare')assert.equal(timers.size,0)
    else assert.equal(timers.size,1)
    old();assert.equal(context.planExpired,false)
  }
})
