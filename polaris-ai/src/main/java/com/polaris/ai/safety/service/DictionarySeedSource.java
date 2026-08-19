package com.polaris.ai.safety.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.safety.model.ModerationRule;
import com.polaris.ai.safety.model.RuleType;
import com.polaris.ai.safety.rule.DictionarySnapshot;
import com.polaris.ai.safety.rule.TextNormalizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Reads and cryptographically validates the bundled, provider-independent seed. */
@Component
public class DictionarySeedSource {
    public static final String RESOURCE_LOCATION = "moderation/polaris-sensitive-seed-v4.json";
    private static final Set<String> ROOT_KEYS = Set.of(
            "sourceVersion", "checksum", "generatedAt", "source", "rules");
    private static final Set<String> SOURCE_KEYS = Set.of(
            "repository", "sourceVersion", "license");
    private static final Set<String> RULE_KEYS = Set.of(
            "id", "ruleType", "content", "normalizedContent", "category", "weight");

    private final InputFactory inputFactory;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public DictionarySeedSource() {
        this(() -> new ClassPathResource(RESOURCE_LOCATION).getInputStream());
    }

    DictionarySeedSource(InputFactory inputFactory) {
        this.inputFactory = inputFactory;
    }

    static DictionarySeedSource fromJson(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new DictionarySeedSource(() -> new ByteArrayInputStream(bytes));
    }

    public SeedBundle load() {
        try (InputStream input = inputFactory.open()) {
            JsonNode root = objectMapper.readTree(input);
            if (root == null || !root.isObject()) {
                throw invalid("root must be a JSON object");
            }
            requireExactKeys(root, ROOT_KEYS, "top-level schema");
            String sourceVersion = requiredText(root, "sourceVersion");
            String declaredChecksum = requiredText(root, "checksum");
            if (!declaredChecksum.matches("[0-9a-f]{64}")) {
                throw invalid("checksum must be a lowercase SHA-256 value");
            }
            requiredText(root, "generatedAt");
            JsonNode source = root.get("source");
            if (source == null || !source.isObject()) {
                throw invalid("source must be an object");
            }
            requireExactKeys(source, SOURCE_KEYS, "source schema");
            requiredText(source, "repository");
            requiredText(source, "sourceVersion");
            requiredText(source, "license");
            JsonNode ruleNodes = root.get("rules");
            if (ruleNodes == null || !ruleNodes.isArray() || ruleNodes.isEmpty()) {
                throw invalid("rules must be a non-empty array");
            }
            List<SeedRule> parsedRules = parseRules((ArrayNode) ruleNodes);
            String actualChecksum = sha256(
                    objectMapper.writeValueAsBytes(canonicalRules((ArrayNode) ruleNodes)));
            if (!MessageDigest.isEqual(declaredChecksum.getBytes(StandardCharsets.US_ASCII),
                    actualChecksum.getBytes(StandardCharsets.US_ASCII))) {
                throw invalid("checksum does not match the canonical rules array");
            }
            return new SeedBundle(sourceVersion, declaredChecksum, parsedRules);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled moderation seed", exception);
        }
    }

