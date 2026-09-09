package com.polaris.ai.safety.dto;

import java.nio.file.Path;

public record ResolvedAttachment(
        String token,
        Path path,
        String originalName,
        String mediaType
) {}
