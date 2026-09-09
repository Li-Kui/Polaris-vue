package com.polaris.ai.safety.service.impl;

import com.polaris.ai.safety.config.ModerationProperties;
import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.mapper.ModerationEventMapper;
import com.polaris.ai.safety.model.ModerationEvent;
import com.polaris.ai.safety.service.FailureLogLimiter;
import com.polaris.ai.safety.service.IModerationAuditService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * AI 安全检测审计日志服务层实现类
 *
 * @author polaris
 */
@Service
public class ModerationAuditServiceImpl implements IModerationAuditService {

    private static final Logger log = LoggerFactory.getLogger(ModerationAuditServiceImpl.class);
    private static final int MAX_MASKED_EXCERPT = 120;

    @Autowired(required = false)
    private ModerationEventMapper mapper;

    @Autowired(required = false)
    @Qualifier("threadPoolTaskExecutor")
    private TaskExecutor executor;

    @Autowired(required = false)
    private ModerationProperties properties;

    private EventWriter writer;
    private Clock clock = Clock.systemUTC();
    private int retentionDays = 180;

    private final FailureLogLimiter rateLimiter =
            new FailureLogLimiter(Duration.ofMinutes(1), System::nanoTime);

    public ModerationAuditServiceImpl() {}

    public ModerationAuditServiceImpl(EventWriter writer, TaskExecutor executor, Clock clock, int retentionDays) {
        this.writer = writer;
        this.executor = executor;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @PostConstruct
    public void init() {
        if (this.writer == null && this.mapper != null) {
            this.writer = this.mapper::insert;
        }
        if (this.properties != null && this.properties.getAuditRetentionDays() > 0) {
            this.retentionDays = this.properties.getAuditRetentionDays();
        }
    }

    public void setWriter(EventWriter writer) {
        this.writer = writer;
    }

    public void setExecutor(TaskExecutor executor) {
        this.executor = executor;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    @Override
    public void record(ModerationRequest request, ModerationResult result) {
        if (request == null || result == null) {
            return;
        }
        ModerationEvent event;
        try {
            event = event(request, result);
        } catch (RuntimeException buildFailure) {
            logFailure(request, result, buildFailure);
            return;
        }
        if (executor != null) {
            try {
                executor.execute(() -> insertAsync(event));
            } catch (Throwable t) {
                if (isRejection(t)) {
                    insertAsync(event);
                } else {
                    logFailure(request, result, t);
                }
            }
        } else {
            insertAsync(event);
        }
    }

    private static boolean isRejection(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof RejectedExecutionException
                    || current instanceof org.springframework.core.task.TaskRejectedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void insertAsync(ModerationEvent event) {
        try {
            if (writer != null) {
                writer.write(event);
            }
        } catch (RuntimeException insertFailure) {
            if (rateLimiter.allow()) {
                log.warn("Moderation audit database write failed; dropped event with hash={}",
                        event.getContentHash(), insertFailure);
            }
        }
    }

    private void logFailure(ModerationRequest request, ModerationResult result, Throwable cause) {
        log.warn("moderation audit failed requestId={} scene={} action={} exception={}",
                request.requestId(), request.scene(), result.finalAction(), cause.getClass().getName());
    }

    private ModerationEvent event(ModerationRequest request, ModerationResult result) {
        Instant now = clock.instant();
        ModerationEvent event = new ModerationEvent();
        event.setRequestId(request.requestId());
        event.setScene(request.scene().name());
        event.setResourceType(request.resourceType());
        event.setResourceId(request.resourceId());
        event.setContentHash(sha256(request.text()));
        event.setMaskedExcerpt(maskExcerpt(request.text()));
        event.setLocalDecision(result.localDecision().name());
        event.setFinalAction(result.finalAction().name());
        event.setRiskScore(result.riskScore());
        event.setCategories(result.categories().stream().sorted().collect(Collectors.joining(",")));
        if (result.matches() != null && !result.matches().isEmpty()) {
            String ruleIds = result.matches().stream()
                    .map(m -> m.ruleId())
                    .filter(Objects::nonNull)
                    .sorted()
                    .map(String::valueOf)
                    .distinct()
                    .collect(Collectors.joining(","));
            event.setMatchedRuleIds(ruleIds);
        } else {
            event.setMatchedRuleIds("");
        }
        event.setPolicyVersion(result.policyVersion());
        event.setDictionaryVersion(result.dictionaryVersion());
        if (result.providerResult() != null) {
            event.setProvider(result.providerResult().provider());
            event.setProviderRequestId(result.providerResult().providerRequestId());
            event.setProviderDecision(result.providerResult().decision().name());
            event.setProviderLatencyMs(result.providerResult().latencyMs());
        }
        event.setFallbackReason(result.fallbackReason());
        event.setExpireTime(Date.from(now.plus(Duration.ofDays(retentionDays))));
        event.setCreateTime(Date.from(now));
        return event;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 not available", impossible);
        }
    }

    public static String masked(String content, int maxExcerpt) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        int codePoints = content.codePointCount(0, content.length());
        if (codePoints <= 1) {
            return "*";
        }
        int keep = Math.min(Math.max(1, codePoints / 3), 3);
        int prefixEnd = content.offsetByCodePoints(0, Math.min(keep, codePoints - 1));
        String prefix = content.substring(0, prefixEnd);
        int maskCount = Math.max(1, Math.min(codePoints - keep, maxExcerpt - prefix.length()));
        String result = prefix + "*".repeat(maskCount);
        if (result.length() > maxExcerpt) {
            result = result.substring(0, maxExcerpt);
        }
        return result;
    }

    private static String maskExcerpt(String content) {
        return masked(content, MAX_MASKED_EXCERPT);
    }

    @FunctionalInterface
    public interface EventWriter {
        int write(ModerationEvent event);
    }
}
