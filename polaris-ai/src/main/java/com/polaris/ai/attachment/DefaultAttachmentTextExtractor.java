package com.polaris.ai.attachment;

import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class DefaultAttachmentTextExtractor implements AttachmentTextExtractor {

    @Override
    public String extract(Path path, String originalName) {
        return AttachmentParserHelper.parse(path, originalName);
    }
}
