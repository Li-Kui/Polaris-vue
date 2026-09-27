package com.polaris.ai.modelcenter.modeltest;

import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/** 启动期校验并冻结的 Capability Test Handler Registry。 */
@Component
public class DefaultCapabilityTestHandlerRegistry
        implements CapabilityTestHandlerRegistry {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    private final Map<String, CapabilityTestHandler> handlers;

    public DefaultCapabilityTestHandlerRegistry(
            List<CapabilityTestHandler> candidates) {
        Map<String, CapabilityTestHandler> registered = new LinkedHashMap<>();
        for (CapabilityTestHandler handler : candidates) {
            validate(handler);
            String code = normalize(handler.capabilityCode());
            if (registered.putIfAbsent(code, handler) != null) {
                throw new IllegalStateException(
                        "重复 Capability Test Handler: " + code);
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    @Override
    public CapabilityTestHandler getRequired(String capabilityCode) {
        return find(capabilityCode).orElseThrow(() -> new ServiceException(
                "CAPABILITY_TEST_NOT_SUPPORTED: " + normalize(capabilityCode)));
    }

    @Override
    public Optional<CapabilityTestHandler> find(String capabilityCode) {
        if (capabilityCode == null || capabilityCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(normalize(capabilityCode)));
    }

    private void validate(CapabilityTestHandler handler) {
        if (handler == null) {
            throw new IllegalStateException(
                    "Capability Test Handler 不能为空");
        }
        String code = normalize(handler.capabilityCode());
        if (!CODE_PATTERN.matcher(code).matches()
                || !code.equals(handler.capabilityCode())) {
            throw new IllegalStateException(
                    "Capability Test Handler Code 必须是规范大写标识");
        }
    }

    private String normalize(String value) {
        return value == null ? "null"
                : value.trim().toUpperCase(Locale.ROOT);
    }
}
