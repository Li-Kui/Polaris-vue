package com.polaris.ai.knowledge;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiMessage;
import com.polaris.ai.mapper.AiChatMapper;
import com.polaris.ai.modelcenter.runtime.ChatRuntimeService;
import com.polaris.ai.modelcenter.runtime.ModelDefinitionResolver;
import com.polaris.ai.modelcenter.service.ModelAggregateService;
import com.polaris.ai.runtime.ModelHttpDiagnostics;
import com.polaris.ai.runtime.stream.*;
import com.polaris.ai.service.IAiKnowledgeService;
import com.polaris.common.exception.ServiceException;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/** 显式 QA 审阅诊断，不接普通聊天、不检索、不保存或改写答案；模型只提出待复核建议。 */
@Service
public class KnowledgeEvidenceReviewService {
    public static final String POLICY_VERSION="EVIDENCE_REVIEW_V1";
    public static final String MODEL_DEFAULT="MODEL_DEFAULT";
    public static final String NON_THINKING="DASHSCOPE_NON_THINKING_V1";
    private static final String POLICY=loadPolicy();
    private final AiChatMapper chats;
    private final ChatKnowledgeEvidenceService evidence;
    private final IAiKnowledgeService knowledge;
    private final ModelAggregateService models;
    private final ModelDefinitionResolver definitions;
    private final ChatRuntimeService runtime;
    private final ObjectMapper json;
    private final Set<String> active=new HashSet<>();
    private final Map<String,Long> recent=new HashMap<>();

    public KnowledgeEvidenceReviewService(AiChatMapper chats, ChatKnowledgeEvidenceService evidence,
            IAiKnowledgeService knowledge, ModelAggregateService models, ModelDefinitionResolver definitions,
            ChatRuntimeService runtime, ObjectMapper json) {
        this.chats=chats;this.evidence=evidence;this.knowledge=knowledge;this.models=models;
        this.definitions=definitions;this.runtime=runtime;this.json=json;
    }

    public record Prefix(String citationId,String documentId,String chunkNo,String textHash) { }
    public record Request(Long modelId,String modelViewHash,String answerHash,String queryHash,
                          List<Prefix> sourcePrefixes,Boolean modelConfirmed,Integer waitTimeoutMs,String reviewMode) {
        public Request(Long modelId,String modelViewHash,String answerHash,String queryHash,
                       List<Prefix> sourcePrefixes,Boolean modelConfirmed,Integer waitTimeoutMs) {
            this(modelId,modelViewHash,answerHash,queryHash,sourcePrefixes,modelConfirmed,waitTimeoutMs,null);
        }
        public Request(Long modelId,String modelViewHash,String answerHash,String queryHash,
                       List<Prefix> sourcePrefixes,Boolean modelConfirmed) {
            this(modelId,modelViewHash,answerHash,queryHash,sourcePrefixes,modelConfirmed,null,null);
        }
        @Override public String toString() {return "EvidenceReviewRequest[modelId="+modelId+",payload=<redacted>]";}
    }
    public record Usage(Integer inputTokens,Integer outputTokens,Integer totalTokens) { }
    /** 仅记录应用解析到的事件元数据，不是首个网络字节、提供器完成或语义支持证明。 */
    public record Diagnostics(int version,String traceId,int waitTimeoutMs,long elapsedMs,Long firstEventMs,
                              Long firstTextMs,Long firstReasoningMs,int textEvents,int textCharacters,
                              int reasoningEvents,int reasoningCharacters,boolean completedEventReceived,
                              boolean budgetExpired,String termination,ModelHttpDiagnostics.Snapshot upstream,String reviewMode) { }
    public record Response(String state,String policyVersion,String reviewerType,String answerHash,String queryHash,
                           String runtimeDefinitionHash,String suggestion,Usage usage,boolean qualityReleaseApproved,
                           Diagnostics diagnostics) {
        @Override public String toString() {return "EvidenceReviewResponse[state="+state+",payload=<redacted>]";}
    }
    public record FailureResponse(String state,String policyVersion,String reviewerType,Diagnostics diagnostics,
                                  boolean qualityReleaseApproved) { }
    public static final class ReviewFailure extends RuntimeException {
        private static final long serialVersionUID=1L;
        private final int code;
        private final Diagnostics diagnostics;
        ReviewFailure(String message,int code,Diagnostics diagnostics) {super(message);this.code=code;this.diagnostics=diagnostics;}
        public int getCode() {return code;}
        public FailureResponse response() {return new FailureResponse("NOT_REVIEWED",POLICY_VERSION,"MODEL_SUGGESTION",diagnostics,false);}
    }
    private record Material(String query,String answer,List<Source> sources) { }
    private record Source(String id,String documentId,String indexVersion,String chunkNo,List<String> locations,
                          boolean truncated,String text) { }

