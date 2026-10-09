package com.polaris.ai.modelcenter.runtime;

import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** 第一版只激活调用方明确推导出的 Feature，不把 enabled 当作 active。 */
@Component
public class DefaultFeatureActivationResolver
        implements FeatureActivationResolver {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    @Override
    public Set<String> resolve(
            ResolvedModelDefinition definition,
            Set<String> requestedFeatures) {
        if (definition == null) {
            throw new ServiceException("MODEL_DEFINITION_REQUIRED");
        }
        Set<String> active = new TreeSet<>();
        definition.enabledFeatures().forEach((code, feature) -> {
            var parameters = feature.parameters();
            String policy = parameters == null ? "ON_REQUEST"
                    : parameters.path("activationPolicy")
                    .asText("ON_REQUEST");
            if ("ALWAYS".equalsIgnoreCase(policy)) {
                active.add(code);
            }
        });
        for (String requested : requestedFeatures == null
                ? Set.<String>of() : requestedFeatures) {
            String feature = normalize(requested);
            if (!definition.enabledFeatures().containsKey(feature)) {
                throw new ServiceException("FEATURE_NOT_ENABLED: " + feature);
            }
            active.add(feature);
        }
        return Set.copyOf(active);
    }

    private String normalize(String value) {
        String normalized = value == null
                ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new ServiceException("FEATURE_NOT_ENABLED");
        }
        return normalized;
    }
}
