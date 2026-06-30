package com.github.bytescry.vineflower;

import com.github.bytescry.api.DecompilerEngine;
import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.DecompilationResult;
import com.github.bytescry.model.DecompilerOptions;
import com.github.bytescry.util.JavaRuntime;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Vineflower/Fernflower-based decompiler engine.
 */
public class VineflowerEngine implements DecompilerEngine {

    public static final String NAME = "vineflower";
    private static final long TIMEOUT_SECONDS = 90;
    private static final int MAX_CACHE_ENTRIES = 8;
    private static final Map<String, Map<String, String>> SOURCE_CACHE = new ConcurrentHashMap<>();

    @Override
    public DecompilationResult decompile(ClassFile classFile, DecompilerOptions options) {
        long start = System.currentTimeMillis();
        Path tempDir = null;
        try {
            Path input = resolveInput(classFile, options);
            if (input == null) {
                tempDir = Files.createTempDirectory("bytescry-vineflower-in-");
                input = tempDir.resolve(classFile.getClassName() + ".class");
                Files.createDirectories(input.getParent());
                Files.write(input, classFile.getBytes());
            }

            Map<String, String> sources = decompileInput(input, options);
            String source = findSourceForClass(sources, classFile.getClassName());
            if (source == null) {
                return DecompilationResult.error(classFile.getClassName(), NAME,
                        new IOException("Vineflower produced no output for " + classFile.getClassName()));
            }
            return new DecompilationResult(classFile.getClassName(), source, NAME,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return DecompilationResult.error(classFile.getClassName(), NAME, e);
        } finally {
            if (tempDir != null) {
                deleteQuietly(tempDir);
            }
        }
    }

    @Override
    public List<DecompilationResult> decompile(List<ClassFile> classFiles, DecompilerOptions options) {
        if (classFiles.isEmpty()) {
            return List.of();
        }
        long start = System.currentTimeMillis();
        Path tempDir = null;
        try {
            Path input = resolveBatchInput(classFiles.get(0), options);
            if (input == null) {
                return DecompilerEngine.super.decompile(classFiles, options);
            }

            Map<String, String> sources = new HashMap<>(decompileInput(input, options));
            if (isJar(input)) {
                List<ClassFile> innerClasses = classFiles.stream()
                        .filter(classFile -> isInnerClass(classFile.getClassName()))
                        .toList();
                if (!innerClasses.isEmpty()) {
                    tempDir = Files.createTempDirectory("bytescry-vineflower-jar-inner-");
                    for (ClassFile classFile : innerClasses) {
                        Path classInput = writeClassInput(tempDir, classFile);
                        sources.putAll(decompileInput(classInput, options));
                    }
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            List<DecompilationResult> results = new ArrayList<>();
            for (ClassFile classFile : classFiles) {
                String source = findSourceForClass(sources, classFile.getClassName());
                if (source == null) {
                    results.add(decompile(classFile, options));
                } else {
                    results.add(new DecompilationResult(classFile.getClassName(), source, NAME, elapsed));
                }
            }
            return results;
        } catch (Exception e) {
            return classFiles.stream()
                    .map(classFile -> DecompilationResult.error(classFile.getClassName(), NAME, e))
                    .toList();
        } finally {
            if (tempDir != null) {
                deleteQuietly(tempDir);
            }
        }
    }

    private Map<String, String> decompileInput(Path input, DecompilerOptions options) throws IOException, InterruptedException {
        String cacheKey = cacheKey(input, options);
        Map<String, String> cached = SOURCE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Path outputDir = Files.createTempDirectory("bytescry-vineflower-out-");
        try {
            List<String> args = new ArrayList<>();
            args.add(JavaRuntime.javaExecutable());
            args.add("-cp");
            args.add(findVineflowerJar());
            args.add("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler");
            addOptions(args);
            args.add(input.toString());
            args.add(outputDir.toString());

            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Vineflower timed out after " + TIMEOUT_SECONDS + " seconds for " + input);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("Vineflower exited with code " + exitCode + ": " + output);
            }
            Map<String, String> sources = readAllOutputs(outputDir);
            putCachedSources(cacheKey, sources);
            return sources;
        } finally {
            deleteQuietly(outputDir);
        }
    }

    private void addOptions(List<String> args) {
        args.add("-din=1");
        args.add("-rsy=1");
        args.add("-asc=1");
        args.add("-rbr=1");
        args.add("-dgs=1");
        args.add("-log=WARN");
    }

    private Path resolveBatchInput(ClassFile classFile, DecompilerOptions options) {
        Path sourceJar = resolveSourceJar(classFile);
        if (options.getInputPath() != null) {
            Path input = Path.of(options.getInputPath());
            if (Files.exists(input) && !isAndroidArtifact(input)) {
                if (Files.isDirectory(input) && sourceJar != null) {
                    return sourceJar;
                }
                return input;
            }
        }
        if (sourceJar != null) {
            return sourceJar;
        }
        return null;
    }

    private Path resolveSourceJar(ClassFile classFile) {
        if (classFile.getSource() != null) {
            Path source = Path.of(classFile.getSource());
            if (Files.exists(source) && source.toString().toLowerCase().endsWith(".jar")) {
                return source;
            }
        }
        return null;
    }

    private Path resolveInput(ClassFile classFile, DecompilerOptions options) {
        if (classFile.getSource() != null) {
            Path source = Path.of(classFile.getSource());
            if (Files.exists(source) && source.toString().toLowerCase().endsWith(".class")) {
                return source;
            }
        }
        if (isInnerClass(classFile.getClassName())) {
            return null;
        }
        Path batchInput = resolveBatchInput(classFile, options);
        if (batchInput != null) {
            return batchInput;
        }
        return null;
    }

    private Path writeClassInput(Path root, ClassFile classFile) throws IOException {
        Path input = root.resolve(classFile.getClassName() + ".class");
        Files.createDirectories(input.getParent());
        Files.write(input, classFile.getBytes());
        return input;
    }

    private boolean isInnerClass(String className) {
        return className.indexOf('$') >= 0;
    }

    private boolean isJar(Path input) {
        return input.toString().toLowerCase().endsWith(".jar");
    }

    private boolean isAndroidArtifact(Path input) {
        String lower = input.toString().toLowerCase();
        return lower.endsWith(".apk") || lower.endsWith(".dex") || lower.endsWith(".aab")
                || lower.endsWith(".xapk") || lower.endsWith(".apks") || lower.endsWith(".apkm");
    }

    private String cacheKey(Path input, DecompilerOptions options) throws IOException {
        long modified = Files.exists(input) ? Files.getLastModifiedTime(input).toMillis() : 0;
        long size = Files.isRegularFile(input) ? Files.size(input) : 0;
        return input.toAbsolutePath().normalize() + "|" + modified + "|" + size + "|" + options.getInputPath();
    }

    private void putCachedSources(String cacheKey, Map<String, String> sources) {
        while (SOURCE_CACHE.size() >= MAX_CACHE_ENTRIES && !SOURCE_CACHE.containsKey(cacheKey)) {
            String firstKey = SOURCE_CACHE.keySet().stream().findFirst().orElse(null);
            if (firstKey == null) {
                break;
            }
            SOURCE_CACHE.remove(firstKey);
        }
        SOURCE_CACHE.put(cacheKey, sources);
    }

    private String findVineflowerJar() throws IOException {
        URL url = VineflowerEngine.class.getClassLoader()
                .getResource("org/jetbrains/java/decompiler/main/decompiler/ConsoleDecompiler.class");
        if (url == null) {
            throw new IOException("Vineflower not found on classpath");
        }
        String external = url.toExternalForm();
        if (external.startsWith("jar:file:")) {
            int idx = external.indexOf('!');
            URI jarUri = URI.create(external.substring("jar:".length(), idx));
            return new File(jarUri).getAbsolutePath();
        }
        throw new IOException("Vineflower is not packaged as a JAR: " + external);
    }

    private Map<String, String> readAllOutputs(Path outputDir) throws IOException {
        Map<String, String> sources = new HashMap<>();
        try (Stream<Path> stream = Files.walk(outputDir)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = outputDir.relativize(file).toString().replace(File.separatorChar, '/');
                String internalName = relative.substring(0, relative.length() - ".java".length());
                String source = Files.readString(file, StandardCharsets.UTF_8);
                sources.put(internalName, source);
                String declaredName = declaredInternalName(source, internalName);
                sources.putIfAbsent(declaredName, source);
            }
        }
        return sources;
    }

    private String declaredInternalName(String source, String fallbackName) {
        String packageName = "";
        String simpleName = fallbackName.substring(fallbackName.lastIndexOf('/') + 1);
        for (String line : source.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ") && trimmed.endsWith(";")) {
                packageName = trimmed.substring("package ".length(), trimmed.length() - 1).replace('.', '/');
            }
            int classIndex = trimmed.indexOf(" class ");
            if (classIndex >= 0) {
                String[] parts = trimmed.substring(classIndex + " class ".length()).split("[\\s<{]");
                if (parts.length > 0 && !parts[0].isBlank()) {
                    simpleName = parts[0];
                    break;
                }
            }
        }
        return packageName.isEmpty() ? simpleName : packageName + "/" + simpleName;
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
