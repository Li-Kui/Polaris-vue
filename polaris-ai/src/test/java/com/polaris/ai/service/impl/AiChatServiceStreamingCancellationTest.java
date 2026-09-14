package com.polaris.ai.service.impl;

import com.polaris.ai.chat.CancellableStreamingChatCall;
import com.polaris.ai.domain.AiConversation;
import com.polaris.ai.mapper.AiChatMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChatServiceStreamingCancellationTest
{
    @Test
    void cancelChatStopsReactiveSubscriptionAndReleasesActiveState() throws Exception
    {
        AiConversation conversation = new AiConversation();
        conversation.setId(42L);
        AiChatMapper mapper = (AiChatMapper) Proxy.newProxyInstance(
                AiChatMapper.class.getClassLoader(),
                new Class<?>[]{AiChatMapper.class},
                (proxy, method, arguments) -> "selectConversationById".equals(method.getName())
                        ? conversation : null);
        AiChatServiceImpl service = new AiChatServiceImpl();
        service.setAiChatMapper(mapper);

        AtomicBoolean subscriptionCancelled = new AtomicBoolean(false);
        AtomicInteger cancellationErrors = new AtomicInteger();
        CancellableStreamingChatCall call = CancellableStreamingChatCall.start(
                publisherModel(subscriptionCancelled),
                List.of(),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                    }

                    @Override
                    public void onError(Throwable error) {
                        cancellationErrors.incrementAndGet();
                    }
                });
        AtomicBoolean cancellation = new AtomicBoolean(false);
        ConcurrentHashMap<Long, AtomicBoolean> conversations =
                map("ACTIVE_CONVERSATIONS");
        ConcurrentHashMap<Long, CancellableStreamingChatCall> calls =
                map("ACTIVE_STREAM_CALLS");
        conversations.put(42L, cancellation);
        calls.put(42L, call);

        try {
            assertTrue(service.cancelChat(42L, 7L));
            assertTrue(cancellation.get());
            assertTrue(subscriptionCancelled.get());
            assertTrue(call.isCancelled());
            assertTrue(call.isTerminated());
            assertTrue(cancellationErrors.get() == 1);
            assertFalse(conversations.containsKey(42L));
            assertFalse(calls.containsKey(42L));
        } finally {
            conversations.remove(42L);
            calls.remove(42L);
        }
    }

    @Test
    void disconnectedOldStreamCannotCancelANewerRun() throws Exception
    {
        AiChatServiceImpl service = new AiChatServiceImpl();
        AtomicBoolean subscriptionCancelled = new AtomicBoolean(false);
        CancellableStreamingChatCall call = CancellableStreamingChatCall.start(
                publisherModel(subscriptionCancelled),
                List.of(),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                    }

                    @Override
                    public void onError(Throwable error) {
                    }
                });
        AtomicBoolean currentCancellation = new AtomicBoolean(false);
        AtomicBoolean oldCancellation = new AtomicBoolean(false);
        ConcurrentHashMap<Long, AtomicBoolean> conversations =
                map("ACTIVE_CONVERSATIONS");
        ConcurrentHashMap<Long, CancellableStreamingChatCall> calls =
                map("ACTIVE_STREAM_CALLS");
        conversations.put(42L, currentCancellation);
        calls.put(42L, call);

        try {
            service.cancelStream(42L, oldCancellation);

            assertTrue(oldCancellation.get());
            assertFalse(subscriptionCancelled.get());
            assertTrue(conversations.containsKey(42L));
            assertTrue(calls.containsKey(42L));

            service.cancelStream(42L, currentCancellation);

            assertTrue(subscriptionCancelled.get());
            assertFalse(conversations.containsKey(42L));
            assertFalse(calls.containsKey(42L));
        } finally {
            conversations.remove(42L);
            calls.remove(42L);
        }
    }

    private StreamingChatModel publisherModel(AtomicBoolean subscriptionCancelled)
    {
        return new StreamingChatModel() {
            @Override
            public Flow.Publisher<ChatModelStreamingEvent> chat(List<ChatMessage> messages)
            {
                return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long count) {
                    }

                    @Override
                    public void cancel() {
                        subscriptionCancelled.set(true);
                    }
                });
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <V> ConcurrentHashMap<Long, V> map(String fieldName) throws Exception
    {
        Field field = AiChatServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (ConcurrentHashMap<Long, V>) field.get(null);
    }
}