    public Response review(Long conversationId,Long messageId,Request request) {
        requireAdmin();
        if(conversationId==null || conversationId<=0 || messageId==null || messageId<=0
                || request==null || request.modelId()==null || request.modelId()<=0 || !Boolean.TRUE.equals(request.modelConfirmed())
                || !digest(request.modelViewHash()) || !digest(request.answerHash()) || !digest(request.queryHash())
                || request.sourcePrefixes()==null || request.sourcePrefixes().isEmpty() || request.sourcePrefixes().size()>50
                || (request.waitTimeoutMs()!=null && request.waitTimeoutMs()!=30000 && request.waitTimeoutMs()!=60000)
                || (request.reviewMode()!=null && !Set.of(MODEL_DEFAULT,NON_THINKING).contains(request.reviewMode()))) {
            throw new ServiceException("请明确选择审阅模型、确认调用及原回答／来源摘要",400);
        }
        var caller=CallerContextHolder.require();String key=caller.getTenantId()+":"+caller.getUserId();begin(key);
        int budget=request.waitTimeoutMs()==null?30000:request.waitTimeoutMs();Capture capture=null;
        try {
            Material before=material(conversationId,messageId);requireBinding(before,request);
            String modelHash=modelHash(request.modelId());
            if(!modelHash.equals(request.modelViewHash()))throw changed();
            var definition=definitions.resolve(request.modelId(),"CHAT_COMPLETION");
            if(!definition.enabledFeatures().containsKey("STREAMING")) {
                throw new ServiceException("此有界诊断需显式启用流式能力；不回退不可取消的调用",409);
            }
            String data=json.writeValueAsString(before);
            if(data.length()+POLICY.length()>30000)throw new ServiceException("审阅输入超限，未截断原依据或调用模型",400);
            var chat=ChatRequest.builder().messages(List.of(SystemMessage.from(POLICY),UserMessage.from(data))).build();
            capture=new Capture(budget,request.reviewMode()==null?MODEL_DEFAULT:request.reviewMode());
            try {
                // 原来源、会话和模型在真正派发前再次检查；派发后撤权无法撤回已经外发的数据。
                if(!before.equals(material(conversationId,messageId)) || !modelHash.equals(modelHash(request.modelId())))throw changed();
                capture.started();
                try (var scope=ModelHttpDiagnostics.bind(capture.http)) {
                    var publisher=NON_THINKING.equals(capture.reviewMode)
                            ?runtime.streamQaNonThinkingReview(request.modelId(),chat,definition.runtimeDefinitionHash())
                            :runtime.stream(request.modelId(),chat,Set.of(),List.of());
                    publisher.subscribe(capture);
                }
                if(!capture.await(waitMillis(budget))) {
                    capture.expire();
                    throw new ServiceException("审阅超时，远端完成状态未知；未自动重试",504);
                }
                String suggestion=capture.result();
                var node=json.readTree(suggestion);
                if(node==null || !node.isObject() || node.size()!=1 || !node.has("claims") || !node.get("claims").isArray()
                        || node.get("claims").isEmpty() || node.get("claims").size()>50) {
                    throw new ServiceException("审阅输出格式无效，未自动修复或判为支持",502);
                }
                requireAdmin();
                if(!before.equals(material(conversationId,messageId)) || !modelHash.equals(modelHash(request.modelId()))
                        || !Objects.equals(definition.runtimeDefinitionHash(),definitions.resolve(request.modelId(),"CHAT_COMPLETION").runtimeDefinitionHash()))throw changed();
                // COMPLETED 仅说明有界调用返回了候选 JSON；精确范围／引文由离线契约校验，语义仍待审阅。
                return new Response("COMPLETED",POLICY_VERSION,"MODEL_SUGGESTION",request.answerHash(),request.queryHash(),
                        definition.runtimeDefinitionHash(),suggestion,capture.usage,false,capture.diagnostics());
            } finally {capture.cancel();}
        } catch (ServiceException e) {
            Integer code=e.getCode();
            if(code==null || !Set.of(400,403,404,409,429,502,504).contains(code))code=502;
            String message=code==504?"审阅超时，远端完成状态未知；未自动重试"
                    :"QA 审阅未完成：权限、原依据、模型或输出不可用；未判为支持，未自动重试";
            if(capture!=null && capture.started && (code==502 || code==504))throw new ReviewFailure(message,code,capture.diagnostics());
            throw new ServiceException(message,code);
        }
        catch (Exception e) {
            String message="审阅服务或输出暂不可用，未判为支持；未自动重试";
            if(capture!=null && capture.started)throw new ReviewFailure(message,502,capture.diagnostics());
            throw new ServiceException(message,502);
        }
        finally {end(key);}
    }

