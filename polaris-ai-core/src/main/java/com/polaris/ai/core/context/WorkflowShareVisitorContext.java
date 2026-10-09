package com.polaris.ai.core.context;

/** 分享聊天匿名访客的执行归属，只持久化会话哈希，不保存 Cookie。 */
public interface WorkflowShareVisitorContext extends CallerContext {

    String getVisitorSessionHash();
}
