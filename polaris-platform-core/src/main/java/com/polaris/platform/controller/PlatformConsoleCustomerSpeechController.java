package com.polaris.platform.controller;

import com.polaris.common.core.domain.ResultData;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.service.CustomerServiceSpeechService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** 仅登录态短录音，不接收任意模型、音频 URL 或客户端历史。 */
@RestController
@RequestMapping("/platform/console/customer-services")
public class PlatformConsoleCustomerSpeechController {
    private final CustomerServiceSpeechService speech;
    private final CustomerServiceRequestReader reader;
    public PlatformConsoleCustomerSpeechController(CustomerServiceSpeechService speech, ObjectMapper json) {
        this.speech = speech; this.reader = new CustomerServiceRequestReader(json);
    }
    @ModelAttribute
    public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @GetMapping("/speech-models")
    public ResultData<?> models() { return ResultData.ok(speech.models()); }
    @GetMapping("/sessions/{sessionId}/speech")
    public ResultData<?> settings(@PathVariable String sessionId) { return ResultData.ok(speech.settings(sessionId)); }
    @PostMapping(value = "/sessions/{sessionId}/speech/requests/{requestId}", consumes = "audio/wav")
    public ResultData<?> transcribe(@PathVariable String sessionId, @PathVariable String requestId, HttpServletRequest request) throws IOException {
        if (!request.getParameterMap().isEmpty()) throw new ServiceException("INVALID_INPUT: 语音接口不接受额外参数", 400);
        byte[] bytes = request.getInputStream().readNBytes(CustomerServiceSpeechService.MAX_WAV_BYTES + 1);
        if (bytes.length > CustomerServiceSpeechService.MAX_WAV_BYTES) throw new ServiceException("INVALID_INPUT: 录音超过 20 秒大小限制", 413);
        return ResultData.ok(speech.transcribe(sessionId, requestId, bytes));
    }
    @PostMapping("/sessions/{sessionId}/speech/requests/{requestId}/playback")
    public ResultData<?> synthesize(@PathVariable String sessionId, @PathVariable String requestId, HttpServletRequest request) {
        var input = reader.read(request, PlaybackRequest.class);
        return ResultData.ok(input.partNo() == null ? speech.synthesize(sessionId, requestId, input.turnId())
                : speech.synthesize(sessionId, requestId, input.turnId(), input.partNo()));
    }
    @GetMapping("/sessions/{sessionId}/speech/requests/{requestId}/audio")
    public org.springframework.http.ResponseEntity<byte[]> audio(@PathVariable String sessionId, @PathVariable String requestId) {
        var audio = speech.audio(sessionId, requestId);
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff").contentType(org.springframework.http.MediaType.parseMediaType(audio.contentType())).body(audio.audio());
    }
    public record PlaybackRequest(String turnId, Integer partNo) { }
    @GetMapping("/sessions/{sessionId}/speech/requests/{requestId}")
    public ResultData<?> get(@PathVariable String sessionId, @PathVariable String requestId) { return ResultData.ok(speech.get(sessionId, requestId)); }
    @PostMapping("/sessions/{sessionId}/speech/requests/{requestId}/cancel")
    public ResultData<?> cancel(@PathVariable String sessionId, @PathVariable String requestId, HttpServletRequest request) {
        reader.read(request, PlatformConsoleCustomerServiceController.EmptyRequest.class);
        speech.cancel(sessionId, requestId); return ResultData.ok();
    }
}
