package com.polaris.ai.safety.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.safety.config.ModerationProperties;
import com.polaris.ai.safety.dto.CandidateBatchRequest;
import com.polaris.ai.safety.dto.ModerationRuleRequest;
import com.polaris.ai.safety.mapper.ModerationCandidateMapper;
import com.polaris.ai.safety.mapper.ModerationDictionaryVersionMapper;
import com.polaris.ai.safety.mapper.ModerationPolicyMapper;
import com.polaris.ai.safety.mapper.ModerationRuleMapper;
import com.polaris.ai.safety.model.*;
import com.polaris.ai.safety.rule.DictionarySnapshot;
import com.polaris.ai.safety.rule.DictionarySnapshotManager;
import com.polaris.ai.safety.rule.TextNormalizer;
import com.polaris.ai.safety.service.DictionarySeedSource;
import com.polaris.ai.safety.service.IDictionaryVersionNotifier;
import com.polaris.ai.safety.service.IDictionaryVersionService;
import com.polaris.ai.safety.vo.DictionaryVersionDiffView;
import com.polaris.ai.safety.vo.DictionaryVersionView;
import com.polaris.common.exception.ServiceException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Supplier;

/**
 * AI 敏感词库版本与规则管理服务层实现类
 *
 * @author polaris
 */
