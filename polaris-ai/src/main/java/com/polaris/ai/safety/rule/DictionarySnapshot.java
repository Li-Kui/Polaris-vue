package com.polaris.ai.safety.rule;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.github.houbb.sensitive.word.support.ignore.SensitiveWordCharIgnores;
import com.github.houbb.sensitive.word.support.resultcondition.WordResultConditions;
import com.polaris.ai.safety.model.ModerationRule;
import com.polaris.ai.safety.model.RuleType;

import java.util.*;

/** Immutable, validated rule view used for one local moderation evaluation. */
public final class DictionarySnapshot {
    public static final int MAX_STREAM_RISK_TERM_LENGTH = 256;
    private final Map<String, List<SnapshotRule>> candidatesByTerm;
    private final Map<String, List<SnapshotRule>> aliasesByTerm;
    private final Map<String, List<SnapshotRule>> allTerms;
    private final List<SnapshotRule> allowRules;
    private final List<SnapshotRule> safeRules;
    private final TextNormalizer normalizer;
    private final int maxRiskTermLength;
    private final SensitiveWordBs matcher;

    private DictionarySnapshot(Map<String, List<SnapshotRule>> candidatesByTerm,
                               Map<String, List<SnapshotRule>> aliasesByTerm,
                               Map<String, List<SnapshotRule>> allTerms,
                               List<SnapshotRule> allowRules,
                               List<SnapshotRule> safeRules,
                               TextNormalizer normalizer,
                               int maxRiskTermLength,
                               SensitiveWordBs matcher) {
        this.candidatesByTerm = immutableLists(candidatesByTerm);
        this.aliasesByTerm = immutableLists(aliasesByTerm);
        this.allTerms = immutableLists(allTerms);
        this.allowRules = List.copyOf(allowRules);
        this.safeRules = List.copyOf(safeRules);
        this.normalizer = normalizer;
        this.maxRiskTermLength = maxRiskTermLength;
        this.matcher = matcher;
    }

    public static DictionarySnapshot from(List<ModerationRule> rules) {
        Objects.requireNonNull(rules, "rules");
        TextNormalizer normalizer = new TextNormalizer();
        PinyinVariantGenerator pinyin = new PinyinVariantGenerator();
        Map<String, List<SnapshotRule>> candidates = new LinkedHashMap<>();
        Map<String, List<SnapshotRule>> aliases = new LinkedHashMap<>();
        List<SnapshotRule> allows = new ArrayList<>();
        List<SnapshotRule> safe = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        int maxRiskTermLength = 0;

        for (ModerationRule sourceRule : rules) {
            validate(sourceRule);
            RuleType type = RuleType.valueOf(sourceRule.getRuleType());
            String term = normalizedTerm(sourceRule, normalizer);
            if (term.isBlank()) {
                throw new IllegalArgumentException("A snapshot rule must not normalize to blank content");
            }
            String identity = type.name() + '\0' + term + '\0' + sourceRule.getCategory();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("A dictionary snapshot contains duplicate canonical rules");
            }
            SnapshotRule rule = SnapshotRule.copyOf(sourceRule, type, term);
            if (type == RuleType.RISK_WORD || type == RuleType.RISK_CONTEXT) {
                if (term.length() > MAX_STREAM_RISK_TERM_LENGTH) {
                    throw new IllegalArgumentException(
                            "A normalized risk term exceeds the streaming overlap cap");
                }
                maxRiskTermLength = Math.max(maxRiskTermLength, term.length());
                candidates.computeIfAbsent(term, ignored -> new ArrayList<>()).add(rule);
                for (String alias : pinyin.variants(term)) {
                    if (alias.length() > MAX_STREAM_RISK_TERM_LENGTH) {
                        throw new IllegalArgumentException(
                                "A normalized risk alias exceeds the streaming overlap cap");
                    }
                    maxRiskTermLength = Math.max(maxRiskTermLength, alias.length());
                    aliases.computeIfAbsent(alias, ignored -> new ArrayList<>()).add(rule);
                }
            } else if (type == RuleType.ALLOW_TERM) {
                allows.add(rule);
            } else if (type == RuleType.SAFE_CONTEXT) {
                safe.add(rule);
            }
        }

