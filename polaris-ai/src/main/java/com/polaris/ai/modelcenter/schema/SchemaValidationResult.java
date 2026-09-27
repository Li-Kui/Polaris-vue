package com.polaris.ai.modelcenter.schema;

import java.util.List;

/** Schema 校验结果。 */
public record SchemaValidationResult(List<String> errors) {

    public SchemaValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
