package com.polaris.ai.dto;

import java.util.List;

/** AI 报告美化的结构化输出契约。 */
public record RefinedReportOutput(
        String domain,
        String executiveSummary,
        List<KpiCard> kpiCards,
        List<Visualization> visualizations,
        List<ActionItem> actionPlan)
{
    public record KpiCard(String label, String value, String status)
    {
    }

    public record Visualization(
            String chartType,
            String title,
            String description,
            ChartData chartData)
    {
    }

    public record ChartData(List<String> categories, List<Series> series)
    {
    }

    public record Series(String name, List<Double> data)
    {
    }

    public record ActionItem(
            String priority,
            String action,
            String owner,
            String deadline)
    {
    }
}
