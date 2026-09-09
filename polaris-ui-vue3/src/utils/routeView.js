function normalizeViewName(view) {
  return String(view || '')
    .trim()
    .replace(/\\/g, '/')
    .replace(/^\.\//, '')
    .replace(/^\/+/, '')
    .replace(/^views\//, '')
    .replace(/\.vue$/, '')
    .replace(/\/+$/, '')
}

function moduleViewName(path) {
  const match = String(path).replace(/\\/g, '/').match(/(?:^|\/)views\/(.+)\.vue$/)
  return match ? match[1] : ''
}

/**
 * Resolve a backend menu component to a Vite view module.
 *
 * RuoYi menu data historically uses both `foo/bar` and `foo/bar/index`
 * for directory-based pages. Supporting both keeps existing databases
 * compatible when a single-file view is moved into an index.vue directory.
 */
export function resolveViewModule(view, modules) {
  const normalizedView = normalizeViewName(view)
  if (!normalizedView) return undefined

  const modulesByView = new Map()
  Object.entries(modules || {}).forEach(([path, loader]) => {
    const name = moduleViewName(path)
    if (name) modulesByView.set(name, loader)
  })

  return modulesByView.get(normalizedView)
    || modulesByView.get(`${normalizedView}/index`)
}
