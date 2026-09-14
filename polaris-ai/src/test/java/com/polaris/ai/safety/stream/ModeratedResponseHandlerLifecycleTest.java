package com.polaris.ai.safety.stream;

import com.polaris.ai.domain.AiMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeratedResponseHandlerLifecycleTest
{
    @Test
    void acceptsOnlyOneCompletionAndIgnoresLateFragments()
    {
        List<String> events = new ArrayList<>();
        List<AiMessage> persisted = new ArrayList<>();
        ModeratedResponseHandler handler = new ModeratedResponseHandler(
                null,
                null,
                (event, data) -> events.add(event + ":" + data),
                new AtomicBoolean(false),
                persisted::add
        );
        handler.setConversationId(7L);

        handler.onToken("有效片段");
        handler.onComplete(3);
        handler.onToken("晚到片段");
        handler.onThinking("晚到思考");
        handler.onComplete(4);

        assertEquals(1, persisted.size());
        assertEquals("有效片段", persisted.get(0).getContent());
        assertEquals(1, events.stream()
                .filter(event -> event.equals("done:[DONE]"))
                .count());
        assertTrue(events.stream().noneMatch(event -> event.contains("晚到片段")));
        assertTrue(events.stream().noneMatch(event -> event.contains("晚到思考")));
    }
}
