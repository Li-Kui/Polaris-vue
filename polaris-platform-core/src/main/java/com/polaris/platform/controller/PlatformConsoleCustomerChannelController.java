package com.polaris.platform.controller;

import com.polaris.common.core.domain.ResultData;
import com.polaris.platform.service.CustomerServiceChannelService;
import com.polaris.platform.service.CustomerServiceConversationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 管理和接待均验证当前真实租户管理员；渠道码不代替登录。 */
@RestController
@RequestMapping("/platform/console/customer-channels")
public class PlatformConsoleCustomerChannelController {
    private final CustomerServiceChannelService channels;
    private final CustomerServiceConversationService conversations;
    private final CustomerServiceRequestReader reader;
    public PlatformConsoleCustomerChannelController(CustomerServiceChannelService channels,
            CustomerServiceConversationService conversations, ObjectMapper json) {
        this.channels = channels;
        this.conversations = conversations;
        this.reader = new CustomerServiceRequestReader(json);
    }
    @ModelAttribute
    public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @GetMapping("/apps/{appId}")
    public ResultData<?> list(@PathVariable Long appId) { return ResultData.ok(channels.list(appId)); }
    @PostMapping("/apps/{appId}")
    public ResultData<?> create(@PathVariable Long appId, HttpServletRequest request) {
        return ResultData.ok(channels.create(appId, reader.read(request, CustomerServiceChannelService.Settings.class)));
    }
    @PutMapping("/items/{id}")
    public ResultData<?> update(@PathVariable String id, HttpServletRequest request) {
        var command = reader.read(request, UpdateRequest.class);
        return ResultData.ok(channels.update(id, command.revision(), command.status(), command.settings()));
    }
    @GetMapping("/entries/{code}")
    public ResultData<?> entry(@PathVariable String code) { return ResultData.ok(channels.entry(code)); }
    @PostMapping("/entries/{code}/sessions")
    public ResultData<?> createSession(@PathVariable String code, HttpServletRequest request) {
        reader.read(request, PlatformConsoleCustomerServiceController.EmptyRequest.class);
        return ResultData.ok(conversations.createFromChannel(code));
    }
    public record UpdateRequest(Long revision, String status, CustomerServiceChannelService.Settings settings) { }
}
