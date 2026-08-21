package com.polaris.ai.core.spi;

/**
 * 操作日志提供者 SPI 接口。
 * 替代直接依赖 ISysOperLogService。
 */
public interface OperationLogProvider {

    /**
     * 记录操作日志
     *
     * @param module 模块名
     * @param action 操作类型
     * @param detail 操作详情
     */
    void recordLog(String module, String action, String detail);
}
