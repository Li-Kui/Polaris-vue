package com.polaris.ai.safety.dto;

public record PrivateAttachmentUploadResult(
        String token,
        String name,
        long size,
        String mediaType
) {}
