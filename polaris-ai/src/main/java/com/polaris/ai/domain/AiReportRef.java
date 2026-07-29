package com.polaris.ai.domain;

/**
 * 报告归档引用。
 *
 * 仅用于让前端继续操作已归档报告，不返回报告正文和审计字段。
 */
public class AiReportRef {
    private Long id;
    private String reportCode;
    private String reportTitle;
    private String refineStatus;

    public AiReportRef() {
    }

    public AiReportRef(Long id, String reportCode, String reportTitle, String refineStatus) {
        this.id = id;
        this.reportCode = reportCode;
        this.reportTitle = reportTitle;
        this.refineStatus = refineStatus;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportCode() {
        return reportCode;
    }

    public void setReportCode(String reportCode) {
        this.reportCode = reportCode;
    }

    public String getReportTitle() {
        return reportTitle;
    }

    public void setReportTitle(String reportTitle) {
        this.reportTitle = reportTitle;
    }

    public String getRefineStatus() {
        return refineStatus;
    }

    public void setRefineStatus(String refineStatus) {
        this.refineStatus = refineStatus;
    }
}
