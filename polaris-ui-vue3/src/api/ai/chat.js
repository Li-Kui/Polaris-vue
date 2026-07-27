import request from '@/utils/request'

// 获取会话列表
export function listConversations() {
  return request({
    url: '/ai/chat/conversations',
    method: 'get'
  })
}

// 新建会话
export function createConversation(modelConfigId, knowledgeBaseId) {
  return request({
    url: '/ai/chat/conversations',
    method: 'post',
    params: { modelConfigId, knowledgeBaseId }
  })
}

// 重命名会话
export function renameConversation(id, title) {
  return request({
    url: `/ai/chat/conversations/${id}/title`,
    method: 'put',
    params: { title }
  })
}

// 更新会话的大模型、知识库、智能体与工作流配置
export function updateConversationConfig(id, modelConfigId, knowledgeBaseId, agentCode, workflowCode) {
  return request({
    url: `/ai/chat/conversations/${id}/config`,
    method: 'put',
    params: { modelConfigId, knowledgeBaseId, agentCode, workflowCode }
  })
}

// 删除会话
export function deleteConversation(id) {
  return request({
    url: `/ai/chat/conversations/${id}`,
    method: 'delete'
  })
}

// 批量删除会话
export function deleteConversationsBatch(ids) {
  return request({
    url: '/ai/chat/conversations/batch',
    method: 'delete',
    data: ids
  })
}

// 获取会话消息历史
export function listMessages(conversationId) {
  return request({
    url: `/ai/chat/conversations/${conversationId}/messages`,
    method: 'get'
  })
}

// 获取已启用的智能体列表
export function listActiveAgents() {
  return request({
    url: '/ai/agent/list/all',
    method: 'get'
  })
}
