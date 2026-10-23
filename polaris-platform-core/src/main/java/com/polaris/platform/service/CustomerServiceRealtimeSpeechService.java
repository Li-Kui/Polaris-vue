package com.polaris.platform.service;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.customer.application.CustomerServiceRealtimeSpeechGateway;
import com.polaris.ai.runtime.usage.NormalizedUsage;
import com.polaris.common.exception.ServiceException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** HTTP 签发短期一次性票据；票据绑定 Origin、用户、会话和请求，不出现在 URL。 */
@Service
public class CustomerServiceRealtimeSpeechService {
    private final CustomerServiceSpeechService speech;
    private final CustomerServiceRealtimeSpeechGateway gateway;
    private final TokenQuotaService quotas;
    private final Map<String,Grant> grants=new HashMap<>();
    private final Set<Live> streams=ConcurrentHashMap.newKeySet();
    private final Semaphore slots=new Semaphore(4);
    private final ScheduledExecutorService cleanup=Executors.newSingleThreadScheduledExecutor(task -> { var thread=new Thread(task,"customer-realtime-fence"); thread.setDaemon(true); return thread; });
    public CustomerServiceRealtimeSpeechService(CustomerServiceSpeechService speech, CustomerServiceRealtimeSpeechGateway gateway, TokenQuotaService quotas) {
        this.speech=speech; this.gateway=gateway; this.quotas=quotas;
        cleanup.scheduleWithFixedDelay(() -> { for(var live:streams) { try { live.check(); } catch(RuntimeException invalid) { try { live.abort(); } catch(RuntimeException ignored) { } } } },1,1,TimeUnit.SECONDS);
    }
    public Ticket ticket(String sessionId,String requestId,String origin) {
        if(requestId==null || !requestId.matches("[A-Za-z0-9_-]{1,64}"))throw invalid();
        String boundOrigin=origin(origin);
        var plan=speech.streamPlan(sessionId);
        if(!gateway.supports(plan.modelId()))return new Ticket(false,null,requestId);
        synchronized(grants) {
            long now=System.currentTimeMillis(); grants.values().removeIf(grant -> grant.expires<now);
            if(grants.size()>=128 || grants.values().stream().filter(grant -> grant.plan.actor().equals(plan.actor())).count()>=2)throw new ServiceException("RATE_LIMITED: 实时连接票据过多",429);
            byte[] random=new byte[32]; new SecureRandom().nextBytes(random); String ticket=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            grants.put(ticket,new Grant(plan,sessionId,requestId,boundOrigin,now+30000,CallerContextHolder.require()));
            return new Ticket(true,ticket,requestId);
        }
    }
    public Stream open(String ticket,String origin,Consumer<Map<String,Object>> events) {
        Grant grant;
        synchronized(grants) { grant=grants.remove(ticket); }
        if(grant==null || grant.expires<System.currentTimeMillis() || !grant.origin.equals(origin(origin)))throw invalid();
        if(!slots.tryAcquire())throw new ServiceException("RATE_LIMITED: 实时识别连接已满",429);
        Live live=new Live(grant,events); streams.add(live);
        try { live.start(); return live; } catch(RuntimeException invalid) { live.abort(); throw invalid; }
    }
    static String origin(String value) {
        try { URI uri=URI.create(value); if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath()==null || uri.getPath().isEmpty()))throw invalid();
            return uri.toString();
        } catch(RuntimeException invalid) { throw invalid(); }
    }
    public interface Stream extends AutoCloseable {
        void audio(int sequence, byte[] pcm);
        void finish(int sequence);
        @Override void close();
    }
    public final class Live implements Stream {
        final Grant grant;
        final Consumer<Map<String,Object>> events;
        final MessageDigest digest;
        final AtomicBoolean done=new AtomicBoolean(), released=new AtomicBoolean();
        final long expires=System.currentTimeMillis()+45000;
        volatile long touched=System.currentTimeMillis();
        volatile CustomerServiceRealtimeSpeechGateway.Session upstream;
        volatile NormalizedUsage usage=NormalizedUsage.unknown();
        TokenQuotaService.TokenReservation reservation;
        volatile boolean begun, finishing;
        int bytes, sequence;
        Live(Grant grant,Consumer<Map<String,Object>> events) { this.grant=grant; this.events=events; try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);} }
        <T>T as(Supplier<T> work) {
            var prior=CallerContextHolder.get();
            // 延续票据签发时的真实主体；访客回调绝不提升为平台管理员。
            CallerContextHolder.set(grant.caller);
            try{return work.get();}finally{CallerContextHolder.set(prior);}
        }
        boolean active() { try{return as(() -> speech.streamActive(grant.session,grant.request));}catch(RuntimeException invalid){return false;} }
        void start() {
            try { as(() -> {
                if(!speech.streamPlan(grant.session).equals(grant.plan))throw invalid();
                speech.beginStream(grant.session,grant.request); begun=true;
                if(done.get())throw invalid();
                reservation=quotas.reserve(grant.plan.actor().tenantId().toString(),1000);
                if(done.get())throw invalid();
                Consumer<String> partial = text -> { if(!done.get() && active()) events.accept(Map.of("type","PARTIAL","text",text)); };
                Consumer<NormalizedUsage> measured = value -> { if(value!=null)usage=value; };
                upstream=grant.plan.vocabulary().isEmpty()
                        ? gateway.open(grant.plan.modelId(),grant.plan.modelHash(),this::active,partial,measured)
                        : gateway.open(grant.plan.modelId(),grant.plan.modelHash(),grant.plan.vocabulary(),this::active,partial,measured);
                if(done.get())throw invalid();
                return null;
            }); } finally {
                if(done.get()) {
                    closeProvider();
                    try { if(begun)as(() -> {speech.cancel(grant.session,grant.request);return null;}); }
                    finally { try{settle();}finally{release();} }
                }
            }
        }
        public synchronized void audio(int number,byte[] pcm) {
            check(); if(finishing || number!=sequence || pcm==null || pcm.length==0 || pcm.length>6400 || (pcm.length&1)!=0 || bytes+pcm.length>640000)throw invalid();
            touched=System.currentTimeMillis(); sequence++; bytes+=pcm.length; digest.update(pcm);
            as(() -> {upstream.audio(pcm);return null;});
        }
        public void finish(int number) {
            synchronized(this){check();if(finishing || sequence!=number || bytes<6400)throw invalid();finishing=true;touched=System.currentTimeMillis();}
            String text=null,error=null;
            try{text=as(() -> upstream.finish());}catch(RuntimeException invalid){
                String message=invalid.getMessage();
                error=message!=null && message.startsWith("CONTENT_BLOCKED:")?"CONTENT_BLOCKED"
                        :message!=null && message.startsWith("SPEECH_TEXT_INVALID:")?"SPEECH_TEXT_INVALID":"SPEECH_PROVIDER_FAILED";
            }
            if(!done.compareAndSet(false,true))return;
            String answer=text;
            try {
                try { settle(); } catch(RuntimeException failed) { error="USAGE_SETTLEMENT_FAILED"; }
                String failure=error;
                var view=as(() -> speech.finishStream(grant.session,grant.request,answer,failure,bytes,HexFormat.of().formatHex(digest.digest()),usage));
                events.accept(Map.of("type","COMPLETE","result",view));
            } finally{release();}
        }
        public void check() { if(done.get() || System.currentTimeMillis()>expires || System.currentTimeMillis()-touched>(finishing?15000:10000) || begun && !active())throw invalid(); }
        synchronized void settle() {
            if(reservation==null)return;
            var held=reservation;reservation=null;
            as(() -> {if(usage.totalTokens()==null)quotas.release(held);else quotas.complete(held,usage.totalTokens());return null;});
        }
        public void abort() {
            if(!done.compareAndSet(false,true))return;
            try {
                closeProvider();
                if(begun)as(() -> {
                    speech.cancel(grant.session,grant.request);
                    try{speech.finishStream(grant.session,grant.request,null,"SPEECH_INTERRUPTED",bytes,"0".repeat(64),usage);}catch(RuntimeException ignored){}
                    return null;
                });
            } finally{
                try{settle();}catch(RuntimeException ignored){}finally{release();}
                try{events.accept(Map.of("type","ERROR","code","SPEECH_INTERRUPTED"));}catch(RuntimeException ignored){}
            }
        }
        void release(){if(released.compareAndSet(false,true)){try{if(upstream!=null)upstream.close();}finally{streams.remove(this);slots.release();}}}
        void closeProvider(){try{if(upstream!=null)upstream.close();}catch(RuntimeException ignored){}}
        @Override public void close(){abort();}
    }
    @PreDestroy void shutdown(){cleanup.shutdownNow();for(var stream:streams){try{stream.abort();}catch(RuntimeException ignored){}}synchronized(grants){grants.clear();}}
    private static ServiceException invalid(){return new ServiceException("SPEECH_INTERRUPTED: 实时连接无效、已过期或输入不合法",409);}
    private record Grant(CustomerServiceSpeechService.StreamPlan plan,String session,String request,String origin,long expires,com.polaris.ai.core.context.CallerContext caller){}
    public record Ticket(boolean available,String ticket,String requestId){}
}
