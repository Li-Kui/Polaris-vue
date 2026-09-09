package com.polaris.ai.core.context;

import org.springframework.core.task.TaskDecorator;

/**
 * 异步线程 CallerContext 自动传播装饰器。
 * 配置到线程池后，SSE 等异步任务能自动获取提交线程的调用者上下文，
 * 并在执行结束后恢复工作线程原有上下文。
 */
public class CallerContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        CallerContext capturedContext = CallerContextHolder.get();
        return () -> {
            CallerContext previousContext = CallerContextHolder.get();
            try {
                CallerContextHolder.clear();
                CallerContextHolder.set(capturedContext);
                runnable.run();
            } finally {
                CallerContextHolder.clear();
                CallerContextHolder.set(previousContext);
            }
        };
    }
}
