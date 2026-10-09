package com.polaris.ai.modelcenter.schema;

import java.util.List;
import java.util.Optional;

/** 运行时不读取 classpath 的不可变 Capability Schema Registry。 */
public interface CapabilitySchemaRegistry {

    Optional<CapabilitySchemaDefinition> find(String code, int version);

    CapabilitySchemaDefinition getRequired(String code, int version);

    Optional<CapabilitySchemaDefinition> findLatest(String code);

    CapabilitySchemaDefinition getLatestRequired(String code);

    List<CapabilitySchemaDefinition> list();
}
