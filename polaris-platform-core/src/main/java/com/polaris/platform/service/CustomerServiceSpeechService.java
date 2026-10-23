package com.polaris.platform.service;

import com.polaris.ai.customer.application.*;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.core.context.CustomerServiceVisitorContext;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import com.polaris.ai.runtime.audio.AudioTtsResult;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.*;
import com.polaris.platform.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 短录音只产生待核对文字，不生成对话轮次或执行业务；持久化去重防止未知结果自动重计费。 */
@Service
public class CustomerServiceSpeechService {
    public static final int MAX_WAV_BYTES = 640044;
    private final CustomerServiceMapper customers;
    private final CustomerServiceSpeechMapper requests;
    private final CustomerServiceAccess access;
    private final CustomerServiceConfigurationService configurations;
    private final CustomerServiceChannelService channels;
    private final CustomerServiceSpeechGateway speech;
    private final TokenQuotaService quotas;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final CustomerServiceConversationService conversations;
    private final LinkedHashMap<String, CachedAudio> audioCache = new LinkedHashMap<>();
    private final java.util.concurrent.Semaphore synthesisSlots = new java.util.concurrent.Semaphore(4);
    private long cachedBytes;

    public CustomerServiceSpeechService(CustomerServiceMapper customers, CustomerServiceSpeechMapper requests,
            CustomerServiceAccess access, CustomerServiceConfigurationService configurations, CustomerServiceChannelService channels,
            CustomerServiceSpeechGateway speech, TokenQuotaService quotas, ObjectMapper json, PlatformTransactionManager transactionManager, CustomerServiceConversationService conversations) {
        this.customers = customers; this.requests = requests; this.access = access; this.configurations = configurations;
        this.channels = channels; this.speech = speech; this.quotas = quotas; this.json = json;
        this.transactions = new TransactionTemplate(transactionManager); this.conversations = conversations;
    }
    public List<CustomerServiceSpeechGateway.Model> models() { access.requireAdmin(); return speech.models(); }
    /** WebSocket 只绑定已发布识别模型，与 HTTP 共用同一发布哈希。 */
    public StreamPlan streamPlan(String sessionId) {
        var actor = requireActor();
        return transactions.execute(status -> {
            var fixed = scope(actor, sessionId); requireModel(fixed);
            speech.validateVocabulary(fixed.config().voice().sttModelId(),fixed.config().voice().vocabulary());
            return new StreamPlan(actor, fixed.config().voice().sttModelId(), fixed.release().getSpeechModelHash(), fixed.config().voice().vocabulary());
        });
    }
    public void beginStream(String sessionId, String requestId) {
        requireRequest(requestId); var actor = requireActor();
        transactions.executeWithoutResult(status -> {
            var fixed = scope(actor, sessionId); requireModel(fixed);
            requests.expire(actor.tenantId(), sessionId, new Date());
            if (requests.select(actor.tenantId(), sessionId, requestId) != null) throw new ServiceException("SPEECH_REQUEST_USED: 流式请求不可重连重放", 409);
            requireRecognitionRound(actor, sessionId);
            if (requests.count(actor.tenantId(), sessionId) >= speechLimit() || customers.countActiveSpeech(actor.tenantId(), sessionId, new Date()) > 0
                    || customers.selectTurns(actor.tenantId(), sessionId).stream().anyMatch(turn -> "RUNNING".equals(turn.getStatus()))) {
                throw new ServiceException("SPEECH_BUSY: 当前会话无法开始新的识别", 409);
            }
            var row = new CustomerServiceSpeech();
            row.setId(UUID.randomUUID().toString()); row.setTenantId(actor.tenantId()); row.setSessionId(sessionId); row.setRequestId(requestId);
            row.setKind("STREAM"); row.setInputHash(digest(("STREAM:" + requestId).getBytes(StandardCharsets.UTF_8))); row.setAudioMillis(0);
            row.setCreateTime(new Date()); row.setDeadline(new Date(System.currentTimeMillis() + 60000));
            if (requests.insert(row) != 1) throw new ServiceException("保存实时语音请求失败");
        });
    }
    public boolean streamActive(String sessionId, String requestId) { return active(requireActor(), sessionId, requestId); }
    public View finishStream(String sessionId, String requestId, String text, String error, int bytes, String hash, NormalizedUsage usage) {
        var actor = requireActor();
        transactions.executeWithoutResult(status -> {
            owned(actor, sessionId);
            var row = requests.select(actor.tenantId(), sessionId, requestId);
            if (row == null || !"STREAM".equals(row.getKind())) throw missing();
            boolean valid = active(actor, sessionId, requestId);
            row.setAudioMillis(Math.max(0, bytes / 32)); row.setInputHash(hash);
            row.setStatus(valid ? error == null ? "COMPLETED" : "FAILED" : "INTERRUPTED");
            row.setTranscript(valid && error == null ? text : null); row.setErrorCode(valid ? error : "SPEECH_INTERRUPTED");
            row.setUsageJson(json.writeValueAsString(safeUsage(usage))); row.setFinishedTime(new Date());
            if (requests.finish(row) == 0) requests.saveUsage(row);
        });
        return get(sessionId, requestId);
    }
    public record StreamPlan(CustomerServiceAccess.Actor actor, Long modelId, String modelHash, List<String> vocabulary) {
        public StreamPlan(CustomerServiceAccess.Actor actor, Long modelId, String modelHash) { this(actor,modelId,modelHash,List.of()); }
        public StreamPlan { vocabulary = com.polaris.ai.runtime.audio.RecognitionVocabulary.normalize(vocabulary); }
    }
    public Settings settings(String sessionId) {
        var actor = requireActor();
        return transactions.execute(status -> {
            var scope = scope(actor, sessionId);
            var voice = scope.config().voice();
            boolean ready = false;
            if (voice != null && voice.sttModelId() != null) {
                try { ready = Objects.equals(scope.release().getSpeechModelHash(), speech.validate(voice.sttModelId())); speech.validateVocabulary(voice.sttModelId(),voice.vocabulary()); }
                catch (RuntimeException unavailable) { ready = false; /* 识别不可用仍可文字接待及设备播报。 */ }
            }
            boolean playback = false;
            if (voice != null && voice.ttsModelId() != null) {
                try { playback = Objects.equals(scope.release().getPlaybackModelHash(), speech.validateTts(voice.ttsModelId())); }
                catch (RuntimeException unavailable) { /* 供应商播报不可用时可选设备音色。 */ }
            }
            return new Settings(ready, voice != null && voice.devicePlayback(), playback, 20, "PCM_WAV_16000_MONO", ready ? null : "SPEECH_NOT_CONFIGURED");
        });
    }
    public View transcribe(String sessionId, String requestId, byte[] wav) {
        requireRequest(requestId);
        int millis = validateWav(wav);
        String hash = digest(wav);
        var actor = requireActor();
        var begun = transactions.execute(status -> {
            var scope = scope(actor, sessionId);
            requireModel(scope);
            requests.expire(actor.tenantId(), sessionId, new Date());
            var existing = requests.select(actor.tenantId(), sessionId, requestId);
            if (existing != null) {
                if (!"STT".equals(existing.getKind()) || !hash.equals(existing.getInputHash())) throw new ServiceException("INVALID_INPUT: 同一语音请求不能更换录音", 409);
                return new Begun(existing, scope, false);
            }
            requireRecognitionRound(actor, sessionId);
            if (customers.countActiveSpeech(actor.tenantId(), sessionId, new Date()) > 0
                    || customers.selectTurns(actor.tenantId(), sessionId).stream().anyMatch(turn -> "RUNNING".equals(turn.getStatus()))) {
                throw new ServiceException("SPEECH_BUSY: 当前识别或对话尚未结束", 409);
            }
            if (requests.count(actor.tenantId(), sessionId) >= speechLimit()) throw new ServiceException("RATE_LIMITED: 本会话识别或播报请求已达上限", 429);
            var request = new CustomerServiceSpeech();
            request.setId(UUID.randomUUID().toString()); request.setTenantId(actor.tenantId()); request.setSessionId(sessionId);
            request.setRequestId(requestId); request.setInputHash(hash); request.setKind("STT"); request.setAudioMillis(millis); request.setStatus("RUNNING");
            request.setCreateTime(new Date()); request.setDeadline(new Date(request.getCreateTime().getTime() + 180000));
            if (requests.insert(request) != 1) throw new ServiceException("保存语音请求失败");
            return new Begun(request, scope, true);
        });
        if (!begun.created()) return view(begun.request());
        var request = begun.request();
        String text = null, error = null;
        NormalizedUsage[] usage = { NormalizedUsage.unknown() };
        TokenQuotaService.TokenReservation reservation = null;
        try {
            reservation = quotas.reserve(actor.tenantId().toString(), 1000);
            var fixed = begun.scope();
            java.util.function.Consumer<NormalizedUsage> measure = measured -> {
                        if (measured != null) usage[0] = new NormalizedUsage(measured.inputTokens(), measured.outputTokens(), measured.totalTokens(),
                                measured.inputCharacters(), measured.audioInputMillis(), measured.audioOutputMillis(), Map.of(), measured.source());
                    };
            var voice = fixed.config().voice();
            text = voice.vocabulary().isEmpty()
                    ? speech.transcribe(voice.sttModelId(),fixed.release().getSpeechModelHash(),wav,() -> active(actor,sessionId,requestId),measure)
                    : speech.transcribe(voice.sttModelId(),fixed.release().getSpeechModelHash(),wav,voice.vocabulary(),() -> active(actor,sessionId,requestId),measure);
            if (text == null || text.isBlank() || text.length() > 2000) throw new ServiceException("SPEECH_TEXT_INVALID: 识别文本无效", 409);
        } catch (RuntimeException failed) { error = safeError(failed); }
        finally {
            if (reservation != null) {
                try { if (usage[0].totalTokens() == null) quotas.release(reservation); else quotas.complete(reservation, usage[0].totalTokens()); }
                catch (RuntimeException settlement) { error = "USAGE_SETTLEMENT_FAILED"; }
            }
        }
        String completedText = text, completedError = error;
        transactions.executeWithoutResult(status -> {
            owned(actor, sessionId);
            requests.expire(actor.tenantId(), sessionId, new Date());
            boolean valid = active(actor, sessionId, requestId);
            request.setStatus(valid ? completedError == null ? "COMPLETED" : "FAILED" : "INTERRUPTED");
            request.setTranscript(valid && completedError == null ? completedText.strip() : null);
            request.setErrorCode(valid ? completedError : "SPEECH_INTERRUPTED");
            request.setUsageJson(json.writeValueAsString(usage[0])); request.setFinishedTime(new Date());
            if (requests.finish(request) == 0) requests.saveUsage(request);
        });
        return get(sessionId, requestId);
    }
    public View get(String sessionId, String requestId) {
        requireRequest(requestId);
        var actor = requireActor();
        return transactions.execute(status -> {
            var scope = scope(actor, sessionId);
            requests.expire(actor.tenantId(), sessionId, new Date());
            var request = requests.select(actor.tenantId(), sessionId, requestId);
            if (request == null) throw missing();
            requireModel(scope, request.getKind());
            return view(request);
        });
    }
    public void cancel(String sessionId, String requestId) {
        requireRequest(requestId);
        var actor = requireActor();
        transactions.executeWithoutResult(status -> {
            owned(actor, sessionId);
            if (requests.select(actor.tenantId(), sessionId, requestId) == null) {
                if (requests.count(actor.tenantId(), sessionId) >= speechLimit()) throw new ServiceException("RATE_LIMITED: 语音请求数量已达上限", 429);
                // 取消可先于上传到达；持久化墓碑，阻止迟到上传成为新的计费请求。
                var cancelled = new CustomerServiceSpeech();
                cancelled.setId(UUID.randomUUID().toString()); cancelled.setTenantId(actor.tenantId()); cancelled.setSessionId(sessionId);
                cancelled.setRequestId(requestId); cancelled.setKind("CANCEL"); cancelled.setInputHash("0".repeat(64)); cancelled.setAudioMillis(0);
                cancelled.setCreateTime(new Date()); cancelled.setDeadline(new Date(System.currentTimeMillis() + 180000));
                if (requests.insert(cancelled) != 1) throw new ServiceException("保存语音取消状态失败");
            }
            requests.interrupt(actor.tenantId(), sessionId, requestId, new Date());
        });
    }
    /** 浏览器只能指定已完成回答ID；服务端重新取得可见回答，不能充当任意文本TTS代理。 */
    public View synthesize(String sessionId, String requestId, String turnId) {
        return synthesize(sessionId, requestId, turnId, null);
    }
    public View synthesize(String sessionId, String requestId, String turnId, Integer partNo) {
        requireRequest(requestId);
        String text = speakable(sessionId, turnId, partNo);
        String hash = playbackHash(turnId, partNo, text);
        var actor = requireActor();
        var begun = transactions.execute(status -> {
            var fixed = scope(actor, sessionId); requireModel(fixed, "TTS");
            if (!text.equals(speakable(sessionId, turnId, partNo))) throw missing();
            requests.expire(actor.tenantId(), sessionId, new Date());
            var existing = requests.select(actor.tenantId(), sessionId, requestId);
            if (existing != null) {
                if (!"TTS".equals(existing.getKind()) || !hash.equals(existing.getInputHash())) throw new ServiceException("INVALID_INPUT: 同一请求不能更换播报回答", 409);
                return new Begun(existing, fixed, false);
            }
            if (customers.countActiveSpeech(actor.tenantId(), sessionId, new Date()) > 0
                    || customers.selectTurns(actor.tenantId(), sessionId).stream().anyMatch(turn -> "RUNNING".equals(turn.getStatus())
                        && !(partNo != null && Objects.equals(turnId, turn.getId())))) {
                throw new ServiceException("SPEECH_BUSY: 当前识别或对话尚未结束", 409);
            }
            if (partNo != null && requests.selectSession(actor.tenantId(), sessionId).stream()
                    .anyMatch(row -> Objects.equals(turnId, row.getTurnId()) && Objects.equals(partNo, row.getPartNo()))) {
                throw new ServiceException("SPEECH_REQUEST_USED: 此分段已有播报请求，请核对原请求", 409);
            }
            if (requests.count(actor.tenantId(), sessionId) >= speechLimit()) throw new ServiceException("RATE_LIMITED: 本会话识别或播报请求已达上限", 429);
            var request = new CustomerServiceSpeech();
            request.setId(UUID.randomUUID().toString()); request.setTenantId(actor.tenantId()); request.setSessionId(sessionId);
            request.setRequestId(requestId); request.setInputHash(hash); request.setKind("TTS"); request.setTurnId(turnId); request.setPartNo(partNo); request.setAudioMillis(0); request.setStatus("RUNNING");
            request.setCreateTime(new Date()); request.setDeadline(new Date(request.getCreateTime().getTime() + 180000));
            if (requests.insert(request) != 1) throw new ServiceException("保存播报请求失败");
            return new Begun(request, fixed, true);
        });
        if (!begun.created()) return view(begun.request());
        var request = begun.request();
        AudioTtsResult audio = null; String error = null;
        NormalizedUsage[] usage = { NormalizedUsage.unknown() };
        TokenQuotaService.TokenReservation reservation = null;
        boolean acquired = synthesisSlots.tryAcquire();
        try {
            if (!acquired) throw new ServiceException("SPEECH_BUSY: 播报服务繁忙", 429);
            reservation = quotas.reserve(actor.tenantId().toString(), 1000);
            audio = speech.synthesize(begun.scope().config().voice().ttsModelId(), begun.scope().release().getPlaybackModelHash(), text,
                    () -> active(actor, sessionId, requestId), measured -> { if (measured != null) usage[0] = safeUsage(measured); });
            if (!text.equals(speakable(sessionId, turnId, partNo))) throw new ServiceException("SPEECH_INTERRUPTED: 回答已变化", 409);
            if (audio == null || audio.audio().length == 0 || audio.audio().length > 8 * 1024 * 1024) throw new ServiceException("SPEECH_FAILED: 合成音频无效", 502);
        } catch (RuntimeException failed) { error = safeError(failed); }
        finally {
            if (acquired) synthesisSlots.release();
            if (reservation != null) {
                try { if (usage[0].totalTokens() == null) quotas.release(reservation); else quotas.complete(reservation, usage[0].totalTokens()); }
                catch (RuntimeException failed) { error = "USAGE_SETTLEMENT_FAILED"; }
            }
        }
        AudioTtsResult completedAudio = audio; String completedError = error;
        transactions.executeWithoutResult(status -> {
            owned(actor, sessionId); requests.expire(actor.tenantId(), sessionId, new Date());
            boolean valid = active(actor, sessionId, requestId);
            request.setStatus(valid ? completedError == null ? "COMPLETED" : "FAILED" : "INTERRUPTED");
            request.setErrorCode(valid ? completedError : "SPEECH_INTERRUPTED");
            request.setUsageJson(json.writeValueAsString(usage[0])); request.setFinishedTime(new Date());
            if (requests.finish(request) == 0) requests.saveUsage(request);
            else if ("COMPLETED".equals(request.getStatus())) cache(request.getId(), completedAudio);
        });
        return get(sessionId, requestId);
    }
    public AudioTtsResult audio(String sessionId, String requestId) {
        requireRequest(requestId);
        var actor = requireActor();
        var request = transactions.execute(status -> {
            var fixed = scope(actor, sessionId); requireModel(fixed, "TTS");
            var found = requests.select(actor.tenantId(), sessionId, requestId);
            if (found == null || !"TTS".equals(found.getKind()) || !"COMPLETED".equals(found.getStatus())
                    || found.getFinishedTime().getTime() + 300000 <= System.currentTimeMillis()) throw missing();
            return found;
        });
        String text = speakable(sessionId, request.getTurnId(), request.getPartNo());
        if (!request.getInputHash().equals(playbackHash(request.getTurnId(), request.getPartNo(), text))) throw missing();
        // 再次核验渠道/会话与模型；内存丢失不会重新调用供应商。
        return transactions.execute(status -> {
            var fixed = scope(actor, sessionId); requireModel(fixed, "TTS");
            synchronized (audioCache) {
                evictAudio(); var cached = audioCache.get(request.getId());
                if (cached == null) throw new ServiceException("SPEECH_AUDIO_EXPIRED: 播报音频已过期或服务已重启，请使用文字或设备音色", 410);
                return cached.audio();
            }
        });
    }
    private String playbackHash(String turnId, Integer partNo, String text) {
        return digest((turnId + (partNo == null ? "" : ":part:" + partNo) + "\n" + text).getBytes(StandardCharsets.UTF_8));
    }
    private String speakable(String sessionId, String turnId, Integer partNo) {
        if (partNo != null) return cleanSpokenText(conversations.answerPart(sessionId, turnId, partNo));
        var session = conversations.get(sessionId);
        if (!"ACTIVE".equals(session.status()) || session.turns().isEmpty()) throw missing();
        var turn = session.turns().get(session.turns().size() - 1);
        if (!Objects.equals(turn.id(), turnId) || !"COMPLETED".equals(turn.status()) || turn.errorCode() != null || turn.answer() == null) throw missing();
        return cleanSpokenText(turn.answer());
    }
    private String cleanSpokenText(String answer) {
        String text = answer.replaceAll("\\[S[0-9]+\\]", "").replaceAll("[*#`]", "").strip();
        if (text.isBlank()) throw missing();
        return text.substring(0, Math.min(600, text.length()));
    }
    private NormalizedUsage safeUsage(NormalizedUsage measured) {
        Object words = measured.details().get("providerTextWords");
        Map<String, Object> detail = words instanceof Number number && number.longValue() >= 0 ? Map.of("providerTextWords", number.longValue()) : Map.of();
        return new NormalizedUsage(measured.inputTokens(), measured.outputTokens(), measured.totalTokens(), measured.inputCharacters(),
                measured.audioInputMillis(), measured.audioOutputMillis(), detail, measured.source());
    }
    private void cache(String id, AudioTtsResult audio) {
        synchronized (audioCache) {
            evictAudio();
            while (!audioCache.isEmpty() && (cachedBytes + audio.audio().length > 32 * 1024 * 1024 || audioCache.size() >= 16)) {
                var first = audioCache.entrySet().iterator(); var item = first.next(); cachedBytes -= item.getValue().size(); first.remove();
            }
            audioCache.put(id, new CachedAudio(audio, audio.audio().length, System.currentTimeMillis() + 300000)); cachedBytes += audio.audio().length;
        }
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    public void clearExpiredAudio() { synchronized (audioCache) { evictAudio(); } }
    private void evictAudio() {
        var iterator = audioCache.values().iterator();
        while (iterator.hasNext()) { var item = iterator.next(); if (item.expires() <= System.currentTimeMillis()) { cachedBytes -= item.size(); iterator.remove(); } }
    }
    private record CachedAudio(AudioTtsResult audio, int size, long expires) { }
    private boolean active(CustomerServiceAccess.Actor actor, String sessionId, String requestId) {
        try {
            if (!actor.equals(requireActor())) return false;
            Supplier<Boolean> check = () -> {
                var scope = scope(actor, sessionId);
                var request = requests.select(actor.tenantId(), sessionId, requestId);
                if (request != null) {
                    requireModel(scope, request.getKind());
                    if (request.getPartNo() != null) conversations.answerPart(sessionId, request.getTurnId(), request.getPartNo());
                }
                return request != null && "RUNNING".equals(request.getStatus()) && request.getDeadline().after(new Date());
            };
            return Boolean.TRUE.equals(TransactionSynchronizationManager.isActualTransactionActive() ? check.get() : transactions.execute(status -> check.get()));
        } catch (ServiceException unavailable) { return false; }
    }
    private Scope scope(CustomerServiceAccess.Actor actor, String id) {
        var session = owned(actor, id);
        var app = customers.lockApp(actor.tenantId(), session.getAppId());
        if (app == null || !"ACTIVE".equals(app.getStatus()) || !"ACTIVE".equals(session.getStatus())
                || !Objects.equals(app.getRuntimeEpoch(), session.getRuntimeEpoch()) || !session.getExpiresAt().after(new Date())) throw missing();
        channels.requireSession(session);
        var release = customers.selectRelease(actor.tenantId(), session.getReleaseId());
        if (release == null || !session.getAppId().equals(release.getAppId())) throw missing();
        return new Scope(release, configurations.config(release.getConfigJson()));
    }
    private CustomerServiceSession owned(CustomerServiceAccess.Actor actor, String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw missing();
        var session = customers.lockSession(actor.tenantId(), actor.userId(), id);
        if (session == null || !actor.tenantId().equals(session.getTenantId()) || !actor.userId().equals(session.getUserId())) throw missing();
        if (CallerContextHolder.get() instanceof CustomerServiceVisitorContext visitor
                && (!Objects.equals(id, visitor.sessionId()) || !Objects.equals(session.getChannelId(), visitor.channelId())
                    || !Objects.equals(session.getVisitorTokenHash(), visitor.tokenHash()))) throw missing();
        return session;
    }
    private CustomerServiceAccess.Actor requireActor() {
        if (CallerContextHolder.get() instanceof CustomerServiceVisitorContext visitor
                && visitor.speechLimit() == 0) throw new ServiceException("SPEECH_UNAVAILABLE: 此渠道未开放访客语音", 403);
        return access.requireConversationActor();
    }
    private void requireRecognitionRound(CustomerServiceAccess.Actor actor, String sessionId) {
        if (CallerContextHolder.get() instanceof CustomerServiceVisitorContext visitor
                && customers.selectTurns(actor.tenantId(), sessionId).size() >= visitor.messageLimit()) {
            throw new ServiceException("RATE_LIMITED: 本次咨询问答轮数已达上限，请结束后重新开始", 429);
        }
    }
    private int speechLimit() {
        return CallerContextHolder.get() instanceof CustomerServiceVisitorContext visitor ? visitor.speechLimit() : 40;
    }
    private void requireModel(Scope scope, String kind) {
        if (!"TTS".equals(kind)) { requireModel(scope); return; }
        var voice = scope.config().voice();
        if (voice == null || voice.ttsModelId() == null || scope.release().getPlaybackModelHash() == null
                || !Objects.equals(scope.release().getPlaybackModelHash(), speech.validateTts(voice.ttsModelId()))) {
            throw new ServiceException("SPEECH_UNAVAILABLE: 合成模型未配置或已变化，请检查并重新发布", 409);
        }
    }
    private void requireModel(Scope scope) {
        var voice = scope.config().voice();
        if (voice == null || voice.sttModelId() == null || scope.release().getSpeechModelHash() == null
                || !Objects.equals(scope.release().getSpeechModelHash(), speech.validate(voice.sttModelId()))) {
            throw new ServiceException("SPEECH_UNAVAILABLE: 语音模型未配置或已变化，请检查并重新发布", 409);
        }
    }
    private View view(CustomerServiceSpeech request) {
        boolean expired = "COMPLETED".equals(request.getStatus()) && (request.getFinishedTime() == null
                || request.getFinishedTime().getTime() + 300000 <= System.currentTimeMillis());
        return new View(request.getRequestId(), expired ? "EXPIRED" : request.getStatus(),
                !expired && "COMPLETED".equals(request.getStatus()) ? request.getTranscript() : null,
                expired ? "TTS".equals(request.getKind()) ? "SPEECH_AUDIO_EXPIRED" : "TRANSCRIPT_EXPIRED" : request.getErrorCode());
    }
    public static int validateWav(byte[] wav) {
        if (wav == null || wav.length < 6444 || wav.length > MAX_WAV_BYTES) throw invalid();
        var buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (!tag(wav, 0, "RIFF") || buffer.getInt(4) != wav.length - 8 || !tag(wav, 8, "WAVE") || !tag(wav, 12, "fmt ")
                || buffer.getInt(16) != 16 || buffer.getShort(20) != 1 || buffer.getShort(22) != 1
                || buffer.getInt(24) != 16000 || buffer.getInt(28) != 32000 || buffer.getShort(32) != 2
                || buffer.getShort(34) != 16 || !tag(wav, 36, "data") || buffer.getInt(40) != wav.length - 44 || (wav.length - 44) % 2 != 0) throw invalid();
        return Math.max(1, (wav.length - 44) / 32);
    }
    private static boolean tag(byte[] bytes, int offset, String expected) { return expected.equals(new String(bytes, offset, 4, StandardCharsets.US_ASCII)); }
    private String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private void requireRequest(String value) { if (value == null || !value.matches("[A-Za-z0-9._:-]{1,64}")) throw invalid(); }
    private String safeError(RuntimeException failed) {
        if (failed instanceof TokenQuotaService.TokenQuotaExceededException) return "QUOTA_EXCEEDED";
        if (failed instanceof TokenQuotaService.TokenQuotaUnavailableException) return "QUOTA_UNAVAILABLE";
        String message = failed instanceof ServiceException ? failed.getMessage() : null;
        String code = message == null ? "" : message.split(":", 2)[0];
        return Set.of("CONTENT_BLOCKED", "SPEECH_UNAVAILABLE", "SPEECH_PROVIDER_FAILED", "SPEECH_INTERRUPTED", "SPEECH_TEXT_INVALID").contains(code) ? code : "SPEECH_FAILED";
    }
    private static ServiceException invalid() { return new ServiceException("INVALID_INPUT: 仅接受最多 20 秒的 16kHz 单声道 16位 PCM WAV", 400); }
    private ServiceException missing() { return new ServiceException("语音请求或会话不存在、已失效或无权访问", 404); }
    private record Scope(CustomerServiceRelease release, CustomerServiceConfig config) { }
    private record Begun(CustomerServiceSpeech request, Scope scope, boolean created) { }
    public record Settings(boolean recognitionAvailable, boolean devicePlayback, boolean providerPlayback, int maxSeconds, String inputFormat, String errorCode) { }
    public record View(String requestId, String status, String text, String errorCode) { }
}
