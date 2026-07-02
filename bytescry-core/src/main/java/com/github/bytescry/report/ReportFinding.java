package com.github.bytescry.report;

import java.util.Objects;

/**
 * A single deterministic analysis finding.
 */
public record ReportFinding(
        Severity severity,
        String category,
        String title,
        String location,
        String evidence
) {
    public ReportFinding {
        Objects.requireNonNull(severity, "severity");
        category = clean(category);
        title = clean(title);
        location = clean(location);
        evidence = clean(evidence);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
