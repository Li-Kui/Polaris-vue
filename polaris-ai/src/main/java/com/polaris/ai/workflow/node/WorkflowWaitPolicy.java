package com.polaris.ai.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** “等待”节点统一的时间解析和安全边界。 */
public final class WorkflowWaitPolicy {

    public static final long DEFAULT_MAX_WAIT_SECONDS = 30L * 24 * 60 * 60;
    public static final long MAX_WAIT_SECONDS = 365L * 24 * 60 * 60;
    public static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    private static final Set<String> UNITS = Set.of("SECOND", "MINUTE", "HOUR", "DAY");

    private WorkflowWaitPolicy() {
    }

    public static boolean isDue(Resolution resolution, Instant now) {
        return resolution != null && now != null && !now.isBefore(resolution.targetAt());
    }

    public static Resolution resolve(JsonNode config, JsonNode input, Instant enteredAt) {
        if (config == null || !config.isObject()) {
            throw invalid("WAIT_CONFIG_INVALID", "等待节点配置不存在");
        }
        if (!"2.0".equals(config.path("configVersion").asText())) {
            throw invalid("WAIT_CONFIG_INVALID", "等待节点仅支持当前配置版本 2.0");
        }
        JsonNode schedule = config.path("schedule");
        JsonNode source = schedule.path("source");
        String kind = schedule.path("kind").asText();
        String sourceKind = source.path("kind").asText();
        if (!Set.of("AFTER", "AT").contains(kind)
                || !Set.of("FIXED", "INPUT").contains(sourceKind)) {
            throw invalid("WAIT_CONFIG_INVALID", "请选择有效的等待方式和时间来源");
        }

        long maximumSeconds = config.path("safety")
                .path("maxWaitSeconds").asLong(DEFAULT_MAX_WAIT_SECONDS);
        if (maximumSeconds < 1 || maximumSeconds > MAX_WAIT_SECONDS) {
            throw invalid("WAIT_LIMIT_EXCEEDED", "最大等待时间必须在 1 秒到 365 天之间");
        }

        Instant targetAt;
        String timezone = source.path("timezone").asText(DEFAULT_TIMEZONE);
        ZoneId zone = zoneId(timezone);
        if ("AFTER".equals(kind)) {
            long amount = durationAmount(source, input, sourceKind);
            String unit = source.path("unit").asText();
            if (!UNITS.contains(unit)) {
                throw invalid("WAIT_INPUT_INVALID", "请选择秒、分钟、小时或天作为等待单位");
            }
            long durationSeconds = multiply(amount, unitSeconds(unit));
            if (durationSeconds > maximumSeconds) {
                throw invalid("WAIT_LIMIT_EXCEEDED",
                        "本次等待超过节点允许的最大等待时间");
            }
            targetAt = plusSeconds(enteredAt, durationSeconds);
            return new Resolution(
                    kind, sourceKind, enteredAt, targetAt, durationSeconds * 1000,
                    false, durationSeconds == 0, timezone);
        }

        targetAt = "FIXED".equals(sourceKind)
                ? fixedTarget(source, zone) : inputTarget(input, zone);
        long waitMillis = durationMillis(enteredAt, targetAt);
        if (waitMillis <= 0) {
            if ("FAIL".equals(config.path("pastDuePolicy").asText("CONTINUE"))) {
                throw invalid("WAIT_TARGET_PAST", "目标时间已经过去，当前策略要求节点失败");
            }
            return new Resolution(
                    kind, sourceKind, enteredAt, targetAt, 0, true, true, timezone);
        }
        if (waitMillis > maximumSeconds * 1000) {
            throw invalid("WAIT_LIMIT_EXCEEDED",
                    "目标时间超过节点允许的最大等待时间");
        }
        return new Resolution(
                kind, sourceKind, enteredAt, targetAt, waitMillis,
                false, false, timezone);
    }

