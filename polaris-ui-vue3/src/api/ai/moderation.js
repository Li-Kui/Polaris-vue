import request from '@/utils/request'

// 策略接口
export const listModerationPolicies = () => request({
  url: '/ai/moderation/policies',
  method: 'get'
})

export const updateModerationPolicy = (scene, data) => request({
  url: `/ai/moderation/policies/${scene}`,
  method: 'put',
  data
})

export const testModerationSentence = data => request({
  url: '/ai/moderation/test',
  method: 'post',
  data
})

// 词库版本与规则接口
export const listDictionaryVersions = () => request({
  url: '/ai/moderation/dictionary/versions',
  method: 'get'
})

export const createDictionaryDraft = baseVersionId => request({
  url: '/ai/moderation/dictionary/drafts',
  method: 'post',
  params: { baseVersionId }
})

export const listDictionaryRules = (versionId, params) => request({
  url: `/ai/moderation/dictionary/versions/${versionId}/rules`,
  method: 'get',
  params
})

export const addDictionaryRule = (versionId, data) => request({
  url: `/ai/moderation/dictionary/versions/${versionId}/rules`,
  method: 'post',
  data
})

export const updateDictionaryRule = (versionId, ruleId, data) => request({
  url: `/ai/moderation/dictionary/versions/${versionId}/rules/${ruleId}`,
  method: 'put',
  data
})

export const deleteDictionaryRule = (versionId, ruleId) => request({
  url: `/ai/moderation/dictionary/versions/${versionId}/rules/${ruleId}`,
  method: 'delete'
})

export const publishDictionaryVersion = versionId => request({
  url: `/ai/moderation/dictionary/versions/${versionId}/publish`,
  method: 'post'
})

export const rollbackDictionaryVersion = versionId => request({
  url: `/ai/moderation/dictionary/versions/${versionId}/rollback`,
  method: 'post'
})

export const diffDictionaryVersion = (versionId, baseVersionId) => request({
  url: `/ai/moderation/dictionary/versions/${versionId}/diff`,
  method: 'get',
  params: { baseVersionId }
})

export const exportDictionaryRulesUrl = versionId => `/ai/moderation/dictionary/versions/${versionId}/export`

export const previewRuleImport = data => request({
  url: '/ai/moderation/dictionary/import/preview',
  method: 'post',
  headers: { 'Content-Type': 'multipart/form-data' },
  data
})

export const applyRuleImport = (draftVersionId, data) => request({
  url: '/ai/moderation/dictionary/import/apply',
  method: 'post',
  params: { draftVersionId },
  data
})

// 候选词接口
export const listCandidates = params => request({
  url: '/ai/moderation/candidates',
  method: 'get',
  params
})

export const batchAcceptCandidates = data => request({
  url: '/ai/moderation/candidates/batch-accept',
  method: 'post',
  data
})

export const batchRejectCandidates = data => request({
  url: '/ai/moderation/candidates/batch-reject',
  method: 'post',
  data
})

// 统计与运维接口
export const getModerationSummary = params => request({
  url: '/ai/moderation/statistics/summary',
  method: 'get',
  params
})

export const triggerModerationCleanup = () => request({
  url: '/ai/moderation/cleanup/trigger',
  method: 'post'
})