    private List<SeedRule> parseRules(ArrayNode nodes) {
        TextNormalizer normalizer = new TextNormalizer();
        List<SeedRule> rules = new ArrayList<>();
        Set<RuleType> types = EnumSet.noneOf(RuleType.class);
        Set<String> identities = new HashSet<>();
        for (JsonNode node : nodes) {
            if (!node.isObject()) {
                throw invalid("every rule must be a JSON object");
            }
            requireExactKeys(node, RULE_KEYS, "rule schema");
            requiredText(node, "id");
            RuleType type;
            try {
                type = RuleType.valueOf(requiredText(node, "ruleType"));
            } catch (IllegalArgumentException exception) {
                throw invalid("ruleType must be one of the four supported values");
            }
            String content = requiredText(node, "content");
            String normalized = normalizer.normalize(content).text();
            if (normalized.isBlank()) {
                throw invalid("rule content must not normalize to blank");
            }
            JsonNode declaredNormalized = node.get("normalizedContent");
            if (!declaredNormalized.isTextual()
                    || !normalized.equals(declaredNormalized.textValue())) {
                throw invalid("normalizedContent must equal TextNormalizer output");
            }
            String category = requiredText(node, "category");
            if (!category.matches("[A-Z][A-Z0-9_]*")) {
                throw invalid("category must be a non-empty uppercase identifier");
            }
            JsonNode weightNode = node.get("weight");
            if (weightNode == null || !weightNode.isIntegralNumber() || !weightNode.canConvertToInt()) {
                throw invalid("weight must be an integer");
            }
            int weight = weightNode.intValue();
            validateWeight(type, weight);
            String identity = type.name() + '\0' + normalized + '\0' + category;
            if (!identities.add(identity)) {
                throw invalid("duplicate canonical rule identity");
            }
            types.add(type);
            rules.add(new SeedRule(type, content, normalized, sha256(normalized), category, weight));
        }
        if (!types.equals(EnumSet.allOf(RuleType.class))) {
            throw invalid("seed must contain all four supported rule types");
        }
        return List.copyOf(rules);
    }

    private static void validateWeight(RuleType type, int weight) {
        boolean valid = switch (type) {
            case RISK_WORD, RISK_CONTEXT -> weight > 0;
            case SAFE_CONTEXT -> weight < 0;
            case ALLOW_TERM -> weight == 0;
        };
        if (!valid) {
            throw invalid("weight is invalid for ruleType " + type.name());
        }
    }

    private ArrayNode canonicalRules(ArrayNode rules) {
        ArrayNode canonical = objectMapper.createArrayNode();
        for (JsonNode rule : rules) {
            ObjectNode ordered = objectMapper.createObjectNode();
            ordered.set("category", rule.get("category"));
            ordered.set("content", rule.get("content"));
            ordered.set("id", rule.get("id"));
            ordered.set("normalizedContent", rule.get("normalizedContent"));
            ordered.set("ruleType", rule.get("ruleType"));
            ordered.set("weight", rule.get("weight"));
            canonical.add(ordered);
        }
        return canonical;
    }

    private static void requireExactKeys(JsonNode object, Set<String> expected, String context) {
        Set<String> actual = new HashSet<>();
        object.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid(context + " must contain exactly " + expected);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + " must be a non-empty string");
        }
        return value.textValue();
    }

    private static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid moderation seed: " + reason);
    }

    public record SeedBundle(String sourceVersion, String checksum, List<SeedRule> rules) {
        public SeedBundle {
            rules = List.copyOf(rules);
        }

        public List<ModerationRule> persistedRules(long dictionaryVersionId) {
            List<ModerationRule> result = new ArrayList<>(rules.size());
            for (SeedRule seed : rules) {
                result.add(seed.toEntity(null, dictionaryVersionId, sourceVersion));
            }
            return result;
        }

        DictionarySnapshot emergencySnapshot() {
            List<ModerationRule> result = new ArrayList<>(rules.size());
            long runtimeId = 1L;
            for (SeedRule seed : rules) {
                result.add(seed.toEntity(runtimeId++, 0L, sourceVersion + ":emergency"));
            }
            return DictionarySnapshot.from(result);
        }
    }

    private record SeedRule(RuleType type, String content, String normalizedContent,
                            String normalizedHash, String category, int weight) {
        ModerationRule toEntity(Long id, long dictionaryVersionId, String source) {
            ModerationRule rule = new ModerationRule();
            rule.setId(id);
            rule.setDictionaryVersionId(dictionaryVersionId);
            rule.setRuleType(type.name());
            rule.setContent(content);
            rule.setNormalizedContent(normalizedContent);
            rule.setNormalizedHash(normalizedHash);
            rule.setCategory(category);
            rule.setWeight(weight);
            rule.setSource(source);
            return rule;
        }
    }

    @FunctionalInterface
    interface InputFactory {
        InputStream open() throws IOException;
    }
}
