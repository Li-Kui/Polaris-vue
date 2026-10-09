package com.polaris.ai.runtime;

/** Runtime 观测扩展点。实现不得读取业务请求正文。 */
@FunctionalInterface
public interface ModelExecutionObserver {

    void onTerminal(ModelExecutionObservation observation);
}
