package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.polaris.ai.domain.AiAgent;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.workflow.langgraph.GraphTopology;
import lombok.Data;
import org.springframework.aop.support.AopUtils;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** 工作流拓扑及其运行依赖的非敏感不可变快照。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowExecutionSnapshot {

    public static final int CURRENT_SCHEMA_VERSION = 3;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private GraphTopology topology;
    private Map<String, AgentSnapshot> agents = new LinkedHashMap<>();
    private Map<Long, ModelSnapshot> models = new LinkedHashMap<>();
    private Map<String, ImplementationSnapshot> javaExecutors = new LinkedHashMap<>();
    private Map<String, ImplementationSnapshot> tools = new LinkedHashMap<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImplementationSnapshot {
        private String className;
        private String sha256;

        public static ImplementationSnapshot from(Object implementation) {
            if (implementation == null) {
                throw new IllegalArgumentException("实现实例不能为空");
            }
            Class<?> targetClass = AopUtils.getTargetClass(implementation);
            if (targetClass == null) {
                targetClass = implementation.getClass();
            }
            ImplementationSnapshot snapshot = new ImplementationSnapshot();
            snapshot.setClassName(targetClass.getName());
            snapshot.setSha256(classSha256(targetClass));
            return snapshot;
        }

        public boolean matches(Object implementation) {
            if (implementation == null) {
                return false;
            }
            ImplementationSnapshot current = from(implementation);
            return current.getClassName().equals(className) && current.getSha256().equals(sha256);
        }

        private static String classSha256(Class<?> type) {
            String resourceName = "/" + type.getName().replace('.', '/') + ".class";
            try (InputStream input = type.getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new IllegalStateException("无法读取实现类字节码: " + type.getName());
                }
                return HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("无法计算实现类指纹: " + type.getName(), e);
            }
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AgentSnapshot {
        private String agentCode;
        private String agentName;
        private String systemPrompt;
        private String tools;
        private Long modelConfigId;
        private Double temperature;

        public static AgentSnapshot from(AiAgent agent, Long resolvedModelConfigId) {
            AgentSnapshot snapshot = new AgentSnapshot();
            snapshot.setAgentCode(agent.getAgentCode());
            snapshot.setAgentName(agent.getAgentName());
            snapshot.setSystemPrompt(agent.getSystemPrompt());
            snapshot.setTools(agent.getTools());
            snapshot.setModelConfigId(resolvedModelConfigId);
            snapshot.setTemperature(agent.getTemperature());
            return snapshot;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelSnapshot {
        private Long id;
        private String name;
        private String provider;
        private String modelName;
        private String baseUrl;
        private Integer maxTokens;
        private Double temperature;
        private String enableThinking;
        private String reasoningEffort;
        private String enableSearch;
        private String enabledTools;

        public static ModelSnapshot from(AiModelConfig config) {
            ModelSnapshot snapshot = new ModelSnapshot();
            snapshot.setId(config.getId());
            snapshot.setName(config.getName());
            snapshot.setProvider(config.getProvider());
            snapshot.setModelName(config.getModelName());
            snapshot.setBaseUrl(config.getBaseUrl());
            snapshot.setMaxTokens(config.getMaxTokens());
            snapshot.setTemperature(config.getTemperature());
            snapshot.setEnableThinking(config.getEnableThinking());
            snapshot.setReasoningEffort(config.getReasoningEffort());
            snapshot.setEnableSearch(config.getEnableSearch());
            snapshot.setEnabledTools(config.getEnabledTools());
            return snapshot;
        }

        public AiModelConfig mergeSecrets(AiModelConfig liveConfig, Double temperatureOverride) {
            AiModelConfig merged = new AiModelConfig();
            merged.setId(id);
            merged.setName(name);
            merged.setProvider(provider);
            merged.setModelName(modelName);
            merged.setBaseUrl(baseUrl);
            merged.setMaxTokens(maxTokens);
            merged.setTemperature(temperatureOverride != null ? temperatureOverride : temperature);
            merged.setEnableThinking(enableThinking);
            merged.setReasoningEffort(reasoningEffort);
            merged.setEnableSearch(enableSearch);
            merged.setEnabledTools(enabledTools);
            if (liveConfig != null) {
                merged.setApiKey(liveConfig.getApiKey());
                merged.setSearchKey(liveConfig.getSearchKey());
            }
            return merged;
        }
    }
}
