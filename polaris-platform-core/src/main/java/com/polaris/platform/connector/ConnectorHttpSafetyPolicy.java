package com.polaris.platform.connector;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** 拒绝可访问本机、私有网络或链路本地网络的连接器目标。 */
@Component
public class ConnectorHttpSafetyPolicy {

    private static final Pattern IP_LITERAL = Pattern.compile(
            "(?i)^(?:0x[0-9a-f]+|[0-9]+)(?:\\.(?:0x[0-9a-f]+|[0-9]+)){0,3}$");

    private final HostResolver hostResolver;

    public ConnectorHttpSafetyPolicy() {
        this(InetAddress::getAllByName);
    }

    ConnectorHttpSafetyPolicy(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    public URI validate(String value) {
        try {
            URI uri = URI.create(value).normalize();
            if (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("连接器URL仅允许HTTP或HTTPS");
            }
            if (uri.getUserInfo() != null || uri.getHost() == null) {
                throw new IllegalArgumentException("连接器URL格式无效");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.endsWith(".localhost")
                    || host.endsWith(".local") || host.endsWith(".internal")) {
                throw new IllegalArgumentException("连接器URL不能访问本地或内部域名");
            }
            InetAddress[] addresses = hostResolver.resolve(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("连接器域名无法解析");
            }
            boolean ipLiteral = isIpLiteral(host);
            for (InetAddress address : addresses) {
                if (!isPublic(address) && !isDnsProxyFakeAddress(address, ipLiteral)) {
                    throw new IllegalArgumentException("连接器URL解析到非公网地址");
                }
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("连接器URL校验失败");
        }
    }

    /**
     * DNS 代理的 Fake-IP 模式通常使用 198.18.0.0/15 映射公网域名。
     * 仅允许“域名解析结果”为该网段，用户直接填写该网段地址时仍然拒绝。
     */
    private boolean isDnsProxyFakeAddress(InetAddress address, boolean ipLiteral) {
        if (ipLiteral || !(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 198 && (second == 18 || second == 19);
    }

    private boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || IP_LITERAL.matcher(host).matches();
    }

    private boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return !(first == 0 || first == 100 && second >= 64 && second <= 127
                    || first == 169 && second == 254
                    || first == 192 && second == 0
                    || first == 192 && second == 88
                    || first == 198 && (second == 18 || second == 19 || second == 51)
                    || first == 203 && second == 0
                    || first >= 224);
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) != 0xfc;
        }
        return false;
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws Exception;
    }
}
