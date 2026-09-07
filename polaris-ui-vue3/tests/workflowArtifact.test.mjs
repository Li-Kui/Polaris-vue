import test from 'node:test'
import assert from 'node:assert/strict'
import {
    ARTIFACT_DEFAULT_CONFIG,
    artifactConfigurationState,
    artifactExtension,
    artifactFileNamePreview,
    artifactSummary,
    normalizeArtifactConfig
} from '../src/components/workflow/workflowArtifact.js'

test('保存产物配置使用安全、易懂的默认值', () => {
  assert.deepEqual(normalizeArtifactConfig(), ARTIFACT_DEFAULT_CONFIG)
  assert.equal(artifactExtension('JSON'), 'json')
  assert.equal(artifactExtension('TEXT'), 'txt')
})

test('文件名只使用系统变量并自动维护扩展名', () => {
  const date = new Date(2026, 8, 7)
  assert.equal(artifactFileNamePreview({
    format: 'JSON',
    fileNameTemplate: '客户/分析-{{date}}'
  }, date), '客户_分析-20260907.json')
  assert.equal(artifactFileNamePreview({
    format: 'TEXT',
    fileNameTemplate: '结果.txt'
  }, date), '结果.txt')
})

test('配置状态明确提示必须选择要保存的内容', () => {
  assert.deepEqual(artifactConfigurationState().issues, ['请选择要保存的内容'])
  const state = artifactConfigurationState({
    config: {format: 'TEXT', fileNameTemplate: '总结'},
    inputMapping: {content: {expression: '$.nodes.agent_1.output.text'}}
  })
  assert.equal(state.code, 'READY')
  assert.equal(state.formatLabel, '纯文本')
  assert.match(state.fileName, /总结\.txt$/)
  assert.match(artifactSummary({format: 'TEXT', fileNameTemplate: '总结'}), /^纯文本 · /)
})

test('保留时间只接受产品提供的安全选项', () => {
  assert.deepEqual(normalizeArtifactConfig({
    retentionMode: 'DAYS',
    retentionDays: 3650
  }), {
    configVersion: '2.0',
    format: 'JSON',
    fileNameTemplate: '工作流产物-{{date}}',
    retentionMode: 'DAYS',
    retentionDays: 30
  })
})
