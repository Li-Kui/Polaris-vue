package com.polaris.platform.web;

import com.polaris.platform.service.CustomerServiceRealtimeSpeechService;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tools.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/** 默认同源握手；第一帧须持有经管理员或专属访客 HTTP 鉴权签发且绑定 Origin 的一次性票据。 */
@Configuration
@EnableWebSocket
public class CustomerSpeechWebSocketConfig implements WebSocketConfigurer {
    private final CustomerServiceRealtimeSpeechService streams;
    private final ObjectMapper json;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final Semaphore sockets = new Semaphore(16);
    private final ExecutorService workers = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), task -> {
        var thread = new Thread(task, "customer-realtime-finish"); thread.setDaemon(true); return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "customer-realtime-handshake"); thread.setDaemon(true); return thread;
    });
    public CustomerSpeechWebSocketConfig(CustomerServiceRealtimeSpeechService streams, ObjectMapper json) {
        this.streams = streams; this.json = json;
        cleanup.scheduleWithFixedDelay(() -> {
            for (var connection : connections.values()) if (connection.live == null && System.currentTimeMillis() - connection.created > 10000) connection.close();
        }, 1, 1, TimeUnit.SECONDS);
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 不设置跨来源允许项，沿用 Spring WebSocket 默认同源校验；票据再绑定同一个 Origin。
        registry.addHandler(new Handler(), "/platform/runtime/customer-speech-stream").addInterceptors(new HandshakeInterceptor() {
            @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attributes) {
                String origin = request.getHeaders().getOrigin();
                try {
                    if (request.getURI().getRawQuery() != null || origin == null || !java.net.URI.create(origin).isAbsolute()) throw new IllegalArgumentException();
                } catch (RuntimeException invalid) { response.setStatusCode(HttpStatus.FORBIDDEN); return false; }
                attributes.put("origin", origin); return true;
            }
            @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception failure) { }
        });
    }
    final class Connection {
        final WebSocketSession socket;
        final long created = System.currentTimeMillis();
        volatile CustomerServiceRealtimeSpeechService.Stream live;
        boolean initialized;
        Connection(WebSocketSession socket) { this.socket = new ConcurrentWebSocketSessionDecorator(socket, 3000, 65536); }
        void event(Map<String, Object> value) {
            try {
                socket.sendMessage(new TextMessage(json.writeValueAsString(value)));
                if (Set.of("COMPLETE", "ERROR").contains(value.get("type"))) close();
            } catch (Exception failed) { close(); throw new IllegalStateException("实时连接已关闭"); }
        }
        void close() {
            if (connections.remove(socket.getId(), this)) {
                try { if (live != null) live.close(); } catch (RuntimeException ignored) { }
                finally { sockets.release(); }
            }
            try { if (socket.isOpen()) socket.close(CloseStatus.NORMAL); } catch (Exception ignored) { }
        }
    }
    final class Handler extends AbstractWebSocketHandler {
        @Override public void afterConnectionEstablished(WebSocketSession socket) throws Exception {
            socket.setBinaryMessageSizeLimit(6404); socket.setTextMessageSizeLimit(2048);
            if (!sockets.tryAcquire()) { socket.close(CloseStatus.POLICY_VIOLATION); return; }
            connections.put(socket.getId(), new Connection(socket));
        }
        @Override protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
            var connection = connections.get(socket.getId()); if (connection == null) return;
            try {
                var value = json.readTree(message.getPayload()); String type = value.path("type").asText();
                if (!value.isObject()) throw new IllegalArgumentException();
                if (!connection.initialized) {
                    if (!"INIT".equals(type) || value.size() != 2 || !value.path("ticket").isTextual() || value.path("ticket").asText().length() != 43) throw new IllegalArgumentException();
                    connection.initialized = true;
                    connection.live = streams.open(value.path("ticket").asText(), Objects.toString(socket.getAttributes().get("origin"), ""), connection::event);
                    if (!socket.isOpen() || !connections.containsKey(socket.getId())) connection.live.close();
                    else connection.event(Map.of("type", "READY"));
                } else if ("FINISH".equals(type) && value.size() == 2 && value.path("sequence").isIntegralNumber() && connection.live != null) {
                    int sequence = value.path("sequence").asInt(-1);
                    workers.execute(() -> { try { connection.live.finish(sequence); } catch (RuntimeException failed) { connection.close(); } });
                } else if ("CANCEL".equals(type) && value.size() == 1) connection.close();
                else throw new IllegalArgumentException();
            } catch (Exception invalid) { connection.event(Map.of("type", "ERROR", "code", "SPEECH_INTERRUPTED")); }
        }
        @Override protected void handleBinaryMessage(WebSocketSession socket, BinaryMessage message) {
            var connection = connections.get(socket.getId()); if (connection == null) return;
            try {
                ByteBuffer value = message.getPayload();
                if (!message.isLast() || connection.live == null || value.remaining() < 6 || value.remaining() > 6404) throw new IllegalArgumentException();
                int sequence = value.getInt(); byte[] pcm = new byte[value.remaining()]; value.get(pcm); connection.live.audio(sequence, pcm);
            } catch (RuntimeException failed) { connection.close(); }
        }
        @Override public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) { var value = connections.get(socket.getId()); if (value != null) value.close(); }
        @Override public void handleTransportError(WebSocketSession socket, Throwable error) { var value = connections.get(socket.getId()); if (value != null) value.close(); }
    }
    @PreDestroy void shutdown() { cleanup.shutdownNow(); workers.shutdownNow(); for (var value : connections.values()) value.close(); }
}
