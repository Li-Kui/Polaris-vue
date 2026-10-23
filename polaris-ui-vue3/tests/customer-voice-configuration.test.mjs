import test from 'node:test'
import assert from 'node:assert/strict'
import {customerRecognitionVocabulary, customerVoiceConfiguration} from '../src/utils/customerVoiceConfiguration.js'

test('识别术语按行保留顺序并去空行，不静默删除重复词或改写实体', () => {
  assert.deepEqual(customerRecognitionVocabulary(' 星河归档中心\r\n\n iPhone16 '), ['星河归档中心', 'iPhone16'])
  for (const text of ['星河\n星河', '<system>', 'x'.repeat(31), Array(21).fill('term').join('\n')]) assert.throws(() => customerRecognitionVocabulary(text))
})
test('编辑旧语音配置补齐可选项，保存新选项时不污染原数组', () => {
  assert.deepEqual(customerVoiceConfiguration(), { sttModelId: null, ttsModelId: null, devicePlayback: false, vocabulary: [], briefReplies: false })
  const original = { sttModelId: 14, vocabulary: ['星河归档中心'], briefReplies: true }
  const value = customerVoiceConfiguration(original); value.vocabulary.push('另一机构')
  assert.equal(original.vocabulary.length, 1); assert.equal(value.briefReplies, true); assert.equal(value.sttModelId, 14)
})
