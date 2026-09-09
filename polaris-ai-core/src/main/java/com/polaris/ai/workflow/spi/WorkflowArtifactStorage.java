package com.polaris.ai.workflow.spi;

/** 工作流产物内容存储接口，存储引用必须由实现内部进行安全校验。 */
public interface WorkflowArtifactStorage {

    void store(String storageRef, byte[] content);

    byte[] load(String storageRef);

    void delete(String storageRef);
}
