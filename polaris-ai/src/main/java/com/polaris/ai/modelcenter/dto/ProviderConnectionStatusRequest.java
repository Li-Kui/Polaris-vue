package com.polaris.ai.modelcenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 修改 Provider Connection 状态的请求。 */
public record ProviderConnectionStatusRequest(
        @NotBlank
        @Size(max = 1)
        String status,

        @NotNull
        @Positive
        Long expectedRevision
) {}
