export function customerContactConfiguration(value = {}) {
  return { name: value.name || '人工客服', webUrl: value.webUrl || '', phone: value.phone || '', serviceHours: value.serviceHours || '', note: value.note || '' }
}
export function customerContactSettings(value) {
  if (!value) return null
  const text = (item, limit, multiline = false) => {
    if (multiline && typeof item === 'string') item = item.replace(/\r\n/g, '\n')
    if (item == null || typeof item === 'string' && !item.trim()) return null
    if (typeof item !== 'string' || item.length > limit || (multiline ? /[\u0000-\u0009\u000b-\u001f\u007f-\u009f]/u : /[\u0000-\u001f\u007f-\u009f]/u).test(item)) throw new Error('人工联系信息格式或长度无效')
    return item.trim()
  }
  const contact = { name: text(value.name, 64), webUrl: text(value.webUrl, 500), phone: text(value.phone, 32), serviceHours: text(value.serviceHours, 120), note: text(value.note, 500, true) }
  if (!contact.name || !contact.webUrl && !contact.phone) throw new Error('请填写人工入口名称，并配置客服网页或电话号码')
  if (contact.webUrl) {
    let url
    try { url = new URL(contact.webUrl) } catch { throw new Error('客服网页必须是有效的 HTTPS 地址') }
    const authority = contact.webUrl.match(/^https:\/\/([^/?#]*)/i)?.[1]
    if (url.protocol !== 'https:' || !authority || /[@\\\u0080-\uffff]/u.test(authority) || contact.webUrl.includes('\\') || url.port === '0') throw new Error('客服网页必须使用不含登录凭据的 HTTPS 地址')
  }
  if (contact.phone && (!/^\+?[0-9 ()-]+$/.test(contact.phone) || contact.phone.replace(/[^0-9]/g, '').length < 4 || contact.phone.replace(/[^0-9]/g, '').length > 20)) throw new Error('请填写有效电话号码，不支持分机指令或其他链接')
  return contact
}
export function customerContactPhoneLink(phone) {
  try { return `tel:${customerContactSettings({ name: '人工客服', phone }).phone.replace(/[ ()-]/g, '')}` } catch { return null }
}
