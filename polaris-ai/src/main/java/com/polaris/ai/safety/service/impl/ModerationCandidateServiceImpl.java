package com.polaris.ai.safety.service.impl;

import com.polaris.ai.safety.config.ModerationProperties;
import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.dto.ProviderResult;
import com.polaris.ai.safety.mapper.ModerationCandidateMapper;
import com.polaris.ai.safety.model.LocalDecision;
import com.polaris.ai.safety.model.ModerationCandidate;
import com.polaris.ai.safety.model.ProviderDecision;
import com.polaris.ai.safety.rule.DictionarySnapshot;
import com.polaris.ai.safety.rule.LocalSensitiveMatcher;
import com.polaris.ai.safety.rule.TextNormalizer;
import com.polaris.ai.safety.service.IModerationCandidateService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * AI 敏感词候选词发现与观察服务层实现类
 *
 * @author polaris
 */
@Service
public class ModerationCandidateServiceImpl implements IModerationCandidateService {
    private static final int MAX_CANDIDATE_TERM = 256;
    private static final int MAX_MASKED_EXCERPT = 120;

    @Autowired(required = false)
    private ModerationCandidateMapper mapper;

    @Autowired(required = false)
    private ModerationProperties properties;

    private CandidateWriter writer;
    private int observationThreshold = 3;
    private LocalSensitiveMatcher matcher = new LocalSensitiveMatcher();
    private TextNormalizer normalizer = new TextNormalizer();
    private Clock clock = Clock.systemUTC();
    private boolean enabled = true;

    public ModerationCandidateServiceImpl() {}

    public ModerationCandidateServiceImpl(CandidateWriter writer, int observationThreshold) {
        this.writer = writer;
        this.observationThreshold = observationThreshold;
    }

    @PostConstruct
    public void init() {
        if (this.writer == null && this.mapper != null) {
            this.writer = this.mapper::observe;
        }
        if (this.properties != null && this.properties.getCandidateObservationThreshold() > 0) {
            this.observationThreshold = this.properties.getCandidateObservationThreshold();
        }
    }

    public void setWriter(CandidateWriter writer) {
        this.writer = writer;
    }

    public void setObservationThreshold(int observationThreshold) {
        this.observationThreshold = observationThreshold;
    }

    public void setMatcher(LocalSensitiveMatcher matcher) {
        this.matcher = matcher;
    }

    public void setNormalizer(TextNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void consider(ModerationRequest request, ModerationResult result,
                         DictionarySnapshot snapshot) {
        if (!enabled || request == null || result == null || snapshot == null || writer == null) {
            return;
        }
        if (result.localDecision() == LocalDecision.PASS) {
            return;
        }
        if (result.localDecision() == LocalDecision.HIGH_RISK) {
            return;
        }
        if (result.providerResult() != null && result.providerResult().decision() == ProviderDecision.PASS) {
            return;
        }
        String signal = signal(result);
        if (signal == null) {
            return;
        }
        Set<String> terms = discoverCandidateTerms(request, result, snapshot);
        if (terms.isEmpty()) {
            return;
        }
        Date now = Date.from(clock.instant());
        for (String term : terms) {
            ModerationCandidate candidate = new ModerationCandidate();
            candidate.setCandidateTerm(term);
            candidate.setExpressionHash(sha256(request.text()));
            candidate.setMaskedExcerpt(maskExcerpt(request.text()));
            candidate.setCategory(category(result));
            candidate.setSourceSignal(signal);
            candidate.setObservationCount(1);
            candidate.setStatus("PROVIDER_HIGH_RISK".equals(signal) ? "PENDING" : "OBSERVING");
            candidate.setCreateTime(now);
            candidate.setUpdateTime(now);
            try {
                writer.observe(candidate, observationThreshold);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private Set<String> discoverCandidateTerms(ModerationRequest request,
                                               ModerationResult result,
                                               DictionarySnapshot snapshot) {
        Set<String> discovered = new LinkedHashSet<>();
        if (request.text() == null || request.text().isBlank()) {
            return discovered;
        }
        if (result.providerResult() != null && result.providerResult().decision() == ProviderDecision.HIGH_RISK) {
            String masked = maskExcerpt(request.text());
            discovered.add(masked);
            return discovered;
        }
        if (result.localDecision() == LocalDecision.SUSPECT) {
            String hash = sha256(request.text());
            discovered.add("obf_" + hash.substring(0, 16));
            return discovered;
        }
        return discovered;
    }

    private static String signal(ModerationResult result) {
        ProviderResult provider = result.providerResult();
        if (provider != null && provider.decision() == ProviderDecision.HIGH_RISK) {
            return "PROVIDER_HIGH_RISK";
        }
        if (result.localDecision() == LocalDecision.SUSPECT) {
            return "LOCAL_OBFUSCATION_REPEAT";
        }
        return null;
    }

    private static String category(ModerationResult result) {
        return result.categories().stream().sorted().findFirst().orElse("GENERAL");
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 not available", impossible);
        }
    }

    private static String maskExcerpt(String content) {
        int length = Math.min(content.length(), MAX_MASKED_EXCERPT);
        if (length == 0) {
            return "";
        }
        int keep = Math.min(3, length);
        String prefix = content.substring(0, keep);
        return prefix + "*".repeat(length - keep);
    }

    @FunctionalInterface
    public interface CandidateWriter {
        int observe(ModerationCandidate candidate, int observationThreshold);
    }
}