    long timeoutMillis() {return 30000;}
    long waitMillis(int budget) {return budget==30000?timeoutMillis():budget;}

    private Material material(Long conversationId,Long messageId) {
        var caller=CallerContextHolder.require();
        var conversation=chats.selectConversationById(conversationId,caller.getUserId());
        if(conversation==null || conversation.getKnowledgeBaseId()==null)throw new ServiceException("原 QA 会话不存在或无权访问",404);
        var kb=knowledge.selectAccessibleKnowledgeBaseById(conversation.getKnowledgeBaseId());
        if(kb==null || kb.getName()==null || !kb.getName().startsWith("QA")
                || (!caller.isSuperAdmin() && !Objects.equals(caller.getTenantId(),Objects.toString(kb.getTenantId(),null)))) {
            throw new ServiceException("此诊断仅用于当前可访问的 QA 知识库会话",404);
        }
        AiMessage answer=chats.selectMessageByIdAndConversation(messageId,conversationId);
        if(answer==null || !Objects.equals(answer.getConversationId(),conversationId) || !"assistant".equals(answer.getRole())
                || !"SAFE".equals(answer.getModerationStatus()) || answer.getContent()==null || answer.getContent().isBlank()
                || answer.getContent().length()>200000 || answer.getKnowledgeCitations()==null || answer.getKnowledgeCitations().length()>50000)throw changed();
        var messages=chats.selectMessagesByConversationId(conversationId);
        int position=-1;
        for(int i=0;i<messages.size();i++)if(Objects.equals(messages.get(i).getId(),messageId)){if(position>=0)throw changed();position=i;}
        if(position<1)throw changed();
        var question=messages.get(position-1);
        if(!"user".equals(question.getRole()) || !Objects.equals(question.getConversationId(),conversationId)
                || question.getContent()==null || question.getContent().isBlank() || question.getContent().length()>2000
                || nonempty(question.getFileUrl()) || nonempty(question.getFileContent()) || nonempty(question.getAttachmentTokens()))throw changed();
        var archive=json.readValue(answer.getKnowledgeCitations(),ChatKnowledgeEvidenceService.Archive.class);
        if(archive==null || archive.version()!=1 || archive.citations()==null || archive.citations().isEmpty() || archive.citations().size()>50)throw changed();
        Set<String> ids=new HashSet<>();List<Source> sources=new ArrayList<>();int chars=0;
        for(var pointer:archive.citations()) {
            if(pointer==null || !Objects.equals(pointer.knowledgeBaseId(),kb.getId()) || !ids.add(pointer.citationId()))throw changed();
            var p=evidence.preview(conversationId,messageId,pointer.citationId());
            if(p.text()==null)throw changed();chars+=p.text().length();
            sources.add(new Source(p.citationId(),Objects.toString(p.documentId()),Objects.toString(p.indexVersion()),p.chunkNo(),p.locations(),p.truncated(),p.text()));
        }
        if(chars>12000)throw changed();
        return new Material(question.getContent(),answer.getContent(),List.copyOf(sources));
    }

