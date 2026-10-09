package com.polaris.ai.workflow.spi;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 在保持文本输入契约的同时，将已授权的私有图片转换为真正的多模态消息。 */
public final class WorkflowChatInputMedia {

    public static final String MARKER_PREFIX = "[polaris-share-image:";
    private static final Pattern IMAGE_REFERENCE = Pattern.compile(
            "\\[polaris-share-image:([a-f0-9]{32})]");

    private WorkflowChatInputMedia() {
    }

    public static UserMessage userMessage(
            String prompt, WorkflowNodeContext context, WorkflowChatMediaResolver resolver) {
        Matcher matcher = IMAGE_REFERENCE.matcher(prompt);
        Set<String> references = new LinkedHashSet<>();
        while (matcher.find()) references.add(matcher.group(1));
        if (references.isEmpty()) return UserMessage.from(prompt);
        if (!"SHARE".equals(context.principalType()) || resolver == null) {
            throw new IllegalArgumentException("当前执行不支持分享图片附件");
        }
        if (references.size() > 5) {
            throw new IllegalArgumentException("本次对话最多关联5张图片，请开始新对话");
        }
        ArrayList<Content> contents = new ArrayList<>();
        contents.add(TextContent.from(matcher.replaceAll("[已关联图片附件]")));
        for (String reference : references) {
            contents.add(resolver.resolveImage(reference, context));
        }
        return UserMessage.from(contents);
    }
}
