package com.polaris.ai.workflow.runtime;

import org.springframework.stereotype.Component;

import java.util.Locale;

/** 无副作用的多人审批决策算法。 */
@Component
public class WorkflowApprovalDecisionEngine {

    public Outcome evaluate(
            String mode,
            int requiredApprovals,
            boolean rejectOnAny,
            int approved,
            int rejected,
            int pending) {
        if (approved < 0 || rejected < 0 || pending < 0) {
            throw new IllegalArgumentException("审批人数统计不能为负数");
        }
        int total = approved + rejected + pending;
        if (total == 0) {
            return Outcome.CONFIG_ERROR;
        }
        if (rejectOnAny && rejected > 0) {
            return Outcome.REJECTED;
        }
        String normalizedMode = mode == null
                ? "ANY" : mode.toUpperCase(Locale.ROOT);
        return switch (normalizedMode) {
            case "ANY" -> {
                if (approved > 0) {
                    yield Outcome.APPROVED;
                }
                yield pending == 0 ? Outcome.REJECTED : Outcome.PENDING;
            }
            case "ALL" -> {
                if (rejected > 0) {
                    yield Outcome.REJECTED;
                }
                yield pending == 0 && approved == total
                        ? Outcome.APPROVED : Outcome.PENDING;
            }
            case "N_OF_M" -> evaluateThreshold(
                    requiredApprovals, approved, pending, total);
            default -> throw new IllegalArgumentException(
                    "不支持的多人审批模式：" + mode);
        };
    }

    private static Outcome evaluateThreshold(
            int requiredApprovals,
            int approved,
            int pending,
            int total) {
        if (requiredApprovals < 1 || requiredApprovals > total) {
            return Outcome.CONFIG_ERROR;
        }
        if (approved >= requiredApprovals) {
            return Outcome.APPROVED;
        }
        if (approved + pending < requiredApprovals) {
            return Outcome.REJECTED;
        }
        return Outcome.PENDING;
    }

    public enum Outcome {
        PENDING,
        APPROVED,
        REJECTED,
        CONFIG_ERROR
    }
}
