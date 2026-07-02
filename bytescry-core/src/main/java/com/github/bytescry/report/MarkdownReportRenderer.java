package com.github.bytescry.report;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Renders analysis reports without additional runtime dependencies.
 */
public class MarkdownReportRenderer {

    public String render(AnalysisReport report) {
        StringBuilder out = new StringBuilder();
        out.append("# ByteScry Analysis Report\n\n");
        out.append("- Input: `").append(escape(report.inputPath().toAbsolutePath().toString())).append("`\n");
        out.append("- Type: ").append(escape(report.artifactType())).append("\n");
        out.append("- Generated: ").append(report.generatedAt()).append("\n");
        out.append("- Classes: ").append(report.classCount()).append("\n");
        out.append("- Methods: ").append(report.methodCount()).append("\n");
        out.append("- Fields: ").append(report.fieldCount()).append("\n\n");

        appendSeveritySummary(out, report.findingCountsBySeverity());
        appendList(out, "Packages", report.packages(), 40);
        appendList(out, "Android Permissions", report.permissions(), 80);
        appendList(out, "URLs", report.urls(), 80);
        appendList(out, "Domains", report.domains(), 80);
        appendList(out, "Suspicious Strings", report.suspiciousStrings(), 80);
        appendFindings(out, report.findings());
        return out.toString();
    }

    private void appendSeveritySummary(StringBuilder out, Map<Severity, Long> counts) {
        out.append("## Summary\n\n");
        out.append("| Severity | Count |\n");
        out.append("| --- | ---: |\n");
        for (Severity severity : Severity.values()) {
            out.append("| ").append(severity).append(" | ").append(counts.getOrDefault(severity, 0L)).append(" |\n");
        }
        out.append("\n");
    }

    private void appendList(StringBuilder out, String title, List<String> values, int limit) {
        out.append("## ").append(title).append("\n\n");
        if (values.isEmpty()) {
            out.append("_None found._\n\n");
            return;
        }
        int count = 0;
        for (String value : values) {
            if (count++ >= limit) {
                out.append("- ... ").append(values.size() - limit).append(" more\n");
                break;
            }
            out.append("- `").append(escape(value)).append("`\n");
        }
        out.append("\n");
    }

    private void appendFindings(StringBuilder out, List<ReportFinding> findings) {
        out.append("## Findings\n\n");
        if (findings.isEmpty()) {
            out.append("_No rule findings._\n");
            return;
        }
        findings.stream()
                .sorted(Comparator.comparing(ReportFinding::severity).reversed()
                        .thenComparing(ReportFinding::category)
                        .thenComparing(ReportFinding::title)
                        .thenComparing(ReportFinding::location))
                .forEach(finding -> {
                    out.append("### ").append(finding.severity()).append(": ")
                            .append(escape(finding.title())).append("\n\n");
                    out.append("- Category: ").append(escape(finding.category())).append("\n");
                    if (!finding.location().isBlank()) {
                        out.append("- Location: `").append(escape(finding.location())).append("`\n");
                    }
                    if (!finding.evidence().isBlank()) {
                        out.append("- Evidence: `").append(escape(finding.evidence())).append("`\n");
                    }
                    out.append("\n");
                });
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("`", "\\`");
    }
}
