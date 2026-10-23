package com.polaris.ai.customer.application;

import com.polaris.common.exception.ServiceException;

import java.util.HashSet;
import java.util.List;

/** 客服文字接待及能力绑定配置；资源仅保存引用，不保存凭据。 */
public record CustomerServiceConfig(
        String name, String persona, String welcomeMessage, String noAnswerMessage,
        Long modelId, List<Long> knowledgeBaseIds, List<CustomerServiceCapability> capabilities, CustomerServiceVoiceConfig voice,
        String responseMode, CustomerServiceAvatarConfig avatar, CustomerServiceContactConfig contact) {

    public CustomerServiceConfig(String name, String persona, String welcomeMessage, String noAnswerMessage,
            Long modelId, List<Long> knowledgeBaseIds, List<CustomerServiceCapability> capabilities,
            CustomerServiceVoiceConfig voice, String responseMode, CustomerServiceAvatarConfig avatar) {
        this(name, persona, welcomeMessage, noAnswerMessage, modelId, knowledgeBaseIds, capabilities, voice, responseMode, avatar, null);
    }

    public CustomerServiceConfig(String name, String persona, String welcomeMessage, String noAnswerMessage,
            Long modelId, List<Long> knowledgeBaseIds, List<CustomerServiceCapability> capabilities,
            CustomerServiceVoiceConfig voice, String responseMode) {
        this(name, persona, welcomeMessage, noAnswerMessage, modelId, knowledgeBaseIds, capabilities, voice, responseMode, null);
    }

    public CustomerServiceConfig(String name, String persona, String welcomeMessage, String noAnswerMessage,
            Long modelId, List<Long> knowledgeBaseIds, List<CustomerServiceCapability> capabilities, CustomerServiceVoiceConfig voice) {
        this(name, persona, welcomeMessage, noAnswerMessage, modelId, knowledgeBaseIds, capabilities, voice, null);
    }

    public CustomerServiceConfig(String name, String persona, String welcomeMessage, String noAnswerMessage,
            Long modelId, List<Long> knowledgeBaseIds, List<CustomerServiceCapability> capabilities) {
        this(name, persona, welcomeMessage, noAnswerMessage, modelId, knowledgeBaseIds, capabilities, null);
    }

    public CustomerServiceConfig(String name, String persona, String welcomeMessage, String noAnswerMessage,
            Long modelId, List<Long> knowledgeBaseIds) {
        this(name, persona, welcomeMessage, noAnswerMessage, modelId, knowledgeBaseIds, List.of());
    }

    public CustomerServiceConfig {
        name = text(name, 64);
        persona = text(persona, 2000);
        welcomeMessage = text(welcomeMessage, 500);
        noAnswerMessage = text(noAnswerMessage, 500);
        responseMode = responseMode == null ? "MODEL_DEFAULT" : responseMode;
        if (!List.of("MODEL_DEFAULT", "FAST").contains(responseMode)) {
            throw new ServiceException("INVALID_INPUT: 客服响应模式无效", 400);
        }
        if (modelId == null || modelId <= 0 || knowledgeBaseIds == null
                || knowledgeBaseIds.isEmpty() || knowledgeBaseIds.size() > 3
                || knowledgeBaseIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(knowledgeBaseIds).size() != knowledgeBaseIds.size()) {
            throw new ServiceException("INVALID_INPUT: 请选择对话模型和 1～3 个不重复的知识库", 400);
        }
        knowledgeBaseIds = List.copyOf(knowledgeBaseIds);
        capabilities = capabilities == null ? List.of() : capabilities;
        if (capabilities.size() > 5 || capabilities.stream().anyMatch(value -> value == null)
                || capabilities.stream().map(CustomerServiceCapability::code).distinct().count() != capabilities.size()) {
            throw new ServiceException("INVALID_INPUT: 最多配置 5 项能力，编码不能重复", 400);
        }
        capabilities = List.copyOf(capabilities);
    }

    private static String text(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit) {
            throw new ServiceException("INVALID_INPUT: 客服配置字段为空或超出长度限制", 400);
        }
        return value.strip();
    }
}
