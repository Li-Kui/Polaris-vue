/**
 * AI 内容安全检测 - 违规风险分类字典与辅助工具
 */

export const MODERATION_CATEGORIES = {
  DRUGS: {
    code: 'DRUGS',
    label: '涉毒违禁',
    emoji: '💊',
    color: '#ef4444',
    bgColor: 'rgba(239, 68, 68, 0.1)',
    tagType: 'danger'
  },
  PORNOGRAPHY: {
    code: 'PORNOGRAPHY',
    label: '色情低俗',
    emoji: '🔞',
    color: '#f43f5e',
    bgColor: 'rgba(244, 63, 94, 0.1)',
    tagType: 'danger'
  },
  PORN: {
    code: 'PORN',
    label: '色情低俗',
    emoji: '🔞',
    color: '#f43f5e',
    bgColor: 'rgba(244, 63, 94, 0.1)',
    tagType: 'danger'
  },
  ABUSE: {
    code: 'ABUSE',
    label: '辱骂攻击',
    emoji: '🤬',
    color: '#f97316',
    bgColor: 'rgba(249, 115, 22, 0.1)',
    tagType: 'warning'
  },
  GAMBLING: {
    code: 'GAMBLING',
    label: '涉赌博彩',
    emoji: '🎲',
    color: '#eab308',
    bgColor: 'rgba(234, 179, 8, 0.1)',
    tagType: 'warning'
  },
  POLITICS: {
    code: 'POLITICS',
    label: '政治敏感',
    emoji: '🏛️',
    color: '#a855f7',
    bgColor: 'rgba(168, 85, 247, 0.1)',
    tagType: ''
  },
  TERRORISM: {
    code: 'TERRORISM',
    label: '暴恐极端',
    emoji: '💣',
    color: '#64748b',
    bgColor: 'rgba(100, 116, 139, 0.1)',
    tagType: 'info'
  },
  TERROR: {
    code: 'TERROR',
    label: '暴恐极端',
    emoji: '💣',
    color: '#64748b',
    bgColor: 'rgba(100, 116, 139, 0.1)',
    tagType: 'info'
  },
  VIOLENCE: {
    code: 'VIOLENCE',
    label: '暴力血腥',
    emoji: '⚔️',
    color: '#dc2626',
    bgColor: 'rgba(220, 38, 38, 0.1)',
    tagType: 'danger'
  },
  CONTRABAND: {
    code: 'CONTRABAND',
    label: '违禁交易',
    emoji: '🚫',
    color: '#ea580c',
    bgColor: 'rgba(234, 88, 12, 0.1)',
    tagType: 'warning'
  },
  FRAUD: {
    code: 'FRAUD',
    label: '网络诈骗',
    emoji: '⚠️',
    color: '#d97706',
    bgColor: 'rgba(217, 119, 6, 0.1)',
    tagType: 'warning'
  },
  GENERAL: {
    code: 'GENERAL',
    label: '通用风险',
    emoji: '📋',
    color: '#3b82f6',
    bgColor: 'rgba(59, 130, 246, 0.1)',
    tagType: ''
  }
}

/**
 * 预设常用分类列表（供下拉选择器使用）
 */
export const CATEGORY_OPTIONS = [
  { value: 'DRUGS', label: '💊 涉毒违禁 (DRUGS)' },
  { value: 'PORNOGRAPHY', label: '🔞 色情低俗 (PORNOGRAPHY)' },
  { value: 'ABUSE', label: '🤬 辱骂攻击 (ABUSE)' },
  { value: 'GAMBLING', label: '🎲 涉赌博彩 (GAMBLING)' },
  { value: 'POLITICS', label: '🏛️ 政治敏感 (POLITICS)' },
  { value: 'TERRORISM', label: '💣 暴恐极端 (TERRORISM)' },
  { value: 'VIOLENCE', label: '⚔️ 暴力血腥 (VIOLENCE)' },
  { value: 'CONTRABAND', label: '🚫 违禁交易 (CONTRABAND)' },
  { value: 'FRAUD', label: '⚠️ 网络诈骗 (FRAUD)' },
  { value: 'GENERAL', label: '📋 通用风险 (GENERAL)' }
]

/**
 * 获取分类的元数据
 * @param {string} categoryCode 分类原始编码
 * @returns {object} 元数据对象
 */
export function getCategoryMeta(categoryCode) {
  if (!categoryCode) {
    return {
      code: '',
      label: '未分类',
      emoji: '🏷️',
      color: '#94a3b8',
      bgColor: 'rgba(148, 163, 184, 0.1)',
      tagType: 'info'
    }
  }

  const upperCode = String(categoryCode).trim().toUpperCase()
  if (MODERATION_CATEGORIES[upperCode]) {
    return MODERATION_CATEGORIES[upperCode]
  }

  // 模糊匹配判断
  if (upperCode.includes('DRUG')) return MODERATION_CATEGORIES.DRUGS
  if (upperCode.includes('PORN')) return MODERATION_CATEGORIES.PORNOGRAPHY
  if (upperCode.includes('ABUS')) return MODERATION_CATEGORIES.ABUSE
  if (upperCode.includes('GAMBL')) return MODERATION_CATEGORIES.GAMBLING
  if (upperCode.includes('POLITIC')) return MODERATION_CATEGORIES.POLITICS
  if (upperCode.includes('TERROR')) return MODERATION_CATEGORIES.TERRORISM
  if (upperCode.includes('VIOLEN')) return MODERATION_CATEGORIES.VIOLENCE
  if (upperCode.includes('CONTRABAND')) return MODERATION_CATEGORIES.CONTRABAND
  if (upperCode.includes('FRAUD')) return MODERATION_CATEGORIES.FRAUD

  // 兜底返回自定义分类
  return {
    code: categoryCode,
    label: categoryCode,
    emoji: '🏷️',
    color: '#6366f1',
    bgColor: 'rgba(99, 102, 241, 0.1)',
    tagType: ''
  }
}

/**
 * 获取分类中文标签名称
 * @param {string} categoryCode 分类原始编码
 * @param {boolean} withCode 是否附带英文编码后缀，例如 "涉毒违禁 (DRUGS)"
 */
export function getCategoryLabel(categoryCode, withCode = false) {
  const meta = getCategoryMeta(categoryCode)
  if (!categoryCode) return meta.label
  if (withCode && meta.code && meta.label !== meta.code) {
    return `${meta.label} (${meta.code})`
  }
  return meta.label
}

/**
 * 获取分类图标
 */
export function getCategoryEmoji(categoryCode) {
  return getCategoryMeta(categoryCode).emoji
}

/**
 * 获取分类进度条主色
 */
export function getCategoryColor(categoryCode) {
  return getCategoryMeta(categoryCode).color
}