@Service
public class DictionaryVersionServiceImpl extends ServiceImpl<ModerationDictionaryVersionMapper, ModerationDictionaryVersion>
        implements IDictionaryVersionService {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String ARCHIVED = "ARCHIVED";

    @Autowired(required = false)
    private ModerationDictionaryVersionMapper versionMapper;

    @Autowired(required = false)
    private ModerationRuleMapper ruleMapper;

    @Autowired(required = false)
    private ModerationPolicyMapper policyMapper;

    @Autowired(required = false)
    private ObjectProvider<ModerationCandidateMapper> candidateMappers;

    @Autowired(required = false)
    private ModerationProperties properties;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    private ObjectProvider<DictionarySnapshotManager> snapshots;

    @Autowired(required = false)
    private ObjectProvider<IDictionaryVersionNotifier> notifiers;

    private Persistence persistence;
    private TransactionBoundary transactions = Supplier::get;
    private SnapshotReloader snapshotReloader = () -> 0L;
    private ModerationCandidateMapper candidateMapper;

    @PostConstruct
    public void init() {
        if (this.persistence == null && this.versionMapper != null) {
            ModerationCandidateMapper cm = candidateMappers != null ? candidateMappers.getIfAvailable() : null;
            this.candidateMapper = cm;
            this.persistence = new MapperPersistence(versionMapper, ruleMapper, policyMapper, cm);
        }
        if (this.transactionManager != null) {
            this.transactions = springTransactions(transactionManager);
        }
        if (this.snapshots != null) {
            this.snapshotReloader = () -> {
                DictionarySnapshotManager mgr = snapshots.getIfAvailable();
                long ver = mgr != null ? mgr.reloadPublished() : 0L;
                IDictionaryVersionNotifier notifier = notifiers != null ? notifiers.getIfAvailable() : null;
                if (notifier != null) {
                    notifier.publish(ver);
                }
                return ver;
            };
        }
    }

    public void setPersistence(Persistence persistence) {
        this.persistence = persistence;
    }

    public void setProperties(ModerationProperties properties) {
        this.properties = properties;
    }

    public void setTransactions(TransactionBoundary transactions) {
        this.transactions = transactions;
    }

    public void setSnapshotReloader(SnapshotReloader snapshotReloader) {
        this.snapshotReloader = snapshotReloader;
    }

    public void setCandidateMapper(ModerationCandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }

    @Override
    public List<ModerationDictionaryVersion> findPublished() {
        return persistence != null ? List.copyOf(persistence.findPublished()) : List.of();
    }

    @Override
    public boolean checksumExists(String checksum) {
        return persistence != null && persistence.findByChecksum(checksum) != null;
    }

    @Override
    public List<DictionaryVersionView> listVersionViews() {
        if (persistence == null) return List.of();
        List<ModerationDictionaryVersion> versions = persistence.findAllVersions();
        List<DictionaryVersionView> views = new ArrayList<>();
        for (ModerationDictionaryVersion v : versions) {
            int count = persistence.countRules(v.getId());
            views.add(new DictionaryVersionView(
                    v.getId(),
                    v.getVersionNo(),
                    v.getStatus(),
                    v.getSourceVersion(),
                    count,
                    v.getPublishedBy(),
                    v.getPublishedTime(),
                    v.getCreateTime()
            ));
        }
        return views;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long getOrCreateDraft(Long baseVersionId, String operator) {
        if (persistence == null) return null;
        List<ModerationDictionaryVersion> drafts = persistence.findDrafts();
        if (!drafts.isEmpty()) {
            return drafts.get(0).getId();
        }

        ModerationDictionaryVersion base = null;
        if (baseVersionId != null) {
            base = persistence.findVersionById(baseVersionId);
        }
        if (base == null) {
            List<ModerationDictionaryVersion> published = persistence.findPublished();
            if (!published.isEmpty()) {
                base = published.get(0);
            }
        }

        ModerationDictionaryVersion draft = new ModerationDictionaryVersion();
        String nextVer = "draft-" + System.currentTimeMillis();
        draft.setVersionNo(nextVer);
        draft.setStatus(DRAFT);
        draft.setSourceVersion(base != null ? "Clone from " + base.getVersionNo() : "Manual Draft");
        draft.setCreateTime(new Date());
        persistence.insertVersion(draft);

        if (base != null && draft.getId() != null) {
            persistence.copyRules(base.getId(), draft.getId());
        }

        return draft.getId();
    }

    @Override
    public List<ModerationRule> listRules(Long versionId, String keyword, String ruleType, String category) {
        if (versionId == null || persistence == null) {
            return List.of();
        }
        return persistence.findRulesFiltered(versionId, keyword, ruleType, category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModerationRule addRule(Long versionId, ModerationRuleRequest req, String operator) {
        assertDraft(versionId);
        validateRuleRequest(req);

        TextNormalizer normalizer = new TextNormalizer();
        String normalized = normalizer.normalize(req.content()).text();
        if (normalized.isBlank()) {
            throw new ServiceException("规则内容归一化后为空");
        }
        String hash = sha256(normalized);

        if (persistence != null && persistence.countRuleByHash(versionId, req.ruleType().name(), hash) > 0) {
            throw new ServiceException("当前草稿中已存在相同规则: [" + req.content().trim() + "]");
        }

        ModerationRule rule = new ModerationRule();
        rule.setDictionaryVersionId(versionId);
        rule.setRuleType(req.ruleType().name());
        rule.setContent(req.content().trim());
        rule.setNormalizedContent(normalized);
        rule.setNormalizedHash(hash);
        rule.setCategory(req.category() != null ? req.category().trim() : "GENERAL");
        rule.setWeight(req.weight());
        rule.setSource("ADMIN");
        rule.setCreateTime(new Date());
        persistence.insertRule(rule);
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModerationRule updateRule(Long versionId, Long ruleId, ModerationRuleRequest req, String operator) {
        assertDraft(versionId);
        validateRuleRequest(req);

        ModerationRule rule = persistence.findRuleById(ruleId);
        if (rule == null || !Objects.equals(rule.getDictionaryVersionId(), versionId)) {
            throw new ServiceException("规则不存在或不属于当前版本");
        }

        TextNormalizer normalizer = new TextNormalizer();
        String normalized = normalizer.normalize(req.content()).text();
        if (normalized.isBlank()) {
            throw new ServiceException("规则内容归一化后为空");
        }
        String hash = sha256(normalized);

        rule.setRuleType(req.ruleType().name());
        rule.setContent(req.content().trim());
        rule.setNormalizedContent(normalized);
        rule.setNormalizedHash(hash);
        rule.setCategory(req.category() != null ? req.category().trim() : "GENERAL");
        rule.setWeight(req.weight());
        rule.setUpdateTime(new Date());
        persistence.updateRule(rule);
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long versionId, Long ruleId, String operator) {
        assertDraft(versionId);
        ModerationRule rule = persistence.findRuleById(ruleId);
        if (rule == null || !Objects.equals(rule.getDictionaryVersionId(), versionId)) {
            throw new ServiceException("规则不存在或不属于当前版本");
        }
        persistence.deleteRule(ruleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAcceptCandidates(CandidateBatchRequest req, String operator) {
        if (req == null || req.candidateIds() == null || req.candidateIds().isEmpty()) {
            throw new ServiceException("候选词ID列表不能为空");
        }
        if (req.targetDraftVersionId() == null) {
            throw new ServiceException("目标草稿版本ID不能为空");
        }
        assertDraft(req.targetDraftVersionId());

        RuleType ruleType = req.ruleType() != null ? req.ruleType() : RuleType.RISK_WORD;
        int weight = req.weight() != null ? req.weight() : 40;

        TextNormalizer normalizer = new TextNormalizer();
        for (Long id : req.candidateIds()) {
            ModerationCandidate candidate = candidateMapper != null ? candidateMapper.selectById(id) : null;
            if (candidate != null && !"ACCEPTED".equals(candidate.getStatus())) {
                String normalized = normalizer.normalize(candidate.getCandidateTerm()).text();
                if (!normalized.isBlank()) {
                    String hash = sha256(normalized);
                    if (persistence == null || persistence.countRuleByHash(req.targetDraftVersionId(), ruleType.name(), hash) == 0) {
                        ModerationRule rule = new ModerationRule();
                        rule.setDictionaryVersionId(req.targetDraftVersionId());
                        rule.setRuleType(ruleType.name());
                        rule.setContent(candidate.getCandidateTerm().trim());
                        rule.setNormalizedContent(normalized);
                        rule.setNormalizedHash(hash);
                        rule.setCategory(req.category() != null ? req.category() : (candidate.getCategory() != null ? candidate.getCategory() : "GENERAL"));
                        rule.setWeight(weight);
                        rule.setSource("CANDIDATE");
                        rule.setCreateTime(new Date());
                        persistence.insertRule(rule);
                    }
                }
                candidate.setStatus("ACCEPTED");
                candidate.setUpdateTime(new Date());
                candidateMapper.updateById(candidate);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRejectCandidates(CandidateBatchRequest req, String operator) {
        if (req == null || req.candidateIds() == null || req.candidateIds().isEmpty()) {
            throw new ServiceException("候选词ID列表不能为空");
        }
        for (Long id : req.candidateIds()) {
            ModerationCandidate candidate = candidateMapper != null ? candidateMapper.selectById(id) : null;
            if (candidate != null) {
                candidate.setStatus("REJECTED");
                candidate.setUpdateTime(new Date());
                candidateMapper.updateById(candidate);
            }
        }
    }

    private void assertDraft(Long versionId) {
        if (versionId == null) {
            throw new ServiceException("版本ID不能为空");
        }
        ModerationDictionaryVersion version = persistence != null ? persistence.findVersionById(versionId) : null;
        if (version == null) {
            throw new ServiceException("指定的词库版本不存在");
        }
        if (!DRAFT.equals(version.getStatus())) {
            throw new ServiceException("已发布或已归档版本不允许直接修改规则，请创建草稿");
        }
    }

    private void validateRuleRequest(ModerationRuleRequest req) {
        if (req == null || req.content() == null || req.content().isBlank()) {
            throw new ServiceException("规则文本内容不能为空");
        }
        if (req.ruleType() == null) {
            throw new ServiceException("规则类型不能为空");
        }
        if (!validWeight(req.ruleType(), req.weight())) {
            throw new ServiceException("规则类型与权重不匹配 (违规词需>0, 安全语境需<0, 放行词需=0)");
        }
    }

    @Override
    public ImportOutcome importSeed(DictionarySeedSource.SeedBundle seed, boolean publishIfNoPublished) {
        Objects.requireNonNull(seed, "seed");
        return transact(() -> {
            ModerationDictionaryVersion existing = persistence.findByChecksum(seed.checksum());
            if (existing != null) {
                return new ImportOutcome(existing.getId(), false, PUBLISHED.equals(existing.getStatus()));
            }
            boolean publish = publishIfNoPublished && persistence.findPublished().isEmpty();
            ModerationDictionaryVersion version = new ModerationDictionaryVersion();
            version.setVersionNo(seed.sourceVersion());
            version.setChecksum(seed.checksum());
            version.setStatus(DRAFT);
            version.setSourceVersion(seed.sourceVersion());
            version.setSourceLocation("classpath:" + DictionarySeedSource.RESOURCE_LOCATION);
            persistence.insertVersion(version);
            if (version.getId() == null || version.getId() <= 0) {
                throw new IllegalStateException("Inserted dictionary version has no positive database id");
            }
            for (ModerationRule rule : seed.persistedRules(version.getId())) {
                persistence.insertRule(rule);
            }
            if (publish) {
                for (ModerationPolicy policy : initialPolicies()) {
                    persistence.insertPolicy(policy);
                }
                version.setStatus(PUBLISHED);
                version.setPublishedTime(new Date());
                persistence.updateVersion(version);
            }
            return new ImportOutcome(version.getId(), true, publish);
        });
    }

    @Override
    public DictionaryVersionDiffView diffVersion(Long targetVersionId, Long baseVersionId) {
        if (targetVersionId == null || persistence == null) {
            throw new ServiceException("目标版本ID不能为空");
        }
        ModerationDictionaryVersion targetVer = persistence.findVersionById(targetVersionId);
        if (targetVer == null) {
            throw new ServiceException("目标版本不存在");
        }

        ModerationDictionaryVersion baseVer = null;
        if (baseVersionId != null) {
            baseVer = persistence.findVersionById(baseVersionId);
        } else {
            List<ModerationDictionaryVersion> published = persistence.findPublished();
            if (!published.isEmpty()) {
                baseVer = published.get(0);
            }
        }

        List<ModerationRule> targetRules = persistence.findRules(targetVersionId);
        List<ModerationRule> baseRules = baseVer != null ? persistence.findRules(baseVer.getId()) : List.of();

        Map<String, ModerationRule> baseMap = new LinkedHashMap<>();
        for (ModerationRule r : baseRules) {
            String key = r.getRuleType() + ":" + r.getNormalizedHash();
            baseMap.put(key, r);
        }

        Map<String, ModerationRule> targetMap = new LinkedHashMap<>();
        for (ModerationRule r : targetRules) {
            String key = r.getRuleType() + ":" + r.getNormalizedHash();
            targetMap.put(key, r);
        }

        List<ModerationRule> addedRules = new ArrayList<>();
        List<DictionaryVersionDiffView.RuleDiffItem> modifiedRules = new ArrayList<>();

        for (Map.Entry<String, ModerationRule> entry : targetMap.entrySet()) {
            ModerationRule baseRule = baseMap.get(entry.getKey());
            if (baseRule == null) {
                addedRules.add(entry.getValue());
            } else {
                if (!Objects.equals(baseRule.getWeight(), entry.getValue().getWeight())
                        || !Objects.equals(baseRule.getCategory(), entry.getValue().getCategory())) {
                    modifiedRules.add(new DictionaryVersionDiffView.RuleDiffItem(
                            entry.getValue().getContent(),
                            baseRule.getCategory(),
                            entry.getValue().getCategory(),
                            baseRule.getRuleType(),
                            entry.getValue().getRuleType(),
                            baseRule.getWeight(),
                            entry.getValue().getWeight()
                    ));
                }
            }
        }

        List<ModerationRule> removedRules = new ArrayList<>();
        for (Map.Entry<String, ModerationRule> entry : baseMap.entrySet()) {
            if (!targetMap.containsKey(entry.getKey())) {
                removedRules.add(entry.getValue());
            }
        }

        return new DictionaryVersionDiffView(
                baseVer != null ? baseVer.getId() : null,
                baseVer != null ? baseVer.getVersionNo() : "无基准版本",
                targetVer.getId(),
                targetVer.getVersionNo(),
                addedRules.size(),
                removedRules.size(),
                modifiedRules.size(),
                addedRules,
                removedRules,
                modifiedRules
        );
    }

    @Override
    public void exportRules(Long versionId, jakarta.servlet.http.HttpServletResponse response) {
        if (versionId == null || persistence == null) {
            throw new ServiceException("版本ID不能为空");
        }
        ModerationDictionaryVersion ver = persistence.findVersionById(versionId);
        if (ver == null) {
            throw new ServiceException("词库版本不存在");
        }
        List<ModerationRule> rules = persistence.findRules(versionId);

        try {
            response.setContentType("text/csv;charset=utf-8");
            String filename = java.net.URLEncoder.encode("rules-" + ver.getVersionNo() + ".csv", java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + filename);

            try (java.io.OutputStream os = response.getOutputStream()) {
                os.write(0xEF);
                os.write(0xBB);
                os.write(0xBF);

                java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8));
                writer.println("ruleType,content,category,weight,source");
                for (ModerationRule r : rules) {
                    String safeContent = r.getContent() != null ? r.getContent().replace("\"", "\"\"") : "";
                    if (safeContent.contains(",") || safeContent.contains("\"") || safeContent.contains("\n")) {
                        safeContent = "\"" + safeContent + "\"";
                    }
                    writer.println(String.format("%s,%s,%s,%d,%s",
                            r.getRuleType() != null ? r.getRuleType() : "RISK_WORD",
                            safeContent,
                            r.getCategory() != null ? r.getCategory() : "GENERAL",
                            r.getWeight() != null ? r.getWeight() : 40,
                            r.getSource() != null ? r.getSource() : "MANUAL"
                    ));
                }
                writer.flush();
            }
        } catch (Exception e) {
            throw new ServiceException("导出词库规则文件失败: " + e.getMessage());
        }
    }

    @Override
    public PublicationOutcome publish(long versionId, String operator) {
        return publishTo(versionId, operator, DRAFT);
    }

    @Override
    public PublicationOutcome rollback(long versionId, String operator) {
        return publishTo(versionId, operator, ARCHIVED);
    }

    private PublicationOutcome publishTo(long versionId, String operator,
                                         String requiredTargetStatus) {
        if (versionId <= 0) {
            throw new IllegalArgumentException("Dictionary version id must be positive");
        }
        String actor = operator == null ? "" : operator.trim();
        if (actor.isEmpty() || actor.length() > 100) {
            throw new IllegalArgumentException("Dictionary publication operator is required");
        }
        PublicationOutcome outcome = transact(() -> {
            List<ModerationDictionaryVersion> versions = persistence.findVersionsForUpdate();
            List<ModerationDictionaryVersion> published = versions.stream()
                    .filter(version -> PUBLISHED.equals(version.getStatus())).toList();
            if (published.size() > 1) {
                throw new IllegalStateException("Multiple published dictionaries detected");
            }
            ModerationDictionaryVersion current = published.isEmpty() ? null : published.get(0);
            ModerationDictionaryVersion target = versions.stream()
                    .filter(version -> Objects.equals(versionId, version.getId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "Target dictionary version does not exist"));
            if (PUBLISHED.equals(target.getStatus())) {
                if (current != null && !Objects.equals(current.getId(), target.getId())) {
                    throw new IllegalStateException("Multiple published dictionaries detected");
                }
                return new PublicationOutcome(versionId, false);
            }
            if (!requiredTargetStatus.equals(target.getStatus())) {
                throw new IllegalStateException("Target dictionary version has invalid status");
            }
            validateSnapshot(versionId, persistence.findRules(versionId));

            if (current != null) {
                current.setStatus(ARCHIVED);
                persistence.updateVersion(current);
            }
            target.setStatus(PUBLISHED);
            target.setPublishedBy(actor);
            target.setPublishedTime(new Date());
            persistence.updateVersion(target);
            return new PublicationOutcome(versionId, true);
        });
        try {
            long loadedVersion = snapshotReloader.reloadPublished();
            if (loadedVersion != versionId) {
                throw new IllegalStateException("Reloaded an unexpected dictionary version");
            }
        } catch (RuntimeException reloadFailure) {
            throw new IllegalStateException(
                    "Dictionary publication committed but local snapshot reload failed; retry publication",
                    reloadFailure);
        }
        return outcome;
    }

    @Override
    public DictionarySnapshotManager.CapturedSnapshot loadPublished() {
        List<ModerationDictionaryVersion> published = persistence.findPublished();
        if (published.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one published dictionary version, found " + published.size());
        }
        ModerationDictionaryVersion version = published.get(0);
        if (version.getId() == null || version.getId() <= 0) {
            throw new IllegalStateException("Published dictionary version must have a positive database id");
        }
        List<ModerationRule> rules = persistence.findRules(version.getId());
        DictionarySnapshot snapshot = validateSnapshot(version.getId(), rules);
        return new DictionarySnapshotManager.CapturedSnapshot(version.getId(), snapshot);
    }

    private List<ModerationPolicy> initialPolicies() {
        List<ModerationPolicy> result = new ArrayList<>();
        if (properties == null || properties.getScenes() == null) return result;
        for (ModerationScene scene : ModerationScene.values()) {
            ModerationProperties.ScenePolicy defaults = properties.getScenes().get(scene);
            if (defaults == null) {
                throw new IllegalStateException("Missing default moderation policy for scene " + scene);
            }
            ModerationPolicy policy = new ModerationPolicy();
            policy.setScene(scene.name());
            policy.setPreset(Objects.requireNonNull(defaults.getPreset(), "policy preset").name());
            policy.setMode(PolicyMode.OBSERVE.name());
            policy.setEnabled(defaults.isEnabled());
            policy.setSuspectThreshold(defaults.getSuspectThreshold());
            policy.setBlockThreshold(defaults.getBlockThreshold());
            policy.setProviderEnabled(defaults.isProviderEnabled());
            policy.setProviderTimeoutMs(defaults.getProviderTimeoutMs());
            policy.setProviderDailyLimit(defaults.getProviderDailyLimit());
            policy.setProviderMonthlyBudget(defaults.getProviderMonthlyBudget());
            policy.setSegmentChars(defaults.getSegmentChars());
            policy.setSegmentOverlapChars(defaults.getSegmentOverlapChars());
            policy.setOutputBufferChars(defaults.getOutputBufferChars());
            policy.setQuarantineDays(defaults.getQuarantineDays());
            policy.setPolicyVersion(1L);
            result.add(policy);
        }
        return result;
    }

    private static void validatePublishedRules(long versionId, List<ModerationRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalStateException("Published dictionary must contain rules");
        }
        TextNormalizer normalizer = new TextNormalizer();
        Set<String> identities = new HashSet<>();
        for (ModerationRule rule : rules) {
            if (rule == null || rule.getId() == null || rule.getId() <= 0
                    || !Objects.equals(versionId, rule.getDictionaryVersionId())) {
                throw new IllegalStateException("Published rule has invalid database identity");
            }
            RuleType type;
            try {
                type = RuleType.valueOf(rule.getRuleType());
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Published rule has invalid type", exception);
            }
            String normalized = normalizer.normalize(rule.getContent()).text();
            if (normalized.isBlank() || !normalized.equals(rule.getNormalizedContent())) {
                throw new IllegalStateException("Published rule has invalid normalized content");
            }
            if (!sha256(normalized).equals(rule.getNormalizedHash())) {
                throw new IllegalStateException("Published rule has invalid normalized hash");
            }
            if (rule.getCategory() == null || !rule.getCategory().matches("[A-Z][A-Z0-9_]*")) {
                throw new IllegalStateException("Published rule has invalid category");
            }
            if (rule.getWeight() == null || !validWeight(type, rule.getWeight())) {
                throw new IllegalStateException("Published rule has invalid weight");
            }
            String identity = type.name() + '\0' + normalized + '\0' + rule.getCategory();
            if (!identities.add(identity)) {
                throw new IllegalStateException("Published dictionary has duplicate canonical rules");
            }
        }
    }

    private static DictionarySnapshot validateSnapshot(long versionId,
                                                        List<ModerationRule> rules) {
        validatePublishedRules(versionId, rules);
        return DictionarySnapshot.from(rules);
    }

    private static boolean validWeight(RuleType type, int weight) {
        return switch (type) {
            case RISK_WORD, RISK_CONTEXT -> weight > 0;
            case SAFE_CONTEXT -> weight < 0;
            case ALLOW_TERM -> weight == 0;
        };
    }

    private static String sha256(String text) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T transact(Supplier<T> work) {
        if (transactions == null) {
            return work.get();
        }
        return (T) transactions.execute(work);
    }

    private static TransactionBoundary springTransactions(PlatformTransactionManager manager) {
        if (manager == null) {
            return Supplier::get;
        }
        TransactionTemplate template = new TransactionTemplate(manager);
        return work -> template.execute(status -> work.get());
    }

    @FunctionalInterface
    public interface TransactionBoundary {
        Object execute(Supplier<?> work);
    }

    @FunctionalInterface
    public interface SnapshotReloader {
        long reloadPublished();
    }

    public interface Persistence {
        List<ModerationDictionaryVersion> findPublished();
        default List<ModerationDictionaryVersion> findAllVersions() { return findPublished(); }
        default List<ModerationDictionaryVersion> findDrafts() { return List.of(); }
        default ModerationDictionaryVersion findVersionById(Long id) { return null; }

        default List<ModerationDictionaryVersion> findVersionsForUpdate() {
            return findPublished();
        }

        ModerationDictionaryVersion findByChecksum(String checksum);

        void insertVersion(ModerationDictionaryVersion version);

        void updateVersion(ModerationDictionaryVersion version);

        void insertRule(ModerationRule rule);
        default void updateRule(ModerationRule rule) {}
        default void deleteRule(Long ruleId) {}
        default ModerationRule findRuleById(Long ruleId) { return null; }
        default int countRules(Long versionId) { return 0; }

        void insertPolicy(ModerationPolicy policy);

        List<ModerationRule> findRules(long dictionaryVersionId);
        default List<ModerationRule> findRulesFiltered(long dictionaryVersionId, String keyword, String ruleType, String category) {
            return findRules(dictionaryVersionId);
        }
        default void copyRules(Long sourceVersionId, Long targetVersionId) {}
        default int countRuleByHash(Long versionId, String ruleType, String normalizedHash) { return 0; }
    }

    public static final class MapperPersistence implements Persistence {
        private final ModerationDictionaryVersionMapper versionMapper;
        private final ModerationRuleMapper ruleMapper;
        private final ModerationPolicyMapper policyMapper;
        private final ModerationCandidateMapper candidateMapper;

        public MapperPersistence(ModerationDictionaryVersionMapper versionMapper,
                                 ModerationRuleMapper ruleMapper,
                                 ModerationPolicyMapper policyMapper,
                                 ModerationCandidateMapper candidateMapper) {
            this.versionMapper = versionMapper;
            this.ruleMapper = ruleMapper;
            this.policyMapper = policyMapper;
            this.candidateMapper = candidateMapper;
        }

        @Override
        public void copyRules(Long sourceVersionId, Long targetVersionId) {
            if (ruleMapper != null && sourceVersionId != null && targetVersionId != null) {
                ruleMapper.copyRulesFromVersion(sourceVersionId, targetVersionId);
            }
        }

        @Override
        public int countRuleByHash(Long versionId, String ruleType, String normalizedHash) {
            if (ruleMapper == null || versionId == null || normalizedHash == null) return 0;
            LambdaQueryWrapper<ModerationRule> query = new LambdaQueryWrapper<ModerationRule>()
                    .eq(ModerationRule::getDictionaryVersionId, versionId)
                    .eq(ModerationRule::getNormalizedHash, normalizedHash);
            if (ruleType != null && !ruleType.isBlank()) {
                query.eq(ModerationRule::getRuleType, ruleType);
            }
            Long count = ruleMapper.selectCount(query);
            return count != null ? count.intValue() : 0;
        }

        @Override
        public List<ModerationDictionaryVersion> findPublished() {
            if (versionMapper == null) return List.of();
            return versionMapper.selectList(new LambdaQueryWrapper<ModerationDictionaryVersion>()
                    .eq(ModerationDictionaryVersion::getStatus, PUBLISHED)
                    .orderByAsc(ModerationDictionaryVersion::getId));
        }

        @Override
        public List<ModerationDictionaryVersion> findAllVersions() {
            if (versionMapper == null) return List.of();
            return versionMapper.selectList(new LambdaQueryWrapper<ModerationDictionaryVersion>()
                    .orderByDesc(ModerationDictionaryVersion::getId));
        }

        @Override
        public List<ModerationDictionaryVersion> findDrafts() {
            if (versionMapper == null) return List.of();
            return versionMapper.selectList(new LambdaQueryWrapper<ModerationDictionaryVersion>()
                    .eq(ModerationDictionaryVersion::getStatus, DRAFT)
                    .orderByDesc(ModerationDictionaryVersion::getId));
        }

        @Override
        public ModerationDictionaryVersion findVersionById(Long id) {
            if (versionMapper == null || id == null) return null;
            return versionMapper.selectById(id);
        }

        @Override
        public List<ModerationDictionaryVersion> findVersionsForUpdate() {
            if (versionMapper == null) return List.of();
            return versionMapper.selectList(new LambdaQueryWrapper<ModerationDictionaryVersion>()
                    .last("FOR UPDATE"));
        }

        @Override
        public ModerationDictionaryVersion findByChecksum(String checksum) {
            if (versionMapper == null || checksum == null) return null;
            return versionMapper.selectOne(new LambdaQueryWrapper<ModerationDictionaryVersion>()
                    .eq(ModerationDictionaryVersion::getChecksum, checksum)
                    .last("LIMIT 1"));
        }

        @Override
        public void insertVersion(ModerationDictionaryVersion version) {
            if (versionMapper != null && version != null) {
                versionMapper.insert(version);
            }
        }

        @Override
        public void updateVersion(ModerationDictionaryVersion version) {
            if (versionMapper != null && version != null) {
                versionMapper.updateById(version);
            }
        }

        @Override
        public void insertRule(ModerationRule rule) {
            if (ruleMapper != null && rule != null) {
                ruleMapper.insert(rule);
            }
        }

        @Override
        public void updateRule(ModerationRule rule) {
            if (ruleMapper != null && rule != null) {
                ruleMapper.updateById(rule);
            }
        }

        @Override
        public void deleteRule(Long ruleId) {
            if (ruleMapper != null && ruleId != null) {
                ruleMapper.deleteById(ruleId);
            }
        }

        @Override
        public ModerationRule findRuleById(Long ruleId) {
            if (ruleMapper == null || ruleId == null) return null;
            return ruleMapper.selectById(ruleId);
        }

        @Override
        public int countRules(Long versionId) {
            if (ruleMapper == null || versionId == null) return 0;
            Long count = ruleMapper.selectCount(new LambdaQueryWrapper<ModerationRule>()
                    .eq(ModerationRule::getDictionaryVersionId, versionId));
            return count != null ? count.intValue() : 0;
        }

        @Override
        public void insertPolicy(ModerationPolicy policy) {
            if (policyMapper != null && policy != null) {
                policyMapper.insert(policy);
            }
        }

        @Override
        public List<ModerationRule> findRules(long dictionaryVersionId) {
            if (ruleMapper == null) return List.of();
            return ruleMapper.selectList(new LambdaQueryWrapper<ModerationRule>()
                    .eq(ModerationRule::getDictionaryVersionId, dictionaryVersionId)
                    .orderByAsc(ModerationRule::getId));
        }

        @Override
        public List<ModerationRule> findRulesFiltered(long dictionaryVersionId, String keyword, String ruleType, String category) {
            if (ruleMapper == null) return List.of();
            LambdaQueryWrapper<ModerationRule> wrapper = new LambdaQueryWrapper<ModerationRule>()
                    .eq(ModerationRule::getDictionaryVersionId, dictionaryVersionId);
            if (keyword != null && !keyword.isBlank()) {
                wrapper.like(ModerationRule::getContent, keyword.trim());
            }
            if (ruleType != null && !ruleType.isBlank()) {
                wrapper.eq(ModerationRule::getRuleType, ruleType.trim());
            }
            if (category != null && !category.isBlank()) {
                wrapper.eq(ModerationRule::getCategory, category.trim());
            }
            wrapper.orderByDesc(ModerationRule::getId);
            return ruleMapper.selectList(wrapper);
        }
    }
}
