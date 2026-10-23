import {invocationSourceLabel, isWorkflowInvocation} from '../src/components/agent/agentBindings.js'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {compileStyle, parse} from '@vue/compiler-sfc'
import {compileString} from 'sass'

const style = readFileSync(new URL('../src/assets/styles/platform-mcp.scss', import.meta.url), 'utf8')
const catalog = readFileSync(new URL('../src/components/platform/McpToolCatalog.vue', import.meta.url), 'utf8')
const invoker = readFileSync(new URL('../src/components/platform/McpToolInvoker.vue', import.meta.url), 'utf8')

test('MCP 主题样式可编译，文字按钮、禁用、悬停与键盘焦点分别覆盖', () => {
  const css = compileString(style).css
  assert.match(css, /\.action-btn-secondary:not\(\.is-disabled\)/)
  assert.match(css, /:hover, :focus-visible, :active/)
  assert.match(css, /\.el-button--primary:not\(\.is-link\):not\(\.is-text\):not\(\.is-disabled\)/)
  assert.match(css, /> span\s*\{\s*color: inherit !important/)
  assert.match(css, /html\.dark/)
  assert.match(css, /outline: 2px solid var\(--mcp-accent\)/)
  assert.match(css, /background: var\(--el-bg-color\) !important/)
  assert.match(readFileSync(new URL('../src/main.js', import.meta.url), 'utf8'), /import '@\/assets\/styles\/platform-mcp\.scss'/)
})

function luminance(hex) {
  const channels = hex.replace('#', '').match(/../g).map(value => {
    const channel = parseInt(value, 16) / 255
    return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
  })
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
}

test('启用按钮悬停配色在浅深色主题下均有足够文字对比度', () => {
  const themes = [...style.matchAll(/--mcp-accent-hover: (#[\da-f]{6});\s*--mcp-accent-soft: (#[\da-f]{6});/g)]
  assert.equal(themes.length, 2)
  for (const [foreground, background] of [
    ...themes.map(match => [match[1], match[2]]),
    ['#ffffff', '#4f46e5'], ['#ffffff', '#4338ca']
  ]) {
    const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
    const ratio = (values[0] + 0.05) / (values[1] + 0.05)
    assert.ok(ratio >= 4.5, `${foreground} / ${background}: ${ratio.toFixed(2)}`)
  }
})

test('目录与调用记录使用共享样式，组件局部样式编译通过', () => {
  for (const source of [catalog, invoker]) {
    assert.doesNotMatch(source, /:global\(html .*action-btn-secondary/)
    const { descriptor } = parse(source)
    for (const block of descriptor.styles) {
      const result = compileStyle({ source: compileString(block.content).css, filename: 'mcp.vue', id: 'data-v-mcp', scoped: true })
      assert.deepEqual(result.errors, [])
    }
  }
  assert.match(catalog, /class="catalog-row-actions"/)
})

test('调用结果与错误码优先展示，完整审计标识保留在展开详情中', () => {
  const { descriptor } = parse(invoker)
  const template = descriptor.template.content
  assert.match(template, /row-key="invocationId"/)
  assert.match(template, /type="expand"/)
  for (const field of ['invocationId', 'executionId', 'nodeRunId', 'attemptNo', 'callDispatched']) {
    assert.match(template, new RegExp(`scope\\.row\\.${field}`))
  }
  assert.ok(template.indexOf('label="状态"') < template.indexOf('label="开始时间"'))
  assert.ok(template.indexOf('label="错误码"') < template.indexOf('label="来源 / 主体"'))
  const script = descriptor.script.content.replace(/^import .*$/gm, '').replace('export default {', 'const component = {')
  const methods = vm.runInNewContext(`${script}\ncomponent.methods`, {isWorkflowInvocation, invocationSourceLabel})
  assert.equal(methods.statusType('SUCCESS'), 'success')
  assert.equal(methods.statusType('REJECTED'), 'danger')
  assert.equal(methods.statusType('TIMEOUT'), 'warning')
  assert.equal(methods.statusType('NEW_STATUS'), 'info')
  assert.equal(methods.statusLabel('TIMEOUT'), '超时／远端未知')
})
