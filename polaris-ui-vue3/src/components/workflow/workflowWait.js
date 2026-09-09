export const WAIT_DEFAULT_CONFIG = Object.freeze({
  configVersion: '2.0',
  schedule: {
    kind: 'AFTER',
    source: {
      kind: 'FIXED', value: 5, unit: 'MINUTE', localDateTime: '',
      timezone: 'Asia/Shanghai'
    }
  },
  pastDuePolicy: 'CONTINUE',
  safety: {maxWaitSeconds: 30 * 24 * 60 * 60}
})

const units = {SECOND: '秒', MINUTE: '分钟', HOUR: '小时', DAY: '天'}
const unitSeconds = {SECOND: 1, MINUTE: 60, HOUR: 3600, DAY: 86400}

export function normalizeWaitConfig(value = {}) {
  const schedule = value.schedule || {}
  const source = schedule.source || {}
  return {
    configVersion: '2.0',
    schedule: {
      kind: ['AFTER', 'AT'].includes(schedule.kind) ? schedule.kind : 'AFTER',
      source: {
        kind: ['FIXED', 'INPUT'].includes(source.kind) ? source.kind : 'FIXED',
        value: Number.isInteger(Number(source.value)) ? Number(source.value) : 5,
        unit: units[source.unit] ? source.unit : 'MINUTE',
        localDateTime: source.localDateTime || '',
        timezone: source.timezone || 'Asia/Shanghai'
      }
    },
    pastDuePolicy: value.pastDuePolicy === 'FAIL' ? 'FAIL' : 'CONTINUE',
    safety: {
      maxWaitSeconds: Math.min(365 * 86400,
        Math.max(1, Math.floor(Number(value.safety?.maxWaitSeconds) || 30 * 86400)))
    }
  }
}

export function waitConfigurationState({config, inputMapping} = {}) {
  const normalized = normalizeWaitConfig(config)
  const {kind, source} = normalized.schedule
  const issues = []
  if (source.kind === 'INPUT') {
    const field = kind === 'AFTER' ? 'duration' : 'targetAt'
    if (!inputMapping?.[field]?.expression) issues.push(kind === 'AFTER'
      ? '请选择提供等待时长的上游字段'
      : '请选择提供目标时间的上游字段')
  } else if (kind === 'AFTER' && (!Number.isInteger(source.value) || source.value <= 0)) {
    issues.push('固定等待时长必须是大于 0 的整数')
  } else if (kind === 'AFTER' && source.kind === 'FIXED'
    && source.value * unitSeconds[source.unit] > normalized.safety.maxWaitSeconds) {
    issues.push('固定等待时长超过了安全上限')
  } else if (kind === 'AT' && !source.localDateTime) {
    issues.push('请选择目标日期和时间')
  }
  if (!source.timezone) issues.push('请选择业务时区')
  return {
    code: issues.length ? 'INCOMPLETE' : 'READY',
    label: issues.length ? `${issues.length} 项待完成` : '配置完成',
    tone: issues.length ? 'warning' : 'success',
    issues
  }
}

export function waitSummary(config, inputMapping = {}) {
  const {schedule} = normalizeWaitConfig(config)
  const source = schedule.source
  if (schedule.kind === 'AFTER') {
    return source.kind === 'INPUT'
      ? `等待上游指定时长${inputMapping.duration?.expression ? '' : '（待选择字段）'}`
      : `等待 ${source.value} ${units[source.unit]}`
  }
  if (source.kind === 'INPUT') {
    return `等到上游目标时间${inputMapping.targetAt?.expression ? '' : '（待选择字段）'}`
  }
  if (!source.localDateTime) return '等到指定时间（待选择）'
  return `等到 ${source.localDateTime.replace('T', ' ')}（${source.timezone}）`
}

export function waitMaximumLabel(seconds) {
  const value = Math.max(1, Number(seconds) || 1)
  if (value % 86400 === 0) return `${value / 86400} 天`
  if (value % 3600 === 0) return `${value / 3600} 小时`
  if (value % 60 === 0) return `${value / 60} 分钟`
  return `${value} 秒`
}
