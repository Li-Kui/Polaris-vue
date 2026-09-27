package com.polaris.ai.modelcenter.schema;

import java.util.List;
import java.util.Optional;

/** 启动期编译完成后使用的不可变 Profile Registry。 */
public interface SchemaProfileRegistry {

    Optional<SchemaProfileDefinition> find(
            SchemaProfileDefinition.ProfileLayer layer,
            String code,
            int version);

    Optional<SchemaProfileDefinition> findLatest(
            SchemaProfileDefinition.ProfileLayer layer,
            String code);

    Optional<SchemaProfileDefinition> matchModel(
            String providerCode,
            String modelName);

    List<SchemaProfileDefinition> list();
}
