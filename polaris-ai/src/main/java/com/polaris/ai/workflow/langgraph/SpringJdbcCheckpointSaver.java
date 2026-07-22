package com.polaris.ai.workflow.langgraph;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringJdbcCheckpointSaver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 自动检测并创建检查点表（系统启动时执行，具幂等性）
     */
    public void setup() {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS ai_graph_checkpoint (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "thread_id VARCHAR(128) NOT NULL," +
                    "checkpoint_id VARCHAR(128) NOT NULL," +
                    "parent_checkpoint_id VARCHAR(128)," +
                    "state_json LONGTEXT NOT NULL," +
                    "metadata_json TEXT," +
                    "status VARCHAR(20) DEFAULT 'running'," +
                    "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uk_thread_checkpoint (thread_id, checkpoint_id)," +
                    "INDEX idx_thread_status (thread_id, status)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            jdbcTemplate.execute(sql);
            log.info(">>> [LangGraph4j] SpringJdbcCheckpointSaver 检查点数据库表校验/初始化成功");
        } catch (Exception e) {
            log.error(">>> [LangGraph4j] 自动建表失败，请检查数据库用户 DDL 权限！", e);
            throw new RuntimeException("数据库建表失败", e);
        }
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        String threadId = config.threadId().orElseThrow(() -> new IllegalArgumentException("threadId is required"));
        String sql = "SELECT checkpoint_id, state_json FROM ai_graph_checkpoint " +
                "WHERE thread_id = ? AND status != 'error' ORDER BY create_time DESC";
        
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
        }, threadId);
        
        return new LinkedList<>(list);
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        String threadId = config.threadId().orElseThrow();
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
        
        String sql = "INSERT INTO ai_graph_checkpoint (thread_id, checkpoint_id, parent_checkpoint_id, state_json, status, workflow_code) " +
                "VALUES (?, ?, null, ?, 'running', ?)";
        jdbcTemplate.update(sql, threadId, checkpointId, stateJson, workflowCode);
        
        checkpoints.add(checkpoint);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        String threadId = config.threadId().orElseThrow();
        String checkpointId = checkpoint.getId();
        
        Map<String, Object> stateMap = new HashMap<>(checkpoint.getState());
        stateMap.put("__node_id__", checkpoint.getNodeId());
        if (checkpoint.getNextNodeId() != null) {
            stateMap.put("__next_node_id__", checkpoint.getNextNodeId());
        }
        String stateJson = objectMapper.writeValueAsString(stateMap);
        
        String sql = "UPDATE ai_graph_checkpoint SET state_json = ?, update_time = NOW() " +
                "WHERE thread_id = ? AND checkpoint_id = ?";
        jdbcTemplate.update(sql, stateJson, threadId, checkpointId);
        
        // 替换链表中旧的 checkpoint
        checkpoints.removeIf(cp -> cp.getId().equals(checkpointId));
        checkpoints.add(checkpoint);
    }

    @Override
    protected BaseCheckpointSaver.Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        String threadId = config.threadId().orElseThrow();
        String sql = "DELETE FROM ai_graph_checkpoint WHERE thread_id = ?";
        jdbcTemplate.update(sql, threadId);
        
        BaseCheckpointSaver.Tag tag = new BaseCheckpointSaver.Tag(threadId, new ArrayList<>(checkpoints));
        checkpoints.clear();
        return tag;
    }
}
