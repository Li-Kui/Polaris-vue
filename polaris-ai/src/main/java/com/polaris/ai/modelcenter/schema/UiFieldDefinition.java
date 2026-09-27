package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.util.Locale;

/** 不参与 Runtime Schema Hash 的 UI 字段描述。 */
public record UiFieldDefinition(
        Component component,
        String customComponent,
        String optionsResolver,
        Integer order,
        Integer span,
        String group,
        String placeholder,
        BigDecimal step) {

    public enum Component {
        INPUT,
        NUMBER,
        SELECT,
        SLIDER,
        SWITCH,
        TAGS,
        CUSTOM;

        @JsonCreator
        public static Component fromJson(String value) {
            return value == null ? null
                    : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String toJson() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
