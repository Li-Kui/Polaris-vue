package com.polaris.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动文档识别及 Prompt 融合结果
 */
@Schema(description = "自动文档识别结果")
public class DocumentRecognitionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "识别并挂载的文档列表")
    private List<RecognizedDoc> recognizedDocs = new ArrayList<>();

    @Schema(description = "构建并带有安全隔离标签的文档 Context Prompt")
    private String formattedContextPrompt;

    @Schema(description = "是否有识别挂载的文档")
    private boolean hasRecognizedDocs;

    private List<KnowledgeCitation> knowledgeCitations = List.of();
    private String retrievalStrategy = "VECTOR_V1";
    private List<String> missingRelatedDocumentIds = List.of();
    public String getRetrievalStrategy() {return retrievalStrategy;}
    public void setRetrievalStrategy(String value) {retrievalStrategy = value;}
    public List<String> getMissingRelatedDocumentIds() {return missingRelatedDocumentIds;}
    public void setMissingRelatedDocumentIds(List<String> value) {missingRelatedDocumentIds = List.copyOf(value);}

    public List<KnowledgeCitation> getKnowledgeCitations() {return knowledgeCitations;}
    public void setKnowledgeCitations(List<KnowledgeCitation> citations) {
        knowledgeCitations = citations == null ? List.of() : List.copyOf(citations);
    }

    public DocumentRecognitionResult() {
    }

    public DocumentRecognitionResult(List<RecognizedDoc> recognizedDocs, String formattedContextPrompt) {
        this.recognizedDocs = recognizedDocs != null ? recognizedDocs : new ArrayList<>();
        this.formattedContextPrompt = formattedContextPrompt;
        this.hasRecognizedDocs = !this.recognizedDocs.isEmpty();
    }

    public List<RecognizedDoc> getRecognizedDocs() {
        return recognizedDocs;
    }

    public void setRecognizedDocs(List<RecognizedDoc> recognizedDocs) {
        this.recognizedDocs = recognizedDocs;
        this.hasRecognizedDocs = recognizedDocs != null && !recognizedDocs.isEmpty();
    }

    public String getFormattedContextPrompt() {
        return formattedContextPrompt;
    }

    public void setFormattedContextPrompt(String formattedContextPrompt) {
        this.formattedContextPrompt = formattedContextPrompt;
    }

    public boolean isHasRecognizedDocs() {
        return hasRecognizedDocs;
    }

    public void setHasRecognizedDocs(boolean hasRecognizedDocs) {
        this.hasRecognizedDocs = hasRecognizedDocs;
    }
}
