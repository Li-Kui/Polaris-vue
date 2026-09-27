package com.polaris.ai.modelcenter.modeltest;

/** 单一 Capability 的真实模型测试入口；具体 Handler 在 Runtime 阶段实现。 */
public interface CapabilityTestHandler {

    String capabilityCode();

    CapabilityTestResult test(ModelTestContext context);
}
