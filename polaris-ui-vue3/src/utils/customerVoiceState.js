export function customerWav(samples, sampleRate) {
  if (!(samples instanceof Float32Array) || !Number.isFinite(sampleRate) || sampleRate < 8000 || sampleRate > 192000
    || samples.length / sampleRate < 0.2 || samples.length / sampleRate > 20.01) throw new Error('请录制 0.2～20 秒的语音')
  const length = Math.min(320000, Math.floor(samples.length * 16000 / sampleRate))
  const buffer = new ArrayBuffer(44 + length * 2), view = new DataView(buffer)
  const tag = (offset, text) => [...text].forEach((char, index) => view.setUint8(offset + index, char.charCodeAt(0)))
  tag(0, 'RIFF'); view.setUint32(4, buffer.byteLength - 8, true); tag(8, 'WAVE'); tag(12, 'fmt ')
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true)
  view.setUint32(24, 16000, true); view.setUint32(28, 32000, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true)
  tag(36, 'data'); view.setUint32(40, length * 2, true)
  for (let index = 0; index < length; index++) {
    const position = index * sampleRate / 16000, left = Math.floor(position), fraction = position - left
    const value = samples[left] * (1 - fraction) + (samples[left + 1] ?? samples[left]) * fraction
    const bounded = Math.max(-1, Math.min(1, Number.isFinite(value) ? value : 0))
    view.setInt16(44 + index * 2, Math.round(bounded * (bounded < 0 ? 32768 : 32767)), true)
  }
  return buffer
}
export function customerTranscript(text, current = '') {
  const draft = String(text || '').trim()
  if (!draft || draft.length > 2000) throw new Error('识别文字为空或过长，请编辑后再填入')
  const merged = current.trim() ? `${current.trim()}\n${draft}` : draft
  if (merged.length > 2000) throw new Error('合并后超过 2000 字，请先整理输入框')
  return merged
}
export function customerLocalVoices(voices = []) { return voices.filter(voice => voice.localService === true && /^zh(?:-|_|$)/i.test(voice.lang || '')) }
export function customerSpokenText(text) { return String(text || '').replace(/\[S\d+\]/g, '').replace(/[*#`]/g, '').trim().slice(0, 2000) }
export function customerSpeechHint(code) {
  return ({ CONTENT_BLOCKED: '识别内容未通过检测，请使用文字或重新录音。', SPEECH_TEXT_INVALID: '未识别到有效短文本，请重新录音或使用文字。',
    SPEECH_UNAVAILABLE: '语音模型不可用或已变化，请检查配置并重新发布。', SPEECH_PROVIDER_FAILED: '语音服务暂不可用，请使用文字接待。',
    QUOTA_EXCEEDED: '租户配额不足，请使用文字或联系管理员。', SPEECH_EXPIRED: '识别已超时，不会自动重跑。',
    SPEECH_INTERRUPTED: '本次识别已取消或会话已失效。', SPEECH_AUDIO_EXPIRED: '播报音频已过期，不会自动重新合成，可使用文字或设备音色。', TRANSCRIPT_EXPIRED: '识别草稿已过期，请重新录制或使用文字。' })[code] || '识别未完成，请使用文字，或核对原请求状态。'
}
