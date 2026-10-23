package com.polaris.platform.controller;

import com.polaris.common.core.domain.ResultData;
import com.polaris.platform.service.CustomerServiceRealtimeSpeechService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/platform/console/customer-services")
public class PlatformConsoleCustomerRealtimeSpeechController {
    private final CustomerServiceRealtimeSpeechService streams;
    private final CustomerServiceRequestReader reader;
    public PlatformConsoleCustomerRealtimeSpeechController(CustomerServiceRealtimeSpeechService streams, ObjectMapper json) {
        this.streams = streams; reader = new CustomerServiceRequestReader(json);
    }
    @PostMapping("/sessions/{sessionId}/speech/realtime-ticket")
    public ResultData<?> ticket(@PathVariable String sessionId, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var input = reader.read(request, TicketRequest.class);
        return ResultData.ok(streams.ticket(sessionId, input.requestId(), request.getHeader("Origin")));
    }
    public record TicketRequest(String requestId) { }
}
