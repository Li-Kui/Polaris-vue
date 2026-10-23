import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import * as sass from 'sass'

const source=readFileSync(new URL('../src/components/knowledge/KnowledgeSnapshotCleanupStatus.vue',import.meta.url),'utf8')
const {descriptor}=parse(source),compiled=compileScript(descriptor,{id:'snapshot-status'})
const script=descriptor.script.content.replace(/^import .*$/gm,'').replace('export default {','const component = {')
const page=()=>({items:[{snapshotId:19,documentId:38,indexVersion:1,snapshotState:'RETIRED',cleanupState:'PENDING',attempts:0,nextAttemptAt:'2026-10-15 08:45:16'}],hasMore:true,nextBeforeId:19})
function state(fetch=async()=>({data:page()})) {
  const calls=[],component=vm.runInNewContext(`${script}\ncomponent`,{getKnowledgeSnapshotCleanupStatus:(...args)=>{calls.push(args);return fetch(...args)}})
  const context={...component.data(),knowledge:{id:10},canManage:true};for(const [name,method] of Object.entries(component.methods))context[name]=method.bind(context)
  return {context,component,calls}
}
test('只读状态组件编译，无自动请求或危险操作，活动保护和实际时间可见',()=>{
  assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:'KnowledgeSnapshotCleanupStatus.vue',id:'snapshot-status',compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
  for(const style of descriptor.styles)assert.ok(sass.compileString(style.content).css.length)
  assert.doesNotMatch(source,/mounted\(|setInterval|setTimeout|v-html|executeKnowledge|cleanupPayload|snapshotKey|tenantId/)
  assert.match(source,/仍属于当前活动索引，禁止清理/);assert.match(source,/不代表已扫描或清除了全部文件/);assert.equal(state().calls.length,0)
})
test('角色、父库和游标校验，显式翻页只读取给定范围',async()=>{
  const {context,calls}=state();context.canManage=false;await context.load();context.canManage=true;context.knowledge={};await context.load();context.knowledge={id:10};for(const cursor of [0,-1,'19',NaN])await context.load(cursor);assert.equal(calls.length,0)
  await context.load();await context.load(context.report.nextBeforeId);assert.deepEqual(calls,[[10,undefined],[10,19]])
})
test('失败清除旧数据且不伪装为零记录，不自动重试',async()=>{
  const {context,calls}=state(async()=>{throw new Error('private error')});context.report=page();await context.load();assert.equal(context.report,null);assert.equal(calls.length,1);assert.match(context.error,/没有执行删除或重试/);assert.doesNotMatch(context.error,/private/)
})
test('切库和卸载清空状态，迟到响应不能显示到另一知识库',async()=>{
  let resolve;const {context,component}=state(()=>new Promise(done=>{resolve=done}));const pending=context.load();context.knowledge={id:9};context.reset();resolve({data:page()});await pending;assert.equal(context.report,null);assert.equal(context.loading,false)
  context.report=page();component.beforeUnmount.call(context);assert.equal(context.report,null)
})
test('固定中文状态及错误兜底，不显示远端内容，日期明确为北京时间',()=>{
  const {context}=state();assert.equal(context.stateText('RETRY_WAIT'),'等待显式重试');assert.equal(context.stateText('SUCCEEDED'),'载荷已清理');assert.match(context.stateText('arbitrary'),/状态未知/);assert.doesNotMatch(context.errorText('<unsafe>'),/unsafe/)
  assert.equal(context.time('2026-10-15 08:45:16'),'2026-10-15 08:45:16（北京时间）')
})
test('API新增独立GET状态接口，清理面板接入且保留原计划与执行契约',()=>{
  const api=readFileSync(new URL('../src/api/ai/knowledge.js',import.meta.url),'utf8');const body=api.slice(api.indexOf('export function getKnowledgeSnapshotCleanupStatus'),api.indexOf('export function executeKnowledgeSnapshotCleanup'))
  assert.match(body,/snapshot-cleanup\/status/);assert.match(body,/method: 'get'/);assert.match(body,/\{beforeId\}/);assert.doesNotMatch(body,/confirmed|fileUrl|tenantId|snapshotKey/)
  const parent=readFileSync(new URL('../src/components/knowledge/KnowledgeSnapshotCleanup.vue',import.meta.url),'utf8');assert.match(parent,/<KnowledgeSnapshotCleanupStatus :knowledge="knowledge" :can-manage="canManage"/)
})