    private void requireBinding(Material material,Request request) {
        if(!hashValue(material.answer()).equals(request.answerHash()) || !hashValue(material.query()).equals(request.queryHash())
                || request.sourcePrefixes().size()!=material.sources().size())throw changed();
        Set<String> seen=new HashSet<>();
        for(var expected:request.sourcePrefixes()) {
            if(expected==null || !seen.add(expected.citationId()))throw changed();
            var actual=material.sources().stream().filter(p->p.id().equals(expected.citationId())).findFirst().orElseThrow(KnowledgeEvidenceReviewService::changed);
            if(!actual.documentId().equals(expected.documentId()) || !Objects.equals(actual.chunkNo(),expected.chunkNo())
                    || !hashValue(actual.text()).equals(expected.textHash()))throw changed();
        }
    }

    private String modelHash(Long modelId) {
        // Normalize JSON numeric scalars to the representation used by the existing Node QA public-view digest.
        var tree=json.valueToTree(models.get(modelId));
        return KnowledgeDocumentSnapshotStore.digest(jsJson(tree));
    }
    private String jsJson(tools.jackson.databind.JsonNode node) {
        if(node.isNumber())return node.decimalValue().stripTrailingZeros().toPlainString();
        if(node.isArray()){List<String> values=new ArrayList<>();node.forEach(value->values.add(jsJson(value)));return "["+String.join(",",values)+"]";}
        if(node.isObject()){List<String> values=new ArrayList<>();node.properties().forEach(entry->values.add(json.writeValueAsString(entry.getKey())+":"+jsJson(entry.getValue())));return "{"+String.join(",",values)+"}";}
        return node.toString();
    }
    private String hashValue(String value) {return KnowledgeDocumentSnapshotStore.digest(json.writeValueAsString(value));}
    private static boolean nonempty(String value) {return value!=null&&!value.isBlank();}
    private static boolean digest(String value) {return value!=null&&value.matches("[a-f0-9]{64}");}
    private static ServiceException changed() {return new ServiceException("原 QA 问答、来源或模型已变化／不可用，停止审阅，不以新版替代",409);}
    private static void requireAdmin() {
        var caller=CallerContextHolder.get();
        if(caller==null || caller.getUserId()==null || caller.getUserId()<=0
                || !(caller.isSuperAdmin() || (caller.isPlatformMode()&&caller.isTenantAdmin())))throw new ServiceException("仅管理员可显式进行 QA 审阅诊断",403);
    }
    private synchronized void begin(String key) {
        long now=System.nanoTime();recent.entrySet().removeIf(e->now-e.getValue()>=5_000_000_000L);
        if(active.contains(key)||active.size()>=4||recent.containsKey(key)||recent.size()>=4096)throw new ServiceException("QA 审阅正在进行或请求过于频繁",429);
        active.add(key);recent.put(key,now);
    }
    private synchronized void end(String key) {active.remove(key);}
    private static String loadPolicy() {
        try(var input=new ClassPathResource("knowledge/evidence-review-v1.txt").getInputStream()) {return new String(input.readAllBytes(),StandardCharsets.UTF_8).trim();}
        catch(Exception e) {throw new IllegalStateException("缺少固定引用审阅契约资源");}
    }

