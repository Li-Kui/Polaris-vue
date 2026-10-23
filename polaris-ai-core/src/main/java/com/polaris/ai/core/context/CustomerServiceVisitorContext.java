package com.polaris.ai.core.context;

import java.util.Set;

/** 仅由客服运行接口在校验渠道及会话凭证后建立；不具有平台用户、管理员或工作流权限。 */
public record CustomerServiceVisitorContext(Long tenantId, String channelId, String sessionId,
        String tokenHash, Long modelId, Set<Long> knowledgeBaseIds, int messageLimit,
        Long sttModelId, Long ttsModelId, int speechLimit) implements CallerContext {
    public CustomerServiceVisitorContext(Long tenantId, String channelId, String sessionId, String tokenHash,
            Long modelId, Set<Long> knowledgeBaseIds, int messageLimit) {
        this(tenantId, channelId, sessionId, tokenHash, modelId, knowledgeBaseIds, messageLimit, null, null, 0);
    }
    public CustomerServiceVisitorContext {
        if (tenantId == null || tenantId <= 0 || channelId == null || sessionId == null || tokenHash == null
                || modelId == null || knowledgeBaseIds == null || messageLimit < 1 || messageLimit > 50
                || speechLimit < 0 || speechLimit > 40 || speechLimit == 0 && (sttModelId != null || ttsModelId != null)) {
            throw new IllegalArgumentException("无效的访客上下文");
        }
        knowledgeBaseIds = Set.copyOf(knowledgeBaseIds);
    }
    @Override public Long getUserId() { return 0L; }
    @Override public String getTenantId() { return tenantId.toString(); }
    @Override public String getUsername() { return "customer-visitor:" + sessionId; }
    @Override public Long getDeptId() { return null; }
    @Override public boolean isSuperAdmin() { return false; }
    @Override public boolean isTenantAdmin() { return false; }
}
