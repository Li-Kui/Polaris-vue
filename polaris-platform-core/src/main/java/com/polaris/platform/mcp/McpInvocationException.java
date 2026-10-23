package com.polaris.platform.mcp;

/** 只传播固定安全错误，不保留远端异常原因、认证头或请求内容。 */
public final class McpInvocationException extends RuntimeException {
    public enum Code {
        POLICY_CHANGED("执行准入、工具审批或连接已变化，请重新检查"),
        DEFINITION_CHANGED("远端工具定义已变化，请重新发现和审批"),
        INPUT_INVALID("调用参数不符合当前输入Schema或超过64KiB上限"),
        OUTPUT_INVALID("远端结构化结果不符合当前输出Schema"),
        RESULT_TOO_LARGE("远端响应超过256KiB上限"),
        RATE_LIMITED("MCP调用超过当前实例限流或并发上限"),
        AUTHENTICATION_FAILED("远端认证失败，请检查连接凭证"),
        HTTP_ERROR("远端HTTP请求失败"),
        JSON_RPC_ERROR("远端返回JSON-RPC错误"),
        PROTOCOL_ERROR("远端协议或结果格式不受支持"),
        TRANSPORT_ERROR("MCP网络连接失败"),
        TIMEOUT("调用超时，本地请求已取消，远端状态不确定"),
        CANCELLED("调用已取消，远端状态不确定"),
        TOOL_ERROR("远端工具返回业务错误"),
        INTERNAL_ERROR("调用处理失败，未自动重试");

        private final String message;
        Code(String message) { this.message = message; }
        public String message() { return message; }
    }

    private final Code code;

    public McpInvocationException(Code code) {
        super(code.message());
        this.code = code;
    }

    public Code code() { return code; }
}
