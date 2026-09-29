package com.polaris.ai.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.util.List;

/** 模型服务出站 HTTP 配置，避免运行环境的全局代理意外改变请求链路。 */
@Data
@Component
@ConfigurationProperties(prefix = "model-center.http")
public class ModelHttpClientProperties {

    private static final ProxySelector DIRECT_PROXY_SELECTOR =
            new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(Proxy.NO_PROXY);
                }

                @Override
                public void connectFailed(
                        URI uri, SocketAddress address, IOException failure) {
                    // 直连模式没有代理节点需要标记失败。
                }
            };

    /** 默认直连，SYSTEM 仅在部署环境明确需要系统代理时启用。 */
    private ProxyMode proxyMode = ProxyMode.DIRECT;

    /** FIXED 模式使用的代理主机。 */
    private String proxyHost;

    /** FIXED 模式使用的代理端口。 */
    private Integer proxyPort;

    public HttpClient.Builder applyProxy(HttpClient.Builder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("HTTP Client Builder 不能为空");
        }
        ProxyMode mode = proxyMode == null ? ProxyMode.DIRECT : proxyMode;
        return switch (mode) {
            case DIRECT -> builder.proxy(DIRECT_PROXY_SELECTOR);
            case SYSTEM -> builder;
            case FIXED -> builder.proxy(ProxySelector.of(fixedProxyAddress()));
        };
    }

    private InetSocketAddress fixedProxyAddress() {
        if (proxyHost == null || proxyHost.isBlank()
                || proxyPort == null || proxyPort < 1 || proxyPort > 65535) {
            throw new IllegalStateException(
                    "FIXED 代理模式必须配置有效的 proxy-host 和 proxy-port");
        }
        return InetSocketAddress.createUnresolved(proxyHost.trim(), proxyPort);
    }

    public enum ProxyMode {
        DIRECT,
        SYSTEM,
        FIXED
    }
}