    public static ObjectNode output(
            Resolution resolution, Instant resumedAt,
            boolean simulated, boolean manualResume) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.put("status", "RESUMED");
        output.put("enteredAt", resolution.enteredAt().toString());
        output.put("targetAt", resolution.targetAt().toString());
        output.put("resumedAt", resumedAt.toString());
        output.put("waitedMs", Math.max(0,
                durationMillis(resolution.enteredAt(), resumedAt)));
        output.put("lateByMs", Math.max(0,
                durationMillis(resolution.targetAt(), resumedAt)));
        output.put("pastDue", resolution.pastDue());
        output.put("skipped", resolution.skipped());
        output.put("manualResume", manualResume);
        output.put("simulated", simulated);
        output.put("timezone", resolution.timezone());
        return output;
    }

    public static Resolution restore(JsonNode waitingOutput) {
        try {
            Instant enteredAt = Instant.parse(waitingOutput.path("enteredAt").asText());
            Instant targetAt = Instant.parse(waitingOutput.path("targetAt").asText());
            return new Resolution(
                    waitingOutput.path("mode").asText("AFTER"),
                    waitingOutput.path("sourceType").asText("FIXED"),
                    enteredAt, targetAt,
                    Math.max(0, durationMillis(enteredAt, targetAt)),
                    waitingOutput.path("pastDue").asBoolean(false),
                    waitingOutput.path("skipped").asBoolean(false),
                    waitingOutput.path("timezone").asText(DEFAULT_TIMEZONE));
        } catch (DateTimeParseException exception) {
            throw invalid("WAIT_STATE_INVALID", "等待节点的持久化时间状态无法恢复");
        }
    }

    public static ObjectNode waitingOutput(Resolution resolution) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.put("status", "WAITING");
        output.put("mode", resolution.mode());
        output.put("sourceType", resolution.sourceType());
        output.put("enteredAt", resolution.enteredAt().toString());
        output.put("targetAt", resolution.targetAt().toString());
        output.put("pastDue", resolution.pastDue());
        output.put("skipped", resolution.skipped());
        output.put("timezone", resolution.timezone());
        return output;
    }

    private static long durationAmount(
            JsonNode source, JsonNode input, String sourceKind) {
        JsonNode value = "FIXED".equals(sourceKind)
                ? source.get("value") : input == null ? null : input.get("duration");
        if (value == null || !value.isIntegralNumber()) {
            throw invalid("WAIT_INPUT_INVALID", "等待时长必须是整数");
        }
        if (!value.canConvertToLong()) {
            throw invalid("WAIT_LIMIT_EXCEEDED", "等待时间超过系统支持范围");
        }
        long amount = value.asLong();
        if (amount < 0 || ("FIXED".equals(sourceKind) && amount == 0)) {
            throw invalid("WAIT_INPUT_INVALID",
                    "固定等待时长必须大于 0，动态等待时长不能小于 0");
        }
        return amount;
    }

    private static Instant fixedTarget(JsonNode source, ZoneId timezone) {
        String value = source.path("localDateTime").asText();
        if (value.isBlank()) {
            throw invalid("WAIT_INPUT_INVALID", "请选择目标日期和时间");
        }
        try {
            return localTarget(LocalDateTime.parse(value), timezone);
        } catch (DateTimeException exception) {
            throw invalid("WAIT_INPUT_INVALID", "目标日期、时间或时区无效");
        }
    }

    private static Instant inputTarget(JsonNode input, ZoneId timezone) {
        JsonNode value = input == null ? null : input.get("targetAt");
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid("WAIT_INPUT_INVALID", "没有获取到目标时间，请检查上游字段");
        }
        String text = value.asText().trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException ignoredOffset) {
                try {
                    return ZonedDateTime.parse(text).toInstant();
                } catch (DateTimeParseException ignoredZone) {
                    try {
                        return localTarget(LocalDateTime.parse(text), timezone);
                    } catch (DateTimeException exception) {
                        throw invalid("WAIT_INPUT_INVALID",
                                "目标时间格式无法识别，请使用标准日期时间");
                    }
                }
            }
        }
    }

    private static ZoneId zoneId(String value) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw invalid("WAIT_INPUT_INVALID", "业务时区无效");
        }
    }

    private static Instant localTarget(LocalDateTime value, ZoneId zone) {
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(value);
        if (offsets.isEmpty()) {
            throw invalid("WAIT_INPUT_INVALID", "目标时间处于夏令时跳时区间，请选择其他时间");
        }
        if (offsets.size() > 1) {
            throw invalid("WAIT_INPUT_INVALID", "目标时间在夏令时切换中出现两次，请选择其他时间");
        }
        return value.atOffset(offsets.get(0)).toInstant();
    }

    private static long unitSeconds(String unit) {
        return switch (unit.toUpperCase(Locale.ROOT)) {
            case "SECOND" -> 1;
            case "MINUTE" -> 60;
            case "HOUR" -> 3600;
            case "DAY" -> 86400;
            default -> throw invalid("WAIT_INPUT_INVALID", "等待时间单位无效");
        };
    }

    private static long multiply(long value, long multiplier) {
        try {
            return Math.multiplyExact(value, multiplier);
        } catch (ArithmeticException exception) {
            throw invalid("WAIT_LIMIT_EXCEEDED", "等待时间超过系统支持范围");
        }
    }

    private static Instant plusSeconds(Instant value, long seconds) {
        try {
            return value.plusSeconds(seconds);
        } catch (DateTimeException | ArithmeticException exception) {
            throw invalid("WAIT_LIMIT_EXCEEDED", "等待时间超过系统支持范围");
        }
    }

    private static long durationMillis(Instant from, Instant to) {
        try {
            return Duration.between(from, to).toMillis();
        } catch (ArithmeticException exception) {
            return to.isAfter(from) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static WaitException invalid(String code, String message) {
        return new WaitException(code, message);
    }

    public record Resolution(
            String mode,
            String sourceType,
            Instant enteredAt,
            Instant targetAt,
            long waitMillis,
            boolean pastDue,
            boolean skipped,
            String timezone) {
    }

    public static final class WaitException extends IllegalArgumentException {
        private final String code;

        private WaitException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
