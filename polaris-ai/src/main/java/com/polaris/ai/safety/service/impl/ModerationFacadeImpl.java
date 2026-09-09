package com.polaris.ai.safety.service.impl;

import com.polaris.ai.safety.dto.ModerationRequest;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.dto.ProviderResult;
import com.polaris.ai.safety.exception.ModerationUnavailableException;
import com.polaris.ai.safety.model.*;
import com.polaris.ai.safety.provider.ProviderReviewCoordinator;
import com.polaris.ai.safety.rule.ContextRiskScorer;
import com.polaris.ai.safety.rule.DictionarySnapshot;
import com.polaris.ai.safety.rule.DictionarySnapshotManager;
import com.polaris.ai.safety.rule.DictionarySnapshotManager.CapturedSnapshot;
import com.polaris.ai.safety.rule.LocalScore;
import com.polaris.ai.safety.service.*;
import com.polaris.ai.safety.stream.DefaultStreamingModerationSession;
import com.polaris.ai.safety.stream.StreamingModerationSession;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * AI 敏感内容机器安全检测统一门面服务层实现类
 *
 * @author polaris
 */
@Service
public class ModerationFacadeImpl implements IModerationFacade {

    @Autowired(required = false)
    private DictionarySnapshotManager snapshotManager;

    @Autowired(required = false)
    private IModerationPolicyService policyService;

    @Autowired(required = false)
    private ModerationDecisionEngine decisionEngine;

    @Autowired(required = false)
    private ProviderReviewCoordinator providerCoordinator;

    @Autowired(required = false)
    private IModerationAuditService auditService;

    @Autowired(required = false)
    private IModerationCandidateService candidateService;

    private LocalScoring scorer;

    public ModerationFacadeImpl() {
        this.decisionEngine = new ModerationDecisionEngine();
        this.scorer = new ContextRiskScorer()::score;
        this.providerCoordinator = ProviderReviewCoordinator.notConfigured();
    }

    public ModerationFacadeImpl(DictionarySnapshotManager snapshotManager,
                                IModerationPolicyService policyService,
                                ModerationDecisionEngine decisionEngine) {
        this(snapshotManager, policyService, decisionEngine, new ContextRiskScorer()::score,
                ProviderReviewCoordinator.notConfigured(), null, null);
    }

    public ModerationFacadeImpl(DictionarySnapshotManager snapshotManager,
                                IModerationPolicyService policyService,
                                ModerationDecisionEngine decisionEngine,
                                ProviderReviewCoordinator providerCoordinator) {
        this(snapshotManager, policyService, decisionEngine, new ContextRiskScorer()::score,
                providerCoordinator, null, null);
    }

    public ModerationFacadeImpl(DictionarySnapshotManager snapshotManager,
                                IModerationPolicyService policyService,
                                ModerationDecisionEngine decisionEngine,
                                LocalScoring scorer,
                                ProviderReviewCoordinator providerCoordinator,
                                IModerationAuditService auditService,
                                IModerationCandidateService candidateService) {
        this.snapshotManager = snapshotManager;
        this.policyService = policyService;
        this.decisionEngine = decisionEngine != null ? decisionEngine : new ModerationDecisionEngine();
        this.scorer = scorer != null ? scorer : new ContextRiskScorer()::score;
        this.providerCoordinator = providerCoordinator != null ? providerCoordinator : ProviderReviewCoordinator.notConfigured();
        this.auditService = auditService;
        this.candidateService = candidateService;
    }

    @PostConstruct
    public void init() {
        if (this.decisionEngine == null) {
            this.decisionEngine = new ModerationDecisionEngine();
        }
        if (this.scorer == null) {
            this.scorer = new ContextRiskScorer()::score;
        }
        if (this.providerCoordinator == null) {
            this.providerCoordinator = ProviderReviewCoordinator.notConfigured();
        }
    }

    public void setSnapshotManager(DictionarySnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }

    public void setPolicyService(IModerationPolicyService policyService) {
        this.policyService = policyService;
    }

    public void setDecisionEngine(ModerationDecisionEngine decisionEngine) {
        this.decisionEngine = decisionEngine;
    }

    public void setProviderCoordinator(ProviderReviewCoordinator providerCoordinator) {
        this.providerCoordinator = providerCoordinator;
    }

