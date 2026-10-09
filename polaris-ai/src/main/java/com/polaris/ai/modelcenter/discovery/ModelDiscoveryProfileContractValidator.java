package com.polaris.ai.modelcenter.discovery;

import com.polaris.ai.modelcenter.schema.SchemaProfileDefinition;
import com.polaris.ai.modelcenter.schema.SchemaProfileRegistry;
import org.springframework.stereotype.Component;

/** 保证声明支持模型发现的 Provider Profile 有对应协议发现实现。 */
@Component
public class ModelDiscoveryProfileContractValidator {

    public ModelDiscoveryProfileContractValidator(
            SchemaProfileRegistry profileRegistry,
            RemoteModelDiscoveryRegistry discoveryRegistry) {
        profileRegistry.list().stream()
                .filter(profile -> profile.layer()
                        == SchemaProfileDefinition.ProfileLayer.PROVIDER)
                .filter(SchemaProfileDefinition::modelDiscoverySupported)
                .forEach(profile -> discoveryRegistry.getRequired(
                        profile.protocolCode()));
    }
}
