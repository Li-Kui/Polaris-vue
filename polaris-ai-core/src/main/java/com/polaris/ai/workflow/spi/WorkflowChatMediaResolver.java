package com.polaris.ai.workflow.spi;

import dev.langchain4j.data.message.ImageContent;

/** 解析服务端生成的聊天图片引用，不接受客户端文件路径或远程 URL。 */
public interface WorkflowChatMediaResolver {

    ImageContent resolveImage(String reference, WorkflowNodeContext context);
}