    public void setAuditService(IModerationAuditService auditService) {
        this.auditService = auditService;
    }

    public void setCandidateService(IModerationCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    public void setScorer(LocalScoring scorer) {
        this.scorer = scorer;
    }

    @Override
    public ModerationResult moderate(ModerationRequest request) {
        Objects.requireNonNull(request, "request");
        CapturedSnapshot captured;
        try {
            captured = captureSnapshot(request);
        } catch (ModerationUnavailableException unavailable) {
            return complete(request, unavailable.safeResult(), null);
        }

        ModerationRequest versioned = request.withDictionaryVersion(captured.version());
        ModerationPolicy policy = null;
        if (policyService != null) {
            try {
                policy = policyService.resolve(versioned.scene());
            } catch (ModerationUnavailableException unavailable) {
                return complete(request, unavailable.withSafeResult(
                        unavailableResult(request, captured, null)).safeResult(),
                        captured.snapshot());
            }
        }

        return evaluateResolved(versioned, captured, policy, true, true);
    }

    @Override
    public StreamingModerationSession openStream(ModerationRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.scene() != ModerationScene.AI_OUTPUT) {
            throw new IllegalArgumentException("Streaming moderation supports AI_OUTPUT only");
        }
        CapturedSnapshot captured = captureSnapshot(request);
        ModerationPolicy policy = policyService != null ? copyPolicy(policyService.resolve(ModerationScene.AI_OUTPUT)) : null;
        ModerationRequest versioned = request.withDictionaryVersion(captured.version());
        return new DefaultStreamingModerationSession(
                policy,
                captured.snapshot(),
                text -> evaluateResolved(withText(versioned, text), captured,
                        policy, true, true),
                text -> evaluateResolved(withText(versioned, text), captured,
                        policy, false, false),
                text -> evaluateLocal(withText(versioned, text), captured, policy));
    }

    @Override
    public StreamingModerationSession openStream(ModerationScene scene, String resourceType, String resourceId) {
        return openStream(ModerationRequest.forStream(scene, resourceType, resourceId));
    }

    private ModerationResult evaluateResolved(
            ModerationRequest versioned, CapturedSnapshot captured,
            ModerationPolicy policy, boolean allowProvider,
            boolean recordSideEffects) {

        LocalScore score;
        try {
            score = score(versioned, captured, policy);
        } catch (ModerationUnavailableException unavailable) {
            return recordSideEffects
                    ? complete(versioned, unavailable.safeResult(), captured.snapshot())
                    : unavailable.safeResult();
        }

        FinalAction action = decisionEngine != null ? decisionEngine.decide(versioned.scene(), score.decision(), policy) : FinalAction.ALLOW;
        ProviderResult providerResult = null;
        String fallback = null;
        Set<String> categories = score.categories();
        if (allowProvider && score.decision() == LocalDecision.SUSPECT && providerCoordinator != null) {
            ProviderReviewCoordinator.Review review = providerCoordinator.review(
                    policy, versioned.scene(), versioned.text(), versioned.requestId());
            providerResult = review.providerResult();
            fallback = review.fallbackReason();
            if (providerResult != null) {
                categories = union(score.categories(), providerResult.categories());
                if (providerResult.decision() == ProviderDecision.PASS) {
                    action = decisionEngine.decide(
                            versioned.scene(), LocalDecision.PASS, policy);
                } else if (providerResult.decision() == ProviderDecision.HIGH_RISK) {
                    action = decisionEngine.decide(
                            versioned.scene(), LocalDecision.HIGH_RISK, policy);
                }
            }
        }
        ModerationResult result = new ModerationResult(
                score.decision(), action, score.score(), categories, score.matches(),
                versioned.dictionaryVersion(), policy != null ? policy.getPolicyVersion() : null, providerResult, fallback);
        return recordSideEffects
                ? complete(versioned, result, captured.snapshot()) : result;
    }

    private ModerationResult complete(ModerationRequest request, ModerationResult result,
                                      DictionarySnapshot snapshot) {
        if (auditService != null) {
            try {
                auditService.record(request, result);
            } catch (RuntimeException ignored) {
            }
        }
        if (candidateService != null && snapshot != null) {
            try {
                candidateService.consider(request, result, snapshot);
            } catch (RuntimeException ignored) {
            }
        }
        return result;
    }

