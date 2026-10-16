package com.polaris.ai.dto;

/** 服务端生成的本轮片段指针；不保存正文、私有路径或授权凭证。 */
public record KnowledgeCitation(String citationId, Long knowledgeBaseId, Long documentId, Long indexVersion,
                                String collectionHash, String chunkNo, String chunkHash,
                                int includedChars, boolean truncated) { }