    private static final class Capture implements Flow.Subscriber<ModelStreamEvent> {
        final CountDownLatch done=new CountDownLatch(1);
        final StringBuilder text=new StringBuilder();
        final int budget;final String reviewMode;final String traceId=UUID.randomUUID().toString();
        final ModelHttpDiagnostics http=new ModelHttpDiagnostics();
        boolean started,budgetExpired;long startedNanos,endedNanos;Long firstEventMs,firstTextMs,firstReasoningMs;
        int textEvents,textCharacters,reasoningEvents,reasoningCharacters;boolean completedEventReceived;
        String termination="AWAITING_EVENTS";
        Flow.Subscription subscription;boolean terminal,completed,failed;int bytes,reasoningBytes;Usage usage;
        Capture(int budget,String reviewMode) {this.budget=budget;this.reviewMode=reviewMode;}
        synchronized void started(){started=true;startedNanos=http.start();}
        synchronized void expire(){budgetExpired=true;terminal=true;termination="WAIT_BUDGET_EXPIRED";endedNanos=System.nanoTime();http.freeze(true);}
        long elapsed(){return started?Math.max(0,((endedNanos==0?System.nanoTime():endedNanos)-startedNanos)/1_000_000L):0;}
        synchronized Diagnostics diagnostics(){return new Diagnostics(1,traceId,budget,elapsed(),firstEventMs,firstTextMs,firstReasoningMs,
                textEvents,textCharacters,reasoningEvents,reasoningCharacters,completedEventReceived,budgetExpired,termination,http.snapshot(),reviewMode);}
        @Override public synchronized void onSubscribe(Flow.Subscription next) {
            if(subscription!=null || terminal){next.cancel();return;}subscription=next;next.request(Long.MAX_VALUE);
        }
        @Override public synchronized void onNext(ModelStreamEvent event) {
            if(terminal)return;
            if(firstEventMs==null)firstEventMs=elapsed();
            if(event instanceof TextDelta delta && delta.text()!=null){if(firstTextMs==null)firstTextMs=elapsed();textEvents++;textCharacters+=delta.text().length();bytes+=delta.text().getBytes(StandardCharsets.UTF_8).length;if(bytes>32768){fail("OUTPUT_LIMIT");return;}text.append(delta.text());}
            else if(event instanceof ReasoningDelta delta && delta.text()!=null){if(firstReasoningMs==null)firstReasoningMs=elapsed();reasoningEvents++;reasoningCharacters+=delta.text().length();reasoningBytes+=delta.text().getBytes(StandardCharsets.UTF_8).length;if(reasoningBytes>65536)fail("REASONING_LIMIT");}
            else if(event instanceof UsageEvent reported){var u=reported.usage();usage=new Usage(u.inputTokens(),u.outputTokens(),u.totalTokens());}
            else if(event instanceof CompletedEvent end){completedEventReceived=true;completed="stop".equalsIgnoreCase(end.finishReason());if(!completed){failed=true;termination="NON_STOP_COMPLETION";}}
            else if(event instanceof ToolCallDelta || event instanceof AudioChunk || event instanceof AudioMetadata)fail("UNEXPECTED_EVENT");
            else if(event instanceof ErrorEvent)fail("UPSTREAM_ERROR");
        }
        @Override public synchronized void onError(Throwable error) {if(!terminal)fail("UPSTREAM_ERROR");}
        @Override public synchronized void onComplete() {if(!terminal){terminal=true;endedNanos=System.nanoTime();if(!completed){failed=true;if(!completedEventReceived)termination="COMPLETION_MISSING";}else termination="STREAM_COMPLETED";http.freeze(false);done.countDown();}}
        private void fail(String reason){failed=true;terminal=true;termination=reason;endedNanos=System.nanoTime();http.freeze(true);if(subscription!=null)subscription.cancel();done.countDown();}
        boolean await(long timeout) throws InterruptedException {try{return done.await(timeout,TimeUnit.MILLISECONDS);}catch(InterruptedException e){synchronized(this){terminal=true;termination="INTERRUPTED";endedNanos=System.nanoTime();http.freeze(true);}Thread.currentThread().interrupt();throw e;}}
        synchronized String result(){if(failed || !completed || text.isEmpty() || text.toString().getBytes(StandardCharsets.UTF_8).length>32768)throw new ServiceException("审阅未完整完成或输出超限，未判为支持",502);return text.toString();}
        synchronized void cancel(){terminal=true;if(endedNanos==0)endedNanos=System.nanoTime();http.freeze(true);if(subscription!=null)subscription.cancel();}
    }
}
