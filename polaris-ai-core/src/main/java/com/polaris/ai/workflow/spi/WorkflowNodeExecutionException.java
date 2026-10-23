package com.polaris.ai.workflow.spi;

/**
 * 可信节点处理器提供的安全失败信息，不得携带远端响应或凭证。
 * notDispatched 仅能在确认尚未发出业务请求时使用；不能由远端只读注解推断。
 */
public final class WorkflowNodeExecutionException extends RuntimeException {
    private final String code;
    private final boolean notDispatched;

    public WorkflowNodeExecutionException(String code, String safeMessage, boolean notDispatched) {
        super(safeMessage);
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("节点错误码无效");
        }
        this.code = code;
        this.notDispatched = notDispatched;
    }

    public String code() { return code; }

    public boolean notDispatched() { return notDispatched; }
}
