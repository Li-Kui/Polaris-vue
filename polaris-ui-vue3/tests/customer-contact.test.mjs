import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import {readFile} from 'node:fs/promises'
import {compileScript, parse} from '@vue/compiler-sfc'
import {transformWithEsbuild} from 'vite'
import * as Vue from 'vue'
import {renderToString} from '@vue/server-renderer'
import * as contactUtils from '../src/utils/customerContactState.js'

async function component(name, inlineTemplate = false) {
  const source = await readFile(new URL(`../src/views/platform/customerService/${name}.vue`, import.meta.url),'utf8')
  const compiled = await transformWithEsbuild(compileScript(parse(source).descriptor,{ id: name, inlineTemplate }).content,`${name}.js`,{ format:'cjs',target:'esnext' })
  const module = { exports: {} }, modules = { vue: Vue, '@/utils/customerContactState': contactUtils, './CustomerContactCard.vue': {} }
  vm.runInNewContext(compiled.code,{ module,exports:module.exports,require:name => { assert.ok(name in modules,name); return modules[name] } })
  return module.exports.default
}
const Card = await component('CustomerContactCard',true), Editor = await component('CustomerContactEditor')
test('人工入口拒绝执行协议、相对地址、明文HTTP、凭据、控制字符和电话指令', () => {
  for (const webUrl of ['javascript:alert(1)','data:text/html,x','//example.invalid','http://example.invalid','https:example.invalid','https://secret:token@example.invalid','https://@example.invalid','https://example.invalid:0','https://example.invalid\\@evil.invalid','https://example.invalid/\nnext']) assert.throws(() => contactUtils.customerContactSettings({ name:'客服',webUrl }))
  for (const phone of ['123','tel:1234','1234;ext=999','1234,9999','+1234+9999','1'.repeat(21)]) assert.throws(() => contactUtils.customerContactSettings({name:'客服',phone}))
  assert.throws(() => contactUtils.customerContactSettings({name:'客服'}))
  assert.equal(contactUtils.customerContactSettings(null),null)
  assert.equal(contactUtils.customerContactPhoneLink('+86 (400) 000-0000'),'tel:+864000000000')
})
test('真实联系卡仅生成用户主动点击的HTTPS与电话链接，说明以文本显示，预览不生成链接', async () => {
  const config = { name:'人工客服',webUrl:'https://example.invalid/support',phone:'+86 (400) 000-0000',serviceHours:'工作日',note:'<script>test()</script>\n第二行' }
  const html = await renderToString(Vue.createSSRApp(Card,{config}))
  assert.match(html,/href="https:\/\/example.invalid\/support"/); assert.match(html,/href="tel:\+864000000000"/)
  assert.match(html,/rel="noopener noreferrer"/); assert.match(html,/&lt;script&gt;/); assert.ok(!html.includes('<script>'))
  assert.match(html,/尚未接通人工坐席/)
  const preview = await renderToString(Vue.createSSRApp(Card,{config,preview:true}))
  assert.ok(!preview.includes('href=')); assert.match(preview,/保存并发布后对新会话生效/)
})
test('异常配置不渲染危险链接，未填写可选项保持未配置，说明换行规范化', async () => {
  const html = await renderToString(Vue.createSSRApp(Card,{config:{name:'客服',webUrl:'javascript:alert(1)'}}))
  assert.ok(!html.includes('href=')); assert.match(html,/暂不可用/)
  const value = contactUtils.customerContactSettings({name:' 客服 ',phone:'400 000 0000',note:'第一行\r\n第二行'})
  assert.equal(value.name,'客服'); assert.equal(value.webUrl,null); assert.equal(value.serviceHours,null); assert.equal(value.note,'第一行\n第二行')
})
test('编辑联系人使用独立草稿，停用仅输出null，取消编辑不会污染原配置', async () => {
  const original = {name:'人工客服',webUrl:'https://example.invalid/support'}, updates = [], scope = Vue.effectScope()
  const state = scope.run(() => Editor.setup({modelValue:original},{emit:(name,value)=>updates.push({name,value}),expose(){}}))
  state.draft.webUrl = 'https://example.invalid/other'; await Vue.nextTick()
  assert.equal(original.webUrl,'https://example.invalid/support'); assert.equal(updates.at(-1).value.webUrl,'https://example.invalid/other')
  state.enabled.value = false; await Vue.nextTick(); assert.equal(updates.at(-1).value,null)
  scope.stop()
})
