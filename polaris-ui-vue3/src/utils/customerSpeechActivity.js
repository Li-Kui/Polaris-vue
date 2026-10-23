// 本地能量分句，不调用浏览器远程识别；按样本时长计算，避免设备分片大小影响停顿判断。
export function createCustomerSpeechActivity({ silenceMillis = 1500, waitingMillis = 8000, minimumListeningMillis = 3000 } = {}) {
  let rate, energy = 0, count = 0, elapsed = 0, voiced = 0, quiet = 0, noise = .002, ended = false
  return {
    push(samples, sampleRate) {
      if (ended) return null
      if (!Number.isFinite(sampleRate) || sampleRate < 8000 || sampleRate > 192000 || rate && rate !== sampleRate) throw new Error('录音采样率不可用')
      rate = sampleRate
      const frame = Math.round(rate * .02)
      for (const value of samples) {
        energy += Number.isFinite(value) ? value * value : 0
        if (++count < frame) continue
        const rms = Math.sqrt(energy / count), millis = count * 1000 / rate
        energy = 0; count = 0; elapsed += millis
        if (rms >= Math.max(.012, noise * 3)) { voiced += millis; quiet = 0 }
        else { quiet += millis; if (voiced < 300) voiced = 0; noise = noise * .95 + Math.min(rms, .01) * .05 }
        // 起录瞬态和短停顿不能立即结束本句；保护期内仍保留全部样本并继续检测后续人声。
        if (elapsed >= minimumListeningMillis && voiced >= 300 && quiet >= silenceMillis) { ended = true; return 'END' }
        if (elapsed >= waitingMillis && voiced < 300) { ended = true; return 'SILENCE' }
      }
      return null
    }
  }
}
