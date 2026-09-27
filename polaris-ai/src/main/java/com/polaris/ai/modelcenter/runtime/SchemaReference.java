package com.polaris.ai.modelcenter.runtime;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 一个已解析 Capability 的不可变 Runtime Schema 引用。 */
public record SchemaReference(
        String capabilityCode,
        int schemaVersion,
        String schemaHash) {

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern CODE =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    public SchemaReference {
        capabilityCode = Objects.requireNonNull(capabilityCode,
                "capabilityCode").trim().toUpperCase(Locale.ROOT);
        schemaHash = Objects.requireNonNull(schemaHash, "schemaHash").trim();
        schemaHash = schemaHash.toLowerCase(Locale.ROOT);
        if (!CODE.matcher(capabilityCode).matches()
                || schemaVersion < 1
                || !SHA256.matcher(schemaHash).matches()) {
            throw new IllegalArgumentException("Runtime Schema 引用无效");
        }
    }
}
