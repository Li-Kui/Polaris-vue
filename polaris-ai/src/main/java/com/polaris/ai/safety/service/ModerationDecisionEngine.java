package com.polaris.ai.safety.service;

import com.polaris.ai.safety.model.*;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Maps local risk and scene semantics to an executable enforcement action. */
@Component
public class ModerationDecisionEngine {

    public FinalAction decide(ModerationScene scene, LocalDecision decision,
                              ModerationPolicy policy) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(policy, "policy");

        FinalAction enforced = switch (decision) {
            case PASS -> FinalAction.ALLOW;
            case SUSPECT -> suspectAction(scene);
            case HIGH_RISK -> conservativeAction(scene);
        };
        PolicyMode mode = PolicyMode.valueOf(policy.getMode());
        if (mode == PolicyMode.OBSERVE
                && !(scene == ModerationScene.KNOWLEDGE
                && decision == LocalDecision.HIGH_RISK)) {
            return FinalAction.ALLOW;
        }
        return enforced;
    }

    public FinalAction conservativeAction(ModerationScene scene) {
        return switch (scene) {
            case KNOWLEDGE -> FinalAction.QUARANTINE;
            case CHAT_INPUT, WORKFLOW_INPUT -> FinalAction.BLOCK;
            case AI_OUTPUT, WORKFLOW_OUTPUT -> FinalAction.REPLACE;
        };
    }

    private FinalAction suspectAction(ModerationScene scene) {
        return switch (scene) {
            case KNOWLEDGE, CHAT_INPUT, WORKFLOW_INPUT -> FinalAction.ALLOW;
            case AI_OUTPUT, WORKFLOW_OUTPUT -> FinalAction.REPLACE;
        };
    }
}
