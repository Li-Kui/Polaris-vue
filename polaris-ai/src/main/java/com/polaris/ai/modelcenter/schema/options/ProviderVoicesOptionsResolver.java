package com.polaris.ai.modelcenter.schema.options;

import org.springframework.stereotype.Component;

import java.util.List;

/** Provider 音色白名单在后端维护，Vue 不感知 Provider 分支。 */
@Component
public class ProviderVoicesOptionsResolver implements SchemaOptionsResolver {

    @Override
    public String code() {
        return "PROVIDER_VOICES";
    }

    @Override
    public List<SchemaOption> resolve(OptionsResolveContext context) {
        String provider = context.providerCode() == null
                ? "" : context.providerCode().toUpperCase();
        return switch (provider) {
            case "OPENAI", "CUSTOM" -> options(
                    "alloy", "ash", "ballad", "coral", "echo",
                    "fable", "nova", "onyx", "sage", "shimmer", "verse");
            case "DASHSCOPE" -> options(
                    "Cherry", "Serena", "Ethan", "Chelsie");
            default -> List.of();
        };
    }

    private List<SchemaOption> options(String... values) {
        return java.util.Arrays.stream(values)
                .map(value -> new SchemaOption(value, value, false))
                .toList();
    }
}
