/**
 * AI 绘图多图 URL 安全解析工具
 * 兼容处理：
 * 1. JSON 数组字符串：'["/profile/a.png", "/profile/b.png"]'
 * 2. 逗号分隔字符串：'/profile/a.png,/profile/b.png'
 * 3. 数组对象：['/profile/a.png', '/profile/b.png']
 * 4. 普通单图 URL 字符串：'/profile/a.png'
 *
 * @param {string|Array} rawImageUrl 原始图片字段值
 * @returns {Array<string>} 图片 URL 字符串数组（绝不返回 null）
 */
export function parseImageUrlList(rawImageUrl) {
  if (!rawImageUrl) return []
  if (Array.isArray(rawImageUrl)) {
    return rawImageUrl.filter(item => typeof item === 'string' && item.trim().length > 0)
  }
  const str = String(rawImageUrl).trim()
  if (!str) return []

  if (str.startsWith('[')) {
    try {
      const arr = JSON.parse(str)
      if (Array.isArray(arr)) {
        return arr.filter(item => typeof item === 'string' && item.trim().length > 0)
      }
    } catch (e) {
      // 容错解析失败，继续尝试后续解析
    }
  }

  if (str.includes(',')) {
    return str.split(',').map(s => s.trim()).filter(Boolean)
  }

  return [str]
}
