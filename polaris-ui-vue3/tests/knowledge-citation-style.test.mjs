import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import {compileStyle, parse} from '@vue/compiler-sfc'

const source=readFileSync(new URL('../src/components/knowledge/ChatKnowledgeSources.vue',import.meta.url),'utf8')
const {descriptor}=parse(source)
const style=descriptor.styles.at(-1).content
const selector='html .knowledge-sources .knowledge-source-open.el-button.is-link'
const block=selector=>style.slice(style.indexOf(selector)).match(/^[^{]+\{([^}]+)\}/s)?.[1]??''
const colors=selector=>Object.fromEntries([...block(selector).matchAll(/(?:^|[;\s])(color|background):\s*(#[\da-f]{6})/g)].map(m=>[m[1],m[2]]))
const luminance=color=>color.slice(1).match(/../g).map(v=>parseInt(v,16)/255)
  .map(v=>v<=0.04045?v/12.92:((v+0.055)/1.055)**2.4)
  .reduce((sum,v,i)=>sum+v*[0.2126,0.7152,0.0722][i],0)
const contrast=(a,b)=>{const l=[luminance(a),luminance(b)].sort((a,b)=>b-a);return (l[0]+0.05)/(l[1]+0.05)}

test('引用查看按钮使用限定样式，避免透明链接继承 primary 白字',()=>{
  assert.match(descriptor.template.content,/link type="primary" class="knowledge-source-open"/)
  assert.deepEqual(compileStyle({source:style,filename:'ChatKnowledgeSources.vue',id:'citation-style'}).errors,[])
  assert.match(block(selector),/color: #3730a3 !important/)
  assert.match(block(selector+' > span'),/color: inherit !important/)
})

test('默认、悬停和禁用文字在浅深色实心背景上均满足 4.5:1 对比度',()=>{
  for(const prefix of ['html ','html.dark ']){
    const base=prefix+selector.slice(5),normal=colors(base),hover=colors(base+':not(.is-disabled)'),disabled=colors(base+':is(.is-disabled')
    assert.ok(contrast(normal.color,normal.background)>=4.5)
    assert.ok(contrast(hover.color,hover.background)>=4.5)
    assert.ok(contrast(disabled.color,normal.background)>=4.5)
  }
})

test('交互颜色不覆盖禁用状态，键盘焦点可见且按钮间距独立',()=>{
  assert.ok(style.includes(':not(.is-disabled):not(:disabled):is(:hover, :active, :focus-visible)'))
  assert.match(block(selector+':focus-visible'),/outline: 2px solid/)
  assert.match(block(selector+':is(.is-disabled'),/cursor: not-allowed/)
  assert.match(descriptor.styles[0].content,/@media \(max-width: 600px\)/)
  assert.match(descriptor.styles[0].content,/flex-wrap: wrap/)
})
