// 只编辑实体词表，保留顺序和旧配置；不从知识库正文自动提取或扩散术语。
export function customerRecognitionVocabulary(text) {
  const words = String(text || '').split(/\r?\n/).map(word => word.trim()).filter(Boolean)
  if (words.length > 20 || words.join('').length > 300 || new Set(words).size !== words.length
    || words.some(word => !/^[\p{L}\p{N}][\p{L}\p{N} ._+/-]{0,29}$/u.test(word))) {
    throw new Error('识别术语最多20项、每项30字、合计300字，不能重复；请每行填写一个专有名词。')
  }
  return words
}
export function customerVoiceConfiguration(value = {}) {
  return { sttModelId: value.sttModelId || null, ttsModelId: value.ttsModelId || null,
    devicePlayback: value.devicePlayback === true, vocabulary: [...(value.vocabulary || [])], briefReplies: value.briefReplies === true }
}
