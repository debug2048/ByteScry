package com.github.bytescry.report;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Structured output from the deterministic artifact scanner.
 */
public class AnalysisReport {

    private final Path inputPath;
    private final String artifactType;
    private final Instant generatedAt;
    private int classCount;
    private int methodCount;
    private int fieldCount;
    private final TreeSet<String> packages = new TreeSet<>();
    private final TreeSet<String> permissions = new TreeSet<>();
    private final TreeSet<String> urls = new TreeSet<>();
    private final TreeSet<String> domains = new TreeSet<>();
    private final TreeSet<String> suspiciousStrings = new TreeSet<>();
    private final List<ReportFinding> findings = new ArrayList<>();

    public AnalysisReport(Path inputPath, String artifactType, Instant generatedAt) {
        this.inputPath = inputPath;
        this.artifactType = artifactType;
        this.generatedAt = generatedAt;
    }

    public Path inputPath() {
        return inputPath;
    }

    public String artifactType() {
        return artifactType;
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    public int classCount() {
        return classCount;
    }

    void setClassCount(int classCount) {
        this.classCount = classCount;
    }

    public int methodCount() {
        return methodCount;
    }

    void incrementMethodCount() {
        methodCount++;
    }

    public int fieldCount() {
        return fieldCount;
    }

    void incrementFieldCount() {
        fieldCount++;
    }

    public List<String> packages() {
        return List.copyOf(packages);
    }

    void addPackage(String packageName) {
        if (packageName != null && !packageName.isBlank()) {
            packages.add(packageName);
        }
    }

    public List<String> permissions() {
        return List.copyOf(permissions);
    }

    void addPermission(String permission) {
        if (permission != null && !permission.isBlank()) {
            permissions.add(permission);
        }
    }

    public List<String> urls() {
        return List.copyOf(urls);
    }

    void addUrl(String url) {
        if (url != null && !url.isBlank()) {
            urls.add(url);
        }
    }

    public List<String> domains() {
        return List.copyOf(domains);
    }

    void addDomain(String domain) {
        if (domain != null && !domain.isBlank()) {
            domains.add(domain);
        }
    }

    public List<String> suspiciousStrings() {
        return List.copyOf(suspiciousStrings);
    }

    void addSuspiciousString(String value) {
        if (value != null && !value.isBlank()) {
            suspiciousStrings.add(value);
        }
    }

    public List<ReportFinding> findings() {
        return Collections.unmodifiableList(findings);
    }

    void addFinding(ReportFinding finding) {
        if (!findings.contains(finding)) {
            findings.add(finding);
        }
    }

    public Map<Severity, Long> findingCountsBySeverity() {
        Map<Severity, Long> counts = new TreeMap<>();
        for (Severity severity : Severity.values()) {
            counts.put(severity, 0L);
        }
        for (ReportFinding finding : findings) {
            counts.put(finding.severity(), counts.get(finding.severity()) + 1);
        }
        return counts;
    }
}
