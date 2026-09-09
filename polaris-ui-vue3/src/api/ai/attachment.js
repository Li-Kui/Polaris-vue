import request from '@/utils/request'

// 上传私有 AI 附件
export function uploadPrivateAttachment(data) {
  return request({
    url: '/ai/attachment/upload-private',
    method: 'post',
    data: data,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