    private CapturedSnapshot captureSnapshot(ModerationRequest request) {
        try {
            if (snapshotManager == null) {
                throw new IllegalStateException("Dictionary snapshot manager is not configured");
            }
            CapturedSnapshot captured = snapshotManager.capture();
            if (captured == null) {
                throw new IllegalStateException("Dictionary snapshot is unavailable");
            }
            return captured;
        } catch (RuntimeException captureFailure) {
            throw unavailable("Dictionary snapshot capture failed",
                    unavailableResult(request, null, null), captureFailure);
        }
    }

    private LocalScore score(ModerationRequest request, CapturedSnapshot captured,
                             ModerationPolicy policy) {
        if (request.text() == null || request.text().isBlank()) {
            return new LocalScore(LocalDecision.PASS, 0, Set.of(), List.of());
        }
        try {
            if (scorer == null) {
                return new LocalScore(LocalDecision.PASS, 0, Set.of(), List.of());
            }
            return scorer.score(request.text(), captured.snapshot(), policy);
        } catch (RuntimeException scoringFailure) {
            throw unavailable("Local moderation scoring failed",
                    unavailableResult(request, captured, policy), scoringFailure);
        }
    }

    private LocalScore evaluateLocal(ModerationRequest request, CapturedSnapshot captured,
                                     ModerationPolicy policy) {
        try {
            return score(request, captured, policy);
        } catch (ModerationUnavailableException unavailable) {
            return new LocalScore(LocalDecision.HIGH_RISK, 100, Set.of(), List.of());
        }
    }

    private ModerationResult unavailableResult(
            ModerationRequest request, CapturedSnapshot captured, ModerationPolicy policy) {
        return new ModerationResult(
                LocalDecision.HIGH_RISK,
                decisionEngine != null ? decisionEngine.conservativeAction(request.scene()) : FinalAction.BLOCK,
                100,
                Set.of(),
                List.of(),
                captured == null ? null : captured.version(),
                policy == null ? null : policy.getPolicyVersion(),
                null,
                LOCAL_ENGINE_UNAVAILABLE);
    }

    private static ModerationUnavailableException unavailable(
            String message, ModerationResult safeResult, RuntimeException cause) {
        return new ModerationUnavailableException(message, safeResult, cause);
    }

    private static Set<String> union(Set<String> local, Set<String> provider) {
        Set<String> combined = new HashSet<>(local);
        if (provider != null) {
            combined.addAll(provider);
        }
        return Set.copyOf(combined);
    }

    private static ModerationRequest withText(ModerationRequest request, String text) {
        return new ModerationRequest(request.scene(), text, request.requestId(),
                request.resourceType(), request.resourceId(), request.dictionaryVersion());
    }

    private static ModerationPolicy copyPolicy(ModerationPolicy source) {
        if (source == null) return null;
        ModerationPolicy copy = new ModerationPolicy();
        copy.setId(source.getId());
        copy.setScene(source.getScene());
        copy.setPreset(source.getPreset());
        copy.setMode(source.getMode());
        copy.setEnabled(source.getEnabled());
        copy.setSuspectThreshold(source.getSuspectThreshold());
        copy.setBlockThreshold(source.getBlockThreshold());
        copy.setProviderEnabled(source.getProviderEnabled());
        copy.setProviderTimeoutMs(source.getProviderTimeoutMs());
        copy.setProviderDailyLimit(source.getProviderDailyLimit());
        copy.setProviderMonthlyBudget(source.getProviderMonthlyBudget());
        copy.setSegmentChars(source.getSegmentChars());
        copy.setSegmentOverlapChars(source.getSegmentOverlapChars());
        copy.setOutputBufferChars(source.getOutputBufferChars());
        copy.setQuarantineDays(source.getQuarantineDays());
        copy.setPolicyVersion(source.getPolicyVersion());
        copy.setCreateTime(source.getCreateTime() == null ? null
                : new java.util.Date(source.getCreateTime().getTime()));
        copy.setUpdateTime(source.getUpdateTime() == null ? null
                : new java.util.Date(source.getUpdateTime().getTime()));
        return copy;
    }

    @FunctionalInterface
    public interface LocalScoring {
        LocalScore score(String raw, com.polaris.ai.safety.rule.DictionarySnapshot snapshot,
                         ModerationPolicy policy);
    }
}
