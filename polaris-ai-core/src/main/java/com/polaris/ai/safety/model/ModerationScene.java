package com.polaris.ai.safety.model;

/** Content boundary being moderated. */
public enum ModerationScene {
    KNOWLEDGE,
    CHAT_INPUT,
    AI_OUTPUT,
    WORKFLOW_INPUT,
    WORKFLOW_OUTPUT
}
