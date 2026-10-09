package com.polaris.ai.modelcenter.protocol;

import com.polaris.ai.modelcenter.schema.SchemaProfileDefinition;
import com.polaris.ai.modelcenter.schema.SchemaProfileRegistry;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** 保证 classpath 中声明的每个 Protocol Profile 都有代码 Adapter。 */
@Component
public class ProtocolProfileContractValidator {

    private static final Set<String> SECRET_CONFIG_TOKENS = Set.of(
            "apikey", "secretkey", "token", "password", "authorization",
            "credential", "header", "accesskey", "secretaccesskey");

    public ProtocolProfileContractValidator(
            SchemaProfileRegistry profileRegistry,
            ProtocolAdapterRegistry adapterRegistry) {
        profileRegistry.list().forEach(profile -> {
            if (profile.layer() == SchemaProfileDefinition.ProfileLayer.PROTOCOL) {
                adapterRegistry.getRequired(profile.code());
                return;
            }
            if (profile.layer() != SchemaProfileDefinition.ProfileLayer.PROVIDER) {
                return;
            }
            ProtocolAdapter adapter = adapterRegistry.getRequired(
                    profile.protocolCode());
            profile.supportedCapabilities().forEach(capability -> {
                if (!adapter.mappedCapabilities().contains(capability)) {
                    throw new IllegalStateException(
                            "Provider Profile 声明了协议未映射的 Capability: "
                                    + profile.code() + "/" + capability);
                }
            });
            if (profile.modelDiscoverySupported()
                    && adapter.modelDiscoveryEndpoint().isEmpty()) {
                throw new IllegalStateException(
                        "Provider Profile 声明支持模型发现，但协议无发现端点: "
                                + profile.code());
            }
            validateDefaultBaseUrl(profile);
            validateConnectionConfigKeys(profile.connectionConfigSchema(), "$", 0);
        });
    }

    private void validateDefaultBaseUrl(SchemaProfileDefinition profile) {
        if (profile.defaultBaseUrl() == null || profile.defaultBaseUrl().isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(profile.defaultBaseUrl()).normalize();
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Provider Profile defaultBaseUrl 格式无效: "
                            + profile.code());
        }
    }

    private void validateConnectionConfigKeys(
            com.polaris.ai.modelcenter.schema.SchemaNode schema,
            String path,
            int depth) {
        if (schema == null || depth > 5) {
            throw new IllegalStateException(
                    "Provider connectionConfigSchema 嵌套过深: " + path);
        }
        schema.properties().forEach((field, child) -> {
            String normalized = field.replaceAll("[^A-Za-z0-9]", "")
                    .toLowerCase(Locale.ROOT);
            if (SECRET_CONFIG_TOKENS.stream().anyMatch(normalized::contains)) {
                throw new IllegalStateException(
                        "Provider connectionConfigSchema 禁止声明敏感字段: "
                                + path + "." + field);
            }
            if (child.type()
                    == com.polaris.ai.modelcenter.schema.SchemaNode.ValueType.OBJECT) {
                validateConnectionConfigKeys(
                        child, path + "." + field, depth + 1);
            }
        });
    }
}
