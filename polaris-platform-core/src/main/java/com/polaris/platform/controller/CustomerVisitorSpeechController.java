package com.polaris.platform.controller;

import com.polaris.common.core.domain.ResultData;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.service.CustomerServiceRealtimeSpeechService;
import com.polaris.platform.service.CustomerServiceSpeechService;
import com.polaris.platform.service.CustomerVisitorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.function.Supplier;

/** 访客语音只接受本次会话凭证和固定发布模型，不能枚举模型或合成任意文本。 */
@RestController
@RequestMapping("/platform/runtime/customer-visitors/{code}/sessions/{id}/speech")
public class CustomerVisitorSpeechController {
    private final CustomerVisitorService visitors;
    private final CustomerServiceSpeechService speech;
    private final CustomerServiceRealtimeSpeechService realtime;
    private final CustomerServiceRequestReader reader;
    public CustomerVisitorSpeechController(CustomerVisitorService visitors, CustomerServiceSpeechService speech,
            CustomerServiceRealtimeSpeechService realtime, ObjectMapper json) {
        this.visitors = visitors; this.speech = speech; this.realtime = realtime;
        this.reader = new CustomerServiceRequestReader(json);
    }
    @ModelAttribute
    public void guard(HttpServletRequest request, HttpServletResponse response) { CustomerVisitorController.guardRequest(request, response); }
    @GetMapping
    public ResultData<?> settings(@PathVariable String code, @PathVariable String id, HttpServletRequest request) {
        return call(code, id, request, false, () -> speech.settings(id));
    }
    @PostMapping("/realtime-ticket")
    public ResultData<?> ticket(@PathVariable String code, @PathVariable String id, HttpServletRequest request) {
        var command = reader.read(request, PlatformConsoleCustomerRealtimeSpeechController.TicketRequest.class);
        return call(code, id, request, true, () -> realtime.ticket(id, command.requestId(), request.getHeader("Origin")));
    }
    @PostMapping(value = "/requests/{requestId}", consumes = "audio/wav")
    public ResultData<?> transcribe(@PathVariable String code, @PathVariable String id, @PathVariable String requestId,
            HttpServletRequest request) throws IOException {
        // 先验证会话与限流，再读取有界录音；不接受音频URL、任意模型或额外身份。
        return call(code, id, request, true, () -> {
            try {
                byte[] wav = request.getInputStream().readNBytes(CustomerServiceSpeechService.MAX_WAV_BYTES + 1);
                if (wav.length > CustomerServiceSpeechService.MAX_WAV_BYTES) throw new ServiceException("INVALID_INPUT: 录音超过20秒大小限制", 413);
                return speech.transcribe(id, requestId, wav);
            } catch (IOException failed) { throw new ServiceException("INVALID_INPUT: 无法读取短录音", 400); }
        });
    }
    @PostMapping("/requests/{requestId}/playback")
    public ResultData<?> playback(@PathVariable String code, @PathVariable String id, @PathVariable String requestId, HttpServletRequest request) {
        var command = reader.read(request, PlatformConsoleCustomerSpeechController.PlaybackRequest.class);
        return call(code, id, request, true, () -> speech.synthesize(id, requestId, command.turnId(), command.partNo()));
    }
    @GetMapping("/requests/{requestId}")
    public ResultData<?> get(@PathVariable String code, @PathVariable String id, @PathVariable String requestId, HttpServletRequest request) {
        return call(code, id, request, false, () -> speech.get(id, requestId));
    }
    @PostMapping("/requests/{requestId}/cancel")
    public ResultData<?> cancel(@PathVariable String code, @PathVariable String id, @PathVariable String requestId, HttpServletRequest request) {
        reader.read(request, PlatformConsoleCustomerServiceController.EmptyRequest.class);
        return call(code, id, request, false, () -> { speech.cancel(id, requestId); return null; });
    }
    @GetMapping("/requests/{requestId}/audio")
    public ResponseEntity<byte[]> audio(@PathVariable String code, @PathVariable String id, @PathVariable String requestId, HttpServletRequest request) {
        var audio = visitors.speech(code, id, request.getHeader("Customer-Session-Token"), false, request.getRemoteAddr(), () -> speech.audio(id, requestId));
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(audio.contentType())).body(audio.audio());
    }
    private ResultData<?> call(String code, String id, HttpServletRequest request, boolean charge, Supplier<?> action) {
        return ResultData.ok(visitors.speech(code, id, request.getHeader("Customer-Session-Token"), charge, request.getRemoteAddr(), action));
    }
}
