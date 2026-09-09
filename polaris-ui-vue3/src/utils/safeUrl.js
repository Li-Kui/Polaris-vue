const CONTROL_CHARACTERS = /[\u0000-\u001F\u007F-\u009F]/

/** Restrict links rendered from model or search output to browser-safe schemes. */
export function sanitizeUrl(value, options = {}) {
  const { allowMailto = true, allowRelative = true } = options
  const url = String(value || '').trim()
  if (!url || CONTROL_CHARACTERS.test(url) || url.includes('\\')) return null

  if (allowRelative && url.startsWith('/') && !url.startsWith('//')) {
    try {
      const base = typeof window === 'undefined' ? 'http://localhost' : window.location.origin
      const parsed = new URL(url, base)
      return parsed.origin === base ? url : null
    } catch (_) {
      return null
    }
  }

  if (/^https?:\/\//i.test(url)) {
    try {
      const parsed = new URL(url)
      return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? url : null
    } catch (_) {
      return null
    }
  }

  if (allowMailto && /^mailto:/i.test(url)) return url
  return null
}
