/** Resolve the public API address through the same proxy as platformRequest. */
export function getPlatformOpenApiBaseUrl(origin, apiBase = '') {
  const url = new URL(apiBase || '/', `${new URL(origin).origin}/`)
  url.pathname = `${url.pathname.replace(/\/+$/, '')}/platform/api/v1`
  url.search = ''
  url.hash = ''
  return url.toString()
}
