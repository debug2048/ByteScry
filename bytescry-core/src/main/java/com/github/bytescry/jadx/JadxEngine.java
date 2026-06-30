package com.github.bytescry.jadx;

import com.github.bytescry.api.ArtifactDecompilerEngine;
import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.DecompilationResult;
import com.github.bytescry.model.DecompilerOptions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * JADX-based Android artifact decompiler engine.
 */
public class JadxEngine implements ArtifactDecompilerEngine {

    public static final String NAME = "jadx";
    private static final long TIMEOUT_SECONDS = 180;
    private static final Map<String, Map<String, String>> SOURCE_CACHE = new ConcurrentHashMap<>();

    @Override
    public DecompilationResult decompile(ClassFile classFile, DecompilerOptions options) {
        long start = System.currentTimeMillis();
        try {
            Path input = resolveInput(classFile, options);
            if (input == null || !supportsArtifact(input)) {
                return DecompilationResult.error(classFile.getClassName(), NAME,
                        new IOException("JADX supports APK/DEX/AAB/APKS/APKM/XAPK inputs only"));
            }
            Map<String, String> sources = decompileArtifact(input);
            String source = findSourceForClass(sources, classFile.getClassName());
            if (source == null) {
                source = buildArtifactSummary(input, sources);
            }
            return new DecompilationResult(classFile.getClassName(), source, NAME,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return DecompilationResult.error(classFile.getClassName(), NAME, e);
        }
    }

    @Override
    public int exportArtifact(Path input, Path outputDir, DecompilerOptions options) throws IOException, InterruptedException {
        if (!supportsArtifact(input)) {
            throw new IOException("JADX supports APK/DEX/AAB/APKS/APKM/XAPK inputs only: " + input);
        }
        runJadx(input, outputDir);
        try (Stream<Path> stream = Files.walk(outputDir)) {
            return (int) stream.filter(p -> p.toString().endsWith(".java")).count();
        }
    }

    @Override
    public List<ClassFile> loadArtifactClasses(Path input, DecompilerOptions options) throws IOException, InterruptedException {
        if (!supportsArtifact(input)) {
            throw new IOException("JADX supports APK/DEX/AAB/APKS/APKM/XAPK inputs only: " + input);
        }
        Map<String, String> sources = decompileArtifact(input);
        return sources.keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> new ClassFile(name, new byte[0], input.toString()))
                .toList();
    }

    @Override
    public boolean supportsArtifact(Path input) {
        String lower = input.toString().toLowerCase();
        return lower.endsWith(".apk") || lower.endsWith(".dex") || lower.endsWith(".aab")
                || lower.endsWith(".apks") || lower.endsWith(".apkm") || lower.endsWith(".xapk");
    }

    private Map<String, String> decompileArtifact(Path input) throws IOException, InterruptedException {
        String cacheKey = cacheKey(input);
        Map<String, String> cached = SOURCE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Path outputDir = Files.createTempDirectory("bytescry-jadx-out-");
        try {
            runJadx(input, outputDir);
            Map<String, String> sources = readAllOutputs(outputDir);
            SOURCE_CACHE.put(cacheKey, sources);
            return sources;
        } finally {
            deleteQuietly(outputDir);
        }
    }

    private void runJadx(Path input, Path outputDir) throws IOException, InterruptedException {
        Files.createDirectories(outputDir);
        Path logFile = Files.createTempFile("bytescry-jadx-", ".log");
        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-cp",
                System.getProperty("java.class.path"),
                "jadx.cli.JadxCLI",
                "--no-res",
                "--show-bad-code",
                "-d",
                outputDir.toString(),
                input.toString()
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());
        Process process = pb.start();
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            deleteQuietly(logFile);
            throw new IOException("JADX timed out after " + TIMEOUT_SECONDS + " seconds for " + input);
        }
        int exitCode = process.exitValue();
        String output = Files.readString(logFile, StandardCharsets.UTF_8);
        deleteQuietly(logFile);
        if (exitCode != 0 && countJavaOutputs(outputDir) == 0) {
            throw new IOException("JADX exited with code " + exitCode + ": " + abbreviateOutput(output));
        }
    }

    private int countJavaOutputs(Path outputDir) throws IOException {
        if (!Files.isDirectory(outputDir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.walk(outputDir)) {
            return (int) stream.filter(p -> p.toString().endsWith(".java")).count();
        }
    }

    private String abbreviateOutput(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        int maxLength = 4000;
        if (output.length() <= maxLength) {
            return output;
        }
        return output.substring(0, maxLength) + "\n... truncated ...";
    }

    private Path resolveInput(ClassFile classFile, DecompilerOptions options) {
        if (options.getInputPath() != null) {
            Path input = Path.of(options.getInputPath());
            if (Files.exists(input)) {
                return input;
            }
        }
        if (classFile.getSource() != null) {
            Path input = Path.of(classFile.getSource());
            if (Files.exists(input)) {
                return input;
            }
        }
        return null;
    }

    private String cacheKey(Path input) throws IOException {
        long modified = Files.exists(input) ? Files.getLastModifiedTime(input).toMillis() : 0;
        long size = Files.isRegularFile(input) ? Files.size(input) : 0;
        return input.toAbsolutePath().normalize() + "|" + modified + "|" + size;
    }

    private Map<String, String> readAllOutputs(Path outputDir) throws IOException {
        Map<String, String> sources = new HashMap<>();
        try (Stream<Path> stream = Files.walk(outputDir)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = outputDir.relativize(file).toString().replace(File.separatorChar, '/');
                if (relative.startsWith("sources/")) {
                    relative = relative.substring("sources/".length());
                }
                String internalName = relative.substring(0, relative.length() - ".java".length());
                sources.put(internalName, Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    private String findSourceForClass(Map<String, String> sources, String className) {
        String exact = sources.get(className);
        if (exact != null) {
            return exact;
        }
        String simple = className.substring(className.lastIndexOf('/') + 1);
        return sources.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("/" + simple) || entry.getKey().equals(simple))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String buildArtifactSummary(Path input, Map<String, String> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Decompiled Android artifact with JADX: ").append(input).append("\n");
        sb.append("// Java source files: ").append(sources.size()).append("\n");
        sb.append("// Use Export Sources to write the complete JADX output.\n\n");
        sources.keySet().stream().sorted().limit(200)
                .forEach(name -> sb.append("// ").append(name.replace('/', '.')).append("\n"));
        if (sources.size() > 200) {
            sb.append("// ... ").append(sources.size() - 200).append(" more\n");
        }
        return sb.toString();
    }

    private void deleteQuietly(Path path) {
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    @Override
    public String getName() {
        return NAME;
    }
}
