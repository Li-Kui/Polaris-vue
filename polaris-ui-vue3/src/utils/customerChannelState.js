// 渠道码仅定位入口。链接和嵌入代码不携带平台令牌或业务身份。
export function customerChannelLinks(origin, apiBase, code, accessMode = 'TENANT_ADMIN') {
  if (!/^[a-f0-9]{32}$/.test(code || '')) throw new Error('无效的渠道码')
  const source = new URL(origin).origin
  const api = new URL(apiBase || '/', `${source}/`)
  if (api.origin !== source) throw new Error('客服嵌入需要前后端同源代理')
  const entry = `${source}/platform/${accessMode === 'VISITOR' ? 'visitor' : 'customer'}/${code}`
  const embed = `${source}${api.pathname.replace(/\/$/, '')}/platform/runtime/customer-channels/${code}/embed`
  const html = `<iframe src="${embed}" title="数字人客服文字接待" width="100%" height="720" allow="microphone" style="border:0" referrerpolicy="same-origin"></iframe>`
  const widget = accessMode === 'VISITOR' ? `<script src="${embed.replace(/\/embed$/, '/widget.js')}" async></script>` : ''
  return { entry, embed, html, widget }
}

// 独立页可以直接打开；嵌入必须通过同源后端容器，由容器的 CSP 检查全部祖先来源。
export function customerChannelFrameAllowed(code, selfOrigin, parentOrigin, parentPath) {
  if (!/^[a-f0-9]{32}$/.test(code || '') || parentOrigin !== selfOrigin) return false
  return new RegExp(`^(?:/dev-api|/prod-api)?/platform/runtime/customer-channels/${code}/embed$`).test(parentPath || '')
}

export function customerChannelSettings(form) {
  const expiry = new Date(form.expiresAt)
  if (!Number.isFinite(expiry.getTime())) throw new Error('请选择有效的到期时间')
  const result = {
    name: form.name.trim(),
    allowedOrigins: form.origins.split('\n').map(value => value.trim()).filter(Boolean),
    capabilityCodes: [...form.capabilityCodes],
    expiresAt: expiry.toISOString()
  }
  if (form.accessMode !== undefined) {
    if (!['TENANT_ADMIN', 'VISITOR'].includes(form.accessMode) || !Number.isInteger(form.visitorDailySessions) || form.visitorDailySessions < 1 || form.visitorDailySessions > 10000
        || !Number.isInteger(form.visitorMessageLimit) || form.visitorMessageLimit < 1 || form.visitorMessageLimit > 50) throw new Error('访问方式或访客限额无效')
    if (form.accessMode === 'VISITOR' && result.capabilityCodes.length) throw new Error('访客接待首批仅开放知识咨询')
    Object.assign(result, { accessMode: form.accessMode, visitorDailySessions: form.visitorDailySessions, visitorMessageLimit: form.visitorMessageLimit })
    if (form.visitorSpeechEnabled !== undefined || form.visitorSpeechLimit !== undefined) {
      if (typeof form.visitorSpeechEnabled !== 'boolean' || !Number.isInteger(form.visitorSpeechLimit) || form.visitorSpeechLimit < 1 || form.visitorSpeechLimit > 40) throw new Error('访客语音设置或请求上限无效')
      Object.assign(result, { visitorSpeechEnabled: form.accessMode === 'VISITOR' && form.visitorSpeechEnabled, visitorSpeechLimit: form.visitorSpeechLimit })
    }
  }
  return result
}
