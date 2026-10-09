package com.polaris.ai.modelcenter.schema;

import java.util.Locale;
import java.util.Objects;

/** Capability Schema 的稳定版本键。 */
public record CapabilitySchemaKey(String code, int version) {

    public CapabilitySchemaKey {
        code = Objects.requireNonNull(code, "code").trim().toUpperCase(Locale.ROOT);
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }
}
