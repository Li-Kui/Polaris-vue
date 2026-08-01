package com.polaris.ai.workflow.langgraph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.workflow.runtime.WorkflowExecutionStore;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.AbstractCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 融入 Spring 事务管理与数据源路由的自定义 JDBC 检查点存储器
 * 继承 AbstractCheckpointSaver，简化实现细节并兼容 Spring 事务生态
 *
 * @author polaris
 */
@Slf4j
@Component
public class SpringJdbcCheckpointSaver extends AbstractCheckpointSaver {

    private final JdbcTemplate jdbcTemplate;
    private final WorkflowExecutionStore executionStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringJdbcCheckpointSaver(
            JdbcTemplate jdbcTemplate, WorkflowExecutionStore executionStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.executionStore = executionStore;
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        String executionId = config.threadId().orElseThrow(() -> new IllegalArgumentException("threadId is required"));
        String sql = "SELECT checkpoint_id, state_json FROM ai_graph_checkpoint " +
                "WHERE execution_id = ? AND status NOT IN ('error','cancelled','rejected') " +
                "ORDER BY sequence_no DESC";
        
        List<Checkpoint> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            String checkpointId = rs.getString("checkpoint_id");
            String stateJson = rs.getString("state_json");
            try {
                Map<String, Object> state = new HashMap<>(objectMapper.readValue(stateJson, Map.class));
                
                // 还原 nodeId 和 nextNodeId，防止 requireNonNull 报错
                String nodeId = (String) state.getOrDefault("__node_id__", "__start__");
                String nextNodeId = (String) state.get("__next_node_id__");
                
                // 剔除临时包装属性，保证状态纯净
                state.remove("__node_id__");
                state.remove("__next_node_id__");

                return Checkpoint.builder()
                        .id(checkpointId)
                        .nodeId(nodeId)
                        .nextNodeId(nextNodeId)
                        .state(state)
                        .build();
            } catch (Exception e) {
                log.error(">>> [LangGraph4j] 反序列化 Checkpoint 失败！checkpointId: {}, stateJson: {}", checkpointId, stateJson, e);
                throw new RuntimeException("Deserialize checkpoint failed, id: " + checkpointId, e);
            }
        }, executionId);
        
        return new LinkedList<>(list);
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        String executionId = config.threadId().orElseThrow();
        String checkpointId = checkpoint.getId();
        
        Map<String, Object> stateMap = new HashMap<>(checkpoint.getState());
        stateMap.put("__node_id__", checkpoint.getNodeId());
        if (checkpoint.getNextNodeId() != null) {
            stateMap.put("__next_node_id__", checkpoint.getNextNodeId());
        }
        String stateJson = objectMapper.writeValueAsString(stateMap);
        
        String workflowCode = config.metadata("workflow_code")
                .map(Object::toString)
                .orElse("default_workflow");
        
        Long userId = metadataLong(config, "user_id");
        Long conversationId = metadataLong(config, "conversation_id");
        long sequence = executionStore.nextCheckpointSequence(executionId);

        String sql = "INSERT INTO ai_graph_checkpoint " +
                "(execution_id, sequence_no, checkpoint_id, parent_checkpoint_id, state_json, " +
                "status, workflow_code, user_id, conversation_id) " +
                "VALUES (?, ?, ?, null, ?, 'running', ?, ?, ?)";
        jdbcTemplate.update(sql, executionId, sequence, checkpointId, stateJson,
                workflowCode, userId, conversationId);
        
        checkpoints.push(checkpoint);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        String executionId = config.threadId().orElseThrow();
        String checkpointId = checkpoint.getId();
        
        Map<String, Object> stateMap = new HashMap<>(checkpoint.getState());
        stateMap.put("__node_id__", checkpoint.getNodeId());
        if (checkpoint.getNextNodeId() != null) {
            stateMap.put("__next_node_id__", checkpoint.getNextNodeId());
        }
        String stateJson = objectMapper.writeValueAsString(stateMap);
        
        String sql = "UPDATE ai_graph_checkpoint SET state_json = ?, update_time = NOW() " +
                "WHERE execution_id = ? AND checkpoint_id = ?";
        jdbcTemplate.update(sql, stateJson, executionId, checkpointId);
        
        // 替换链表中旧的 checkpoint
        checkpoints.removeIf(cp -> cp.getId().equals(checkpointId));
        checkpoints.push(checkpoint);
    }

    @Override
    protected BaseCheckpointSaver.Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        String executionId = config.threadId().orElseThrow();
        String sql = "DELETE FROM ai_graph_checkpoint WHERE execution_id = ?";
        jdbcTemplate.update(sql, executionId);
        
        BaseCheckpointSaver.Tag tag = new BaseCheckpointSaver.Tag(executionId, new ArrayList<>(checkpoints));
        checkpoints.clear();
        return tag;
    }

    private Long metadataLong(RunnableConfig config, String key) {
        return config.metadata(key)
                .map(value -> value instanceof Number number
                        ? number.longValue()
                        : Long.valueOf(value.toString()))
                .orElse(null);
    }
}
