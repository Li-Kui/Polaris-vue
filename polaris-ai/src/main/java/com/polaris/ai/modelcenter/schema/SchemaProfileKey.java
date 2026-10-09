package com.polaris.ai.modelcenter.schema;

import java.util.Locale;
import java.util.Objects;

/** Profile 的稳定层级、代码和版本键。 */
public record SchemaProfileKey(
        SchemaProfileDefinition.ProfileLayer layer,
        String code,
        int version) {

    public SchemaProfileKey {
        layer = Objects.requireNonNull(layer, "layer");
        code = Objects.requireNonNull(code, "code").trim().toUpperCase(Locale.ROOT);
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }
}
