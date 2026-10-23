package com.polaris.ai.customer.application;

import com.polaris.common.exception.ServiceException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 客服能力的固定版本契约；不保存凭据，不允许任意工作流执行参数。 */
public record CustomerServiceCapability(
        String code, String name, String description, Long definitionId, String versionId,
        Map<String, String> inputSources, List<String> outputFields) {
    public static final Set<String> INPUT_SOURCES = Set.of("USER_INPUT", "PLATFORM_USER_ID", "TENANT_ID");

    public CustomerServiceCapability {
        if (code == null || !code.matches("[a-z][a-z0-9_]{0,31}")
                || name == null || name.isBlank() || name.length() > 64
                || description == null || description.isBlank() || description.length() > 300
                || definitionId == null || definitionId <= 0
                || versionId == null || !versionId.matches("[A-Za-z0-9_-]{1,64}")
                || inputSources == null || inputSources.size() > 10
                || inputSources.entrySet().stream().anyMatch(entry -> !field(entry.getKey())
                    || entry.getValue() == null || !INPUT_SOURCES.contains(entry.getValue()))
                || outputFields == null || outputFields.isEmpty() || outputFields.size() > 10
                || outputFields.stream().anyMatch(value -> !field(value))
                || new HashSet<>(outputFields).size() != outputFields.size()) {
            throw new ServiceException("INVALID_INPUT: 能力编码、版本、输入映射或输出字段无效", 400);
        }
        name = name.strip();
        description = description.strip();
        inputSources = Map.copyOf(inputSources);
        outputFields = List.copyOf(outputFields);
    }

    private static boolean field(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_]{0,63}");
    }
}