        Map<String, List<SnapshotRule>> allTerms = new LinkedHashMap<>();
        candidates.forEach((term, rList) -> allTerms.computeIfAbsent(term, ignored -> new ArrayList<>()).addAll(rList));
        aliases.forEach((term, rList) -> allTerms.computeIfAbsent(term, ignored -> new ArrayList<>()).addAll(rList));

        SensitiveWordBs matcher = null;
        if (!allTerms.isEmpty()) {
            matcher = SensitiveWordBs.newInstance()
                    .wordDeny(() -> List.copyOf(allTerms.keySet()))
                    .wordAllow(WordAllows.empty())
                    .wordResultCondition(WordResultConditions.defaults())
                    .charIgnore(SensitiveWordCharIgnores.none())
                    .ignoreRepeat(true)
                    .wordFailFast(false)
                    .init();
        }

        return new DictionarySnapshot(candidates, aliases, allTerms, allows, safe, normalizer,
                maxRiskTermLength, matcher);
    }

    public SensitiveWordBs matcher() {
        return matcher;
    }

    public Map<String, List<SnapshotRule>> allTerms() {
        return allTerms;
    }

    Map<String, List<SnapshotRule>> candidatesByTerm() {
        return candidatesByTerm;
    }

    Map<String, List<SnapshotRule>> aliasesByTerm() {
        return aliasesByTerm;
    }

    List<SnapshotRule> allowRules() {
        return allowRules;
    }

    List<SnapshotRule> safeRules() {
        return safeRules;
    }

    TextNormalizer normalizer() {
        return normalizer;
    }

    String normalizedTerm(SnapshotRule rule) {
        return rule.normalizedContent();
    }

    public int maxRiskTermLength() {
        return maxRiskTermLength;
    }

    public NormalizedText normalizeForMatching(String raw) {
        return normalizer.normalizeForMatching(raw);
    }

    private static String normalizedTerm(ModerationRule rule, TextNormalizer normalizer) {
        String source = rule.getNormalizedContent();
        if (source == null || source.isBlank()) {
            source = rule.getContent();
        }
        return normalizer.normalize(source).text();
    }

    private static void validate(ModerationRule rule) {
        if (rule == null || rule.getId() == null || rule.getId() <= 0
                || rule.getDictionaryVersionId() == null || rule.getDictionaryVersionId() < 0
                || rule.getRuleType() == null || rule.getCategory() == null
                || !rule.getCategory().matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("A snapshot rule requires id, type, and category");
        }
        RuleType type = RuleType.valueOf(rule.getRuleType());
        String content = rule.getNormalizedContent() == null
                ? rule.getContent() : rule.getNormalizedContent();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("A snapshot rule requires content");
        }
        if (rule.getNormalizedHash() == null || rule.getNormalizedHash().isBlank()
                || rule.getWeight() == null) {
            throw new IllegalArgumentException("A snapshot rule requires normalized hash and weight");
        }
        boolean validWeight = switch (type) {
            case RISK_WORD, RISK_CONTEXT -> rule.getWeight() > 0;
            case SAFE_CONTEXT -> rule.getWeight() < 0;
            case ALLOW_TERM -> rule.getWeight() == 0;
        };
        if (!validWeight) {
            throw new IllegalArgumentException("A snapshot rule has invalid weight for its type");
        }
    }

    private static Map<String, List<SnapshotRule>> immutableLists(
            Map<String, List<SnapshotRule>> source) {
        Map<String, List<SnapshotRule>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }

    /** Deep-copied immutable rule metadata used by matchers and scorers. */
    public record SnapshotRule(
            Long id,
            Long dictionaryVersionId,
            RuleType ruleType,
            String content,
            String normalizedContent,
            String normalizedHash,
            String category,
            Integer weight,
            String source) {

        private static SnapshotRule copyOf(ModerationRule rule, RuleType type,
                                           String normalizedContent) {
            return new SnapshotRule(rule.getId(), rule.getDictionaryVersionId(), type,
                    rule.getContent(), normalizedContent, rule.getNormalizedHash(),
                    rule.getCategory(), rule.getWeight(), rule.getSource());
        }

        public Long getId() {
            return id;
        }
    }
}
