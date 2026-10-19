package com.polaris.ai.agent.collaboration;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.AiKnowledgeMapper;
import com.polaris.ai.modelcenter.runtime.ModelDefinitionResolver;
import com.polaris.ai.rag.AiVectorStoreResolver;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.Code;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.Failure;

/** 知识冻结摘要只读取元数据，不生成向量或请求向量库。 */
@Service
public class AgentKnowledgeSnapshotService {
    private final AiKnowledgeMapper knowledge;
    private final ModelDefinitionResolver models;
    private final AiVectorStoreResolver vectors;
    private final AgentToolCatalog actors;
    public AgentKnowledgeSnapshotService(AiKnowledgeMapper knowledge, ModelDefinitionResolver models,
            AiVectorStoreResolver vectors, AgentToolCatalog actors) {
        this.knowledge = knowledge; this.models = models; this.vectors = vectors; this.actors = actors;
    }
    public record Snapshot(String knowledgeId, String definitionHash, String indexVersion) { }
    public Snapshot resolve(Long tenant, Long user, Long id) {
        var actor = actors.requireActor(tenant, user); var previous = CallerContextHolder.get();
        try {
            CallerContextHolder.set(actor);
            AiKnowledgeBase row = knowledge.selectWorkflowResource(tenant, id);
            if (row == null || !tenant.equals(row.getTenantId()) || !"0".equals(row.getDelFlag())
                    || !"READY".equals(row.getIndexStatus()) || row.getIndexVersion() == null || row.getIndexVersion() < 1
                    || row.getVectorCollection() == null || row.getVectorCollection().isBlank() || row.getEmbeddingModelId() == null)
                throw new Failure(Code.KNOWLEDGE_UNAVAILABLE);
            var model = models.resolve(row.getEmbeddingModelId(), "TEXT_EMBEDDING");
            if (!Objects.equals(row.getEmbeddingModelRevision(), model.modelRevision())) throw new Failure(Code.SNAPSHOT_CHANGED);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("knowledgeId", id); fields.put("tenantId", tenant); fields.put("indexVersion", row.getIndexVersion());
            fields.put("indexGeneration", row.getIndexGeneration()); fields.put("indexSignature", row.getIndexSignature());
            fields.put("collection", row.getVectorCollection()); fields.put("dimension", row.getEmbeddingDimension());
            fields.put("schemaHash", row.getEmbeddingSchemaHash()); fields.put("modelHash", model.runtimeDefinitionHash());
            fields.put("storage", vectors.storageIdentity());
            return new Snapshot(id.toString(), AgentBindingService.digest(AgentPlanValidator.JSON.writeValueAsString(
                    AgentPlanValidator.sorted(AgentPlanValidator.JSON.valueToTree(fields)))), row.getIndexVersion().toString());
        } catch (Failure safe) { throw safe; }
        catch (RuntimeException unavailable) { throw new Failure(Code.KNOWLEDGE_UNAVAILABLE); }
        finally { CallerContextHolder.set(previous); }
    }
    public void requireCurrent(Long tenant, Long user, String id, String hash) {
        try { if (!resolve(tenant, user, Long.valueOf(id)).definitionHash().equals(hash)) throw new Failure(Code.SNAPSHOT_CHANGED); }
        catch (Failure safe) { throw safe; }
        catch (RuntimeException invalid) { throw new Failure(Code.KNOWLEDGE_UNAVAILABLE); }
    }
}
