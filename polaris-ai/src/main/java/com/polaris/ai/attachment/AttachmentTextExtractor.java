package com.polaris.ai.attachment;

import java.nio.file.Path;

public interface AttachmentTextExtractor {
    String extract(Path path, String originalName);
}
