import test from 'node:test'
import assert from 'node:assert/strict'
import {workflowDisplayLabel, workflowDisplayPath, workflowOptionLabel} from '../src/utils/workflowDisplayLabels.js'

test('分享页的常用技术字段和历史英文标题显示为中文', () => {
  assert.equal(workflowDisplayLabel('messages'), '对话消息')
  assert.equal(workflowDisplayLabel('messages', 'Messages'), '对话消息')
  assert.equal(workflowDisplayLabel('system_prompt'), '系统提示词')
  assert.equal(workflowDisplayLabel('structuredOutputSchema'), '结构化输出格式')
})

test('保留配置的业务标题、未知字段和业务枚举，避免错误改写', () => {
  assert.equal(workflowDisplayLabel('messages', '客户沟通记录'), '客户沟通记录')
  assert.equal(workflowDisplayLabel('invoiceReference'), 'invoiceReference')
  assert.equal(workflowDisplayLabel('constructor'), 'constructor')
  assert.equal(workflowOptionLabel('ACME'), 'ACME')
  assert.equal(workflowOptionLabel('__proto__'), '__proto__')
})

test('嵌套校验提示使用业务标题和从一开始的序号', () => {
  const schema = {properties: {messages: {title: '对话记录', items: {properties: {content: {title: '消息正文'}}}}}}
  const path = '$.messages[0].content'
  assert.equal(workflowDisplayPath(path, schema), '对话记录 / 第 1 项 / 消息正文')
  assert.equal(workflowDisplayPath('$', schema), '输入内容')
})

test('图片生成方式只翻译显示文案', () => {
  assert.equal(workflowOptionLabel('image_to_image'), '图生图')
  assert.equal(workflowOptionLabel('background_replacement'), '更换背景')
})
