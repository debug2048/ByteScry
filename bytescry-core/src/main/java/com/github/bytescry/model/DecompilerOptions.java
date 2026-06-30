package com.github.bytescry.model;

import java.util.List;

/**
 * Options passed to a {@link com.github.bytescry.api.DecompilerEngine}.
 */
public class DecompilerOptions {

    private final String targetJvmVersion;
    private final boolean printBytecode;
    private final String outputDirectory;
    private final String inputPath;
    private final List<String> extraClassPath;
    private final boolean bestEffort;
    private final boolean fallbackEnabled;

    public DecompilerOptions() {
        this(null, false, null);
    }

    public DecompilerOptions(String targetJvmVersion, boolean printBytecode, String outputDirectory) {
        this(targetJvmVersion, printBytecode, outputDirectory, null, List.of(), true, true);
    }

    public DecompilerOptions(String targetJvmVersion, boolean printBytecode, String outputDirectory,
                             String inputPath, List<String> extraClassPath,
                             boolean bestEffort, boolean fallbackEnabled) {
        this.targetJvmVersion = targetJvmVersion;
        this.printBytecode = printBytecode;
        this.outputDirectory = outputDirectory;
        this.inputPath = inputPath;
        this.extraClassPath = List.copyOf(extraClassPath == null ? List.of() : extraClassPath);
        this.bestEffort = bestEffort;
        this.fallbackEnabled = fallbackEnabled;
    }

    public String getTargetJvmVersion() {
        return targetJvmVersion;
    }

    public boolean isPrintBytecode() {
        return printBytecode;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public String getInputPath() {
        return inputPath;
    }

    public List<String> getExtraClassPath() {
        return extraClassPath;
    }

    public boolean isBestEffort() {
        return bestEffort;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public DecompilerOptions withTargetJvmVersion(String version) {
        return new DecompilerOptions(version, printBytecode, outputDirectory, inputPath, extraClassPath, bestEffort, fallbackEnabled);
    }

    public DecompilerOptions withPrintBytecode(boolean printBytecode) {
        return new DecompilerOptions(targetJvmVersion, printBytecode, outputDirectory, inputPath, extraClassPath, bestEffort, fallbackEnabled);
    }

    public DecompilerOptions withOutputDirectory(String outputDirectory) {
        return new DecompilerOptions(targetJvmVersion, printBytecode, outputDirectory, inputPath, extraClassPath, bestEffort, fallbackEnabled);
    }

    public DecompilerOptions withInputPath(String inputPath) {
        return new DecompilerOptions(targetJvmVersion, printBytecode, outputDirectory, inputPath, extraClassPath, bestEffort, fallbackEnabled);
    }

    public DecompilerOptions withExtraClassPath(List<String> extraClassPath) {
        return new DecompilerOptions(targetJvmVersion, printBytecode, outputDirectory, inputPath, extraClassPath, bestEffort, fallbackEnabled);
    }

    public DecompilerOptions withBestEffort(boolean bestEffort) {
        return new DecompilerOptions(targetJvmVersion, printBytecode, outputDirectory, inputPath, extraClassPath, bestEffort, fallbackEnabled);
    }

    public DecompilerOptions withFallbackEnabled(boolean fallbackEnabled) {
        return new DecompilerOptions(targetJvmVersion, printBytecode, outputDirectory, inputPath, extraClassPath, bestEffort, fallbackEnabled);
    }
}
