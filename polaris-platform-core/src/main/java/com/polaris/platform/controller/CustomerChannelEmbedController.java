package com.polaris.platform.controller;

import com.polaris.platform.service.CustomerServiceChannelService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开的无资料 HTML 容器；前后端需同源反向代理，按渠道访问方式加载管理员页或独立访客页。 */
@RestController
@RequestMapping("/platform/runtime/customer-channels")
public class CustomerChannelEmbedController {
    private final CustomerServiceChannelService channels;
    public CustomerChannelEmbedController(CustomerServiceChannelService channels) { this.channels = channels; }

    @GetMapping(value = "/{code}/embed", produces = MediaType.TEXT_HTML_VALUE)
    public String embed(@PathVariable String code, HttpServletResponse response) {
        var origins = channels.embedOrigins(code);
        String entryPath = channels.visitorEmbed(code) ? "visitor" : "customer";
        // CSP frame-ancestors 校验完整祖先链；不从 Origin、Referer 或 URL 参数反射白名单。
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-src 'self'; style-src 'unsafe-inline'; frame-ancestors 'self'"
                + (origins.isEmpty() ? "" : " " + String.join(" ", origins)));
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Permissions-Policy", "microphone=(self), camera=()");
        response.setHeader("X-Content-Type-Options", "nosniff");
        return """
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1"><title>客服接待</title>
                <style>html,body,iframe{margin:0;width:100%%;height:100%%;border:0;display:block}</style></head>
                <body><iframe title="数字人客服文字接待" src="/platform/%s/%s" allow="microphone"
                sandbox="allow-scripts allow-same-origin allow-popups allow-popups-to-escape-sandbox"></iframe></body></html>
                """.formatted(entryPath, code);
    }
}
