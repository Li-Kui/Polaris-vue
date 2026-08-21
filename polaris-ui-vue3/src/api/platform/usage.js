import request from '@/utils/platformRequest'

export function getUsage() {
  return request({
    url: '/platform/console/usage',
    method: 'get'
  })
}
