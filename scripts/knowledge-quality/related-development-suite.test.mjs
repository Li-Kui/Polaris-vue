import assert from 'node:assert/strict'
import test from 'node:test'
import {suite} from './related-development-suite.mjs'
import {suite as oldSuite} from './natural-long-suite.mjs'

test('C1 合成开发集独立于旧长文题目，符合新预算且不重复消费保留集', () => {
  assert.equal(suite.documents.length, 6)
  assert.ok(suite.documents.reduce((total, document) => total + document.content.length, 0) <= 6000)
  assert.equal(new Set(suite.documents.map(d => d.key)).size, 6)
  assert.equal(new Set(suite.documents.map(d => d.fileName)).size, 6)
  assert.equal(suite.cases.length * 2, suite.budget.retrievalRequests)
  for (const item of suite.cases) {
    assert.ok(!oldSuite.cases.some(old => old.query === item.query))
    assert.ok(item.related.length > 0 && item.related.length <= 3)
    assert.equal(new Set(item.related).size, item.related.length)
    for (const key of [...item.related, ...item.evidence]) assert.ok(suite.documents.some(d => d.key === key))
  }
  assert.equal(suite.generationCaseIds.length, suite.budget.answerRequests)
  for (const id of suite.generationCaseIds) assert.ok(suite.cases.some(item => item.id === id))
})
