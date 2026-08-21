package com.polaris.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RAG 向量存储配置。
 */
@ConfigurationProperties(prefix = "ai.vector-store")
public class AiVectorStoreProperties
{
    public enum Type
    {
        MEMORY,
        QDRANT
    }

    public enum RebuildOnStartup
    {
        AUTO,
        ALWAYS,
        NEVER
    }

    private Type type = Type.MEMORY;

    /**
     * AUTO: 内存模式重建，持久化存储模式跳过。
     */
    private RebuildOnStartup rebuildOnStartup = RebuildOnStartup.AUTO;

    private QdrantProperties qdrant = new QdrantProperties();

    public boolean shouldRebuildOnStartup()
    {
        if (rebuildOnStartup == RebuildOnStartup.ALWAYS) {
            return true;
        }
        if (rebuildOnStartup == RebuildOnStartup.NEVER) {
            return false;
        }
        return type == Type.MEMORY;
    }

    public Type getType()
    {
        return type;
    }

    public void setType(Type type)
    {
        this.type = type == null ? Type.MEMORY : type;
    }

    public RebuildOnStartup getRebuildOnStartup()
    {
        return rebuildOnStartup;
    }

    public void setRebuildOnStartup(RebuildOnStartup rebuildOnStartup)
    {
        this.rebuildOnStartup = rebuildOnStartup == null ? RebuildOnStartup.AUTO : rebuildOnStartup;
    }

    public QdrantProperties getQdrant()
    {
        return qdrant;
    }

    public void setQdrant(QdrantProperties qdrant)
    {
        this.qdrant = qdrant == null ? new QdrantProperties() : qdrant;
    }

    public static class QdrantProperties
    {
        private String host = "localhost";
        private int port = 6334;
        private boolean useTls = false;
        private String apiKey;
        private String collectionName = "polaris_knowledge";
        private int dimension = 1024;
        private boolean onDisk = true;
        private boolean initializeSchema = true;
        private boolean createPayloadIndexes = true;
        private String payloadTextKey = "text_segment";
        private Duration timeout = Duration.ofSeconds(10);

        public String getHost()
        {
            return host;
        }

        public void setHost(String host)
        {
            this.host = host;
        }

        public int getPort()
        {
            return port;
        }

        public void setPort(int port)
        {
            this.port = port;
        }

        public boolean isUseTls()
        {
            return useTls;
        }

        public void setUseTls(boolean useTls)
        {
            this.useTls = useTls;
        }

        public String getApiKey()
        {
            return apiKey;
        }

        public void setApiKey(String apiKey)
        {
            this.apiKey = apiKey;
        }

        public String getCollectionName()
        {
            return collectionName;
        }

        public void setCollectionName(String collectionName)
        {
            this.collectionName = collectionName;
        }

        public int getDimension()
        {
            return dimension;
        }

        public void setDimension(int dimension)
        {
            this.dimension = dimension;
        }

        public boolean isOnDisk()
        {
            return onDisk;
        }

        public void setOnDisk(boolean onDisk)
        {
            this.onDisk = onDisk;
        }

        public boolean isInitializeSchema()
        {
            return initializeSchema;
        }

        public void setInitializeSchema(boolean initializeSchema)
        {
            this.initializeSchema = initializeSchema;
        }

        public boolean isCreatePayloadIndexes()
        {
            return createPayloadIndexes;
        }

        public void setCreatePayloadIndexes(boolean createPayloadIndexes)
        {
            this.createPayloadIndexes = createPayloadIndexes;
        }

        public String getPayloadTextKey()
        {
            return payloadTextKey;
        }

        public void setPayloadTextKey(String payloadTextKey)
        {
            this.payloadTextKey = payloadTextKey;
        }

        public Duration getTimeout()
        {
            return timeout;
        }

        public void setTimeout(Duration timeout)
        {
            this.timeout = timeout;
        }
    }
}
