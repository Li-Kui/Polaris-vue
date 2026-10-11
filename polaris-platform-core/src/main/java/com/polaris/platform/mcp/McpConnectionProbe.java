package com.polaris.platform.mcp;

import com.polaris.common.exception.ServiceException;
import com.polaris.platform.connector.ConnectorCredentialCipher;
import com.polaris.platform.domain.PlatformMcpConnection;
import com.polaris.platform.dto.McpConnectionProbeResult;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/** 测试、发现与受控调用复用独占会话，不缓存客户端或凭证。 */
@Component
public class McpConnectionProbe {
    private static final List<String> PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-11-25");
    private static final int MAX_MESSAGE_BYTES = 256 * 1024;
    private static final int MAX_DISCOVERY_BYTES = 1024 * 1024;
    private static final int MAX_TOOLS = 200;
    private static final int MAX_PAGES = 20;
    private static final JsonMapper JSON_MAPPER = JsonMapper.shared();

    private final McpConnectionPolicy policy;
    private final ConnectorCredentialCipher cipher;
    private final Semaphore probeSlots = new Semaphore(16);

    public McpConnectionProbe(McpConnectionPolicy policy, ConnectorCredentialCipher cipher) {
        this.policy = policy;
        this.cipher = cipher;
    }

    public McpConnectionProbeResult probe(PlatformMcpConnection connection, boolean discoverTools) {
        return withSession(connection, discoverTools, false, (client, initialized, tools, deadline, started) -> {
            McpSchema.Implementation server = initialized.serverInfo();
            return new McpConnectionProbeResult(initialized.protocolVersion(),
                    server == null ? null : server.name(), server == null ? null : server.version(),
                    initialized.capabilities() != null && initialized.capabilities().tools() != null,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), tools);
        });
    }

    <T> T withSession(PlatformMcpConnection connection, boolean discoverTools, boolean invocation,
            SessionOperation<T> operation) {
        if (!probeSlots.tryAcquire()) {
            if (invocation) throw new McpInvocationException(McpInvocationException.Code.RATE_LIMITED);
            throw new ServiceException("MCP测试/发现正在繁忙，请稍后重试");
        }
        try {
            return performSession(connection, discoverTools, invocation, operation);
        } finally {
            probeSlots.release();
        }
    }

    private <T> T performSession(PlatformMcpConnection connection, boolean discoverTools, boolean invocation,
            SessionOperation<T> operation) {
        long started = System.nanoTime();
        URI endpoint = policy.validate(connection);
        long deadline = started + TimeUnit.MILLISECONDS.toNanos(connection.getTimeoutMs());
        String secret = credential(connection);
        Duration timeout = Duration.ofMillis(connection.getTimeoutMs());
        Sinks.One<String> sessionProtocol = Sinks.one();
        McpAsyncClient client = null;
        try (McpManagedHttpClient.ManagedBuilder httpBuilder = new McpManagedHttpClient.ManagedBuilder()) {
            try {
                HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                        .builder(endpoint.toString()).endpoint(endpoint.toString())
                        .clientBuilder(httpBuilder)
                        .connectTimeout(Duration.ofMillis(Math.min(connection.getTimeoutMs(), 10000)))
                        .jsonMapper(invocation ? new McpStrictJsonMapper(JSON_MAPPER) : new JacksonMcpJsonMapper(JSON_MAPPER))
                        .openConnectionOnStartup(false).resumableStreams(false)
                        .supportedProtocolVersions(PROTOCOL_VERSIONS)
                        .maxResponseSize(MAX_MESSAGE_BYTES)
                        .asyncHttpRequestCustomizer((builder, method, target, body, context) -> {
                            // SDK 收到初始化响应头即建立 GET 流，早于协商结果解析。
                            // 延后 GET，确保旧协议服务不会收到最新版本的会话头。
                            Mono<String> version = "GET".equals(method) ? sessionProtocol.asMono()
                                    : Mono.just(builder.build().headers().firstValue("MCP-Protocol-Version")
                                            .orElse("2025-11-25"));
                            return version.flatMap(protocol -> Mono.fromCallable(() -> {
                                if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) {
                                    throw new IllegalStateException("MCP_PROBE_CANCELLED");
                                }
                                // SDK 的 POST / GET / DELETE 均经过该回调，不跟随重定向。
                                if (!endpoint.equals(target) || !endpoint.equals(policy.validate(connection))) {
                                    throw new IllegalStateException("MCP_ENDPOINT_CHANGED");
                                }
                                builder.timeout(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())));
                                builder.setHeader("MCP-Protocol-Version", protocol);
                                if ("BEARER".equals(connection.getAuthType())) {
                                    builder.setHeader("Authorization", "Bearer " + secret);
                                } else if ("API_KEY".equals(connection.getAuthType())) {
                                    builder.setHeader(connection.getCredentialHeaderName(), secret);
                                }
                                return builder;
                            }).subscribeOn(Schedulers.boundedElastic()));
                        }).build();
                client = McpClient.async(transport).requestTimeout(timeout).initializationTimeout(timeout)
                        .clientInfo(new McpSchema.Implementation("Polaris", "3.9.2"))
                        .enableCallToolSchemaCaching(false).build();
                McpSchema.InitializeResult initialized = await(client.initialize(), deadline);
                if (!PROTOCOL_VERSIONS.contains(initialized.protocolVersion())) {
                    throw new ServiceException("MCP服务协商了不支持的协议版本");
                }
                sessionProtocol.tryEmitValue(initialized.protocolVersion());
                boolean toolsSupported = initialized.capabilities() != null
                        && initialized.capabilities().tools() != null;
                List<McpConnectionProbeResult.ToolPreview> tools = discoverTools && toolsSupported
                        ? discover(client, deadline) : List.of();
                return operation.execute(client, initialized, tools, deadline, started);
            } finally {
                sessionProtocol.tryEmitEmpty();
                if (client != null) {
                    try {
                        // DELETE 尝试关闭远端会话，不能无限延长业务请求。
                        Mono<Void> closing = client.closeGracefully();
                        McpSchema.InitializeResult initialized = client.getCurrentInitializationResult();
                        if (initialized != null) {
                            closing = closing.contextWrite(context -> context.put(
                                    McpAsyncClient.NEGOTIATED_PROTOCOL_VERSION, initialized.protocolVersion()));
                        }
                        closing.block(Duration.ofMillis(300));
                    } catch (Exception ignored) {
                        // 后续资源 close 总是取消未完成的 future/SSE subscription。
                    }
                }
            }
        } catch (McpInvocationException e) {
            if (!invocation) throw new ServiceException("MCP连接失败，请检查端点、凭证、协议版本或远端服务状态");
            throw e;
        } catch (ServiceException e) {
            if (invocation) throw new McpInvocationException(McpInvocationException.Code.PROTOCOL_ERROR);
            throw e;
        } catch (TimeoutException e) {
            if (invocation) throw new McpInvocationException(McpInvocationException.Code.TIMEOUT);
            throw new ServiceException("MCP测试/发现超时，底层请求已取消");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (invocation) throw new McpInvocationException(McpInvocationException.Code.CANCELLED);
            throw new ServiceException("MCP测试/发现已取消");
        } catch (Exception e) {
            // 不把 SDK 异常原文/远端原文/认证头带到接口和通用异常日志。
            Throwable cause = e;
            for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
                if (invocation && cause instanceof McpInvocationException safe) throw safe;
                // SDK的尺寸异常是包私有类型，只匹配已核对的类名，不解析可能含载荷的错误文本。
                if (invocation && "io.modelcontextprotocol.client.transport.MaxSizeExceededException".equals(cause.getClass().getName())) {
                    throw new McpInvocationException(McpInvocationException.Code.RESULT_TOO_LARGE);
                }
                if (invocation && cause instanceof io.modelcontextprotocol.spec.McpError) {
                    throw new McpInvocationException(McpInvocationException.Code.JSON_RPC_ERROR);
                }
                if (cause instanceof TimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
                    if (invocation) throw new McpInvocationException(McpInvocationException.Code.TIMEOUT);
                    throw new ServiceException("MCP测试/发现超时，底层请求已取消");
                }
            }
            if (invocation) throw new McpInvocationException(McpInvocationException.Code.TRANSPORT_ERROR);
            throw new ServiceException("MCP连接失败，请检查端点、凭证、协议版本或远端服务状态");
        }
    }

    private List<McpConnectionProbeResult.ToolPreview> discover(McpAsyncClient client, long deadline)
            throws ExecutionException, InterruptedException, TimeoutException {
        List<McpConnectionProbeResult.ToolPreview> tools = new ArrayList<>();
        Set<String> cursors = new HashSet<>();
        Set<String> names = new HashSet<>();
        String cursor = null;
        int totalBytes = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            McpSchema.ListToolsResult result = await(client.listTools(cursor), deadline);
            if (result.tools() == null || result.tools().size() + tools.size() > MAX_TOOLS) {
                throw new ServiceException("MCP工具数量无效或超过200个预览上限");
            }
            for (McpSchema.Tool tool : result.tools()) {
                if (tool.name() == null || tool.name().isBlank() || tool.name().length() > 256
                        || !names.add(tool.name()) || tool.inputSchema() == null) {
                    throw new ServiceException("MCP工具名称、Schema无效或存在重名");
                }
                McpSchema.ToolAnnotations annotations = tool.annotations();
                McpConnectionProbeResult.ToolPreview preview = new McpConnectionProbeResult.ToolPreview(
                        tool.name(), tool.title(), tool.description(), tool.inputSchema(), tool.outputSchema(),
                        annotations == null ? null : annotations.readOnlyHint(),
                        annotations == null ? null : annotations.destructiveHint(),
                        JSON_MAPPER.convertValue(tool, new TypeReference<java.util.Map<String, Object>>() {}));
                totalBytes += JSON_MAPPER.writeValueAsBytes(preview).length;
                if (totalBytes > MAX_DISCOVERY_BYTES) {
                    throw new ServiceException("MCP工具预览超过1MiB总大小上限");
                }
                tools.add(preview);
            }
            cursor = result.nextCursor();
            if (cursor == null || cursor.isBlank()) {
                return List.copyOf(tools);
            }
            if (!cursors.add(cursor)) {
                throw new ServiceException("MCP服务返回了重复分页游标");
            }
        }
        throw new ServiceException("MCP工具发现超过20页上限");
    }

    private String credential(PlatformMcpConnection connection) {
        if ("NONE".equals(connection.getAuthType())) {
            return null;
        }
        if (!cipher.isEncrypted(connection.getAuthConfig())) {
            throw new ServiceException("MCP凭证未配置或格式无效，请重新保存凭证");
        }
        String secret = cipher.decrypt(connection.getAuthConfig());
        policy.validateSecret(secret);
        return secret;
    }

    static <T> T await(Mono<T> operation, long deadline)
            throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<T> future = operation.toFuture();
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new TimeoutException();
            }
            return future.get(remaining, TimeUnit.NANOSECONDS);
        } finally {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    @FunctionalInterface
    interface SessionOperation<T> {
        T execute(McpAsyncClient client, McpSchema.InitializeResult initialized,
                List<McpConnectionProbeResult.ToolPreview> tools, long deadline, long started) throws Exception;
    }
}
