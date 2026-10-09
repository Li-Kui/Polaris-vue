package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
import java.util.Set;

/** 运行时参数是否可被上层来源覆盖。 */
public record ParameterPolicyDefinition(
        boolean overridable,
        Set<ParameterSource> allowedSources) {

    public ParameterPolicyDefinition {
        allowedSources = allowedSources == null ? Set.of() : Set.copyOf(allowedSources);
    }

    public enum ParameterSource {
        APPLICATION,
        AGENT,
        WORKFLOW,
        REQUEST;

        @JsonCreator
        public static ParameterSource fromJson(String value) {
            return value == null ? null
                    : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String toJson() {
            return name();
        }
    }
}
