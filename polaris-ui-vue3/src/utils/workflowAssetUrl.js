/** Resolve backend-owned files for display only; workflow inputs keep their storage paths. */
export function resolveWorkflowAssetUrl(value, apiBase = '') {
  if (typeof value !== 'string' || !value.startsWith('/profile/')) return value
  const origin = 'https://workflow-assets.invalid'
  try {
    const asset = new URL(value, origin)
    if (asset.origin !== origin || !asset.pathname.startsWith('/profile/')) return ''
    const base = new URL(apiBase || '/', origin)
    if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password || apiBase.startsWith('//')) return ''
    base.pathname = `${base.pathname.replace(/\/+$/, '')}/`
    base.search = ''
    base.hash = ''
    const result = new URL(`${asset.pathname.slice(1)}${asset.search}${asset.hash}`, base)
    return /^https?:\/\//i.test(apiBase) ? result.toString() : `${result.pathname}${result.search}${result.hash}`
  } catch { return '' }
}
