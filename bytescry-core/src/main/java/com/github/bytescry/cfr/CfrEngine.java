package com.github.bytescry.cfr;

import com.github.bytescry.api.DecompilerEngine;
import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.DecompilationResult;
import com.github.bytescry.model.DecompilerOptions;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * CFR-based decompiler engine.
 *
 * CFR is an open-source Java decompiler (MIT license) supporting Java 17 features such as
 * records, sealed classes, switch expressions and lambdas. This wrapper invokes CFR in a
 * separate process to avoid System.exit side effects and to use CFR's own class resolution.
 */
public class CfrEngine implements DecompilerEngine {

    public static final String NAME = "cfr";
    private static final long CFR_TIMEOUT_SECONDS = 90;
    private static final Map<String, Map<String, String>> SOURCE_CACHE = new ConcurrentHashMap<>();

    @Override
    public DecompilationResult decompile(ClassFile classFile, DecompilerOptions options) {
        long start = System.currentTimeMillis();
        Path tempDir = null;
        try {
            Path contextInput = resolveContextInput(classFile, options);
            Map<String, String> sources;
            if (contextInput != null) {
                if (Files.isDirectory(contextInput)) {
                    Path classInput = resolveClassInput(classFile, contextInput);
                    sources = classInput == null ? Map.of() : decompileContext(classInput, options);
                } else if (isJar(contextInput) && isInnerClass(classFile.getClassName())) {
                    tempDir = Files.createTempDirectory("bytescry-cfr-inner-");
                    Path classFilePath = tempDir.resolve(classFile.getClassName() + ".class");
                    Files.createDirectories(classFilePath.getParent());
                    Files.write(classFilePath, classFile.getBytes());
                    sources = runCfr(classFilePath, options);
                } else {
                    sources = decompileContext(contextInput, options);
                }
            } else {
                tempDir = Files.createTempDirectory("bytescry-cfr-");
                Path classFilePath = tempDir.resolve(classFile.getClassName() + ".class");
                Files.createDirectories(classFilePath.getParent());
                Files.write(classFilePath, classFile.getBytes());
                Path classInput = resolveClassInput(classFile, tempDir);
                sources = runCfr(classInput == null ? classFilePath : classInput, options);
            }

            String source = findSourceForClass(sources, classFile.getClassName());
            if (source == null) {
                source = "/* CFR produced no output for " + classFile.getClassName() + " */";
            }

            long elapsed = System.currentTimeMillis() - start;
            return new DecompilationResult(classFile.getClassName(), source, NAME, elapsed);
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
            Path contextInput = resolveContextInput(classFiles.get(0), options);
            if (contextInput == null) {
                tempDir = Files.createTempDirectory("bytescry-cfr-batch-");
                for (ClassFile classFile : classFiles) {
                    Path classFilePath = tempDir.resolve(classFile.getClassName() + ".class");
                    Files.createDirectories(classFilePath.getParent());
                    Files.write(classFilePath, classFile.getBytes());
                }
                contextInput = tempDir;
            }
            Map<String, String> sources;
            if (Files.isDirectory(contextInput)) {
                sources = decompileClassInputs(classFiles, contextInput, options);
            } else if (isJar(contextInput)) {
                sources = new HashMap<>(decompileContext(contextInput, options));
                List<ClassFile> innerClasses = classFiles.stream()
                        .filter(classFile -> isInnerClass(classFile.getClassName()))
                        .toList();
                if (!innerClasses.isEmpty()) {
                    tempDir = Files.createTempDirectory("bytescry-cfr-jar-inner-");
                    for (ClassFile classFile : classFiles) {
                        Path classFilePath = tempDir.resolve(classFile.getClassName() + ".class");
                        Files.createDirectories(classFilePath.getParent());
                        Files.write(classFilePath, classFile.getBytes());
                    }
                    sources.putAll(decompileClassInputs(innerClasses, tempDir, options));
                }
            } else {
                sources = decompileContext(contextInput, options);
            }
            long elapsed = Math.max(0, System.currentTimeMillis() - start);
            List<DecompilationResult> results = new ArrayList<>();
            for (ClassFile classFile : classFiles) {
                String source = findSourceForClass(sources, classFile.getClassName());
                if (source == null) {
                    results.add(DecompilationResult.error(classFile.getClassName(), NAME,
                            new IOException("CFR produced no output for " + classFile.getClassName())));
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

    private Map<String, String> decompileContext(Path input, DecompilerOptions options) throws IOException, InterruptedException {
        String cacheKey = cacheKey(input, options);
        Map<String, String> cached = SOURCE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Map<String, String> sources = runCfr(input, options);
        SOURCE_CACHE.put(cacheKey, sources);
        return sources;
    }

    private Map<String, String> decompileClassInputs(List<ClassFile> classFiles, Path root, DecompilerOptions options)
            throws IOException, InterruptedException {
        Map<String, String> sources = new HashMap<>();
        for (Path input : resolveClassInputs(classFiles, root)) {
            sources.putAll(decompileContext(input, options));
        }
        return sources;
    }

    private Map<String, String> runCfr(Path input, DecompilerOptions options) throws IOException, InterruptedException {
        Path outputDir = Files.createTempDirectory("bytescry-cfr-out-");
        Path nestedLibDir = Files.createTempDirectory("bytescry-cfr-nested-libs-");
        try {
            List<String> args = new ArrayList<>();
            args.add("java");
            args.add("-cp");
            args.add(findCfrJar());
            args.add("org.benf.cfr.reader.Main");
            args.add(input.toString());
            args.add("--outputdir");
            args.add(outputDir.toString());
            args.add("--showversion");
            args.add("false");
            args.add("--silent");
            args.add("true");
            addBestEffortOptions(args, options);
            addClassPathOptions(args, input, options, nestedLibDir);

            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(CFR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("CFR timed out after " + CFR_TIMEOUT_SECONDS + " seconds for " + input);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("CFR exited with code " + exitCode + ": " + output);
            }
            return readAllOutputs(outputDir);
        } finally {
            deleteQuietly(outputDir);
            deleteQuietly(nestedLibDir);
        }
    }

    private void addBestEffortOptions(List<String> args, DecompilerOptions options) {
        if (!options.isBestEffort()) {
            return;
        }
        addBooleanOption(args, "usenametable", true);
        addBooleanOption(args, "usesignatures", true);
        addBooleanOption(args, "decodelambdas", true);
        addBooleanOption(args, "recordtypes", true);
        addBooleanOption(args, "sealed", true);
        addBooleanOption(args, "tryresources", true);
        addBooleanOption(args, "stringconcat", true);
        addBooleanOption(args, "recover", true);
        addBooleanOption(args, "removeboilerplate", true);
        addBooleanOption(args, "hidebridgemethods", true);
        addBooleanOption(args, "removeinnerclasssynthetics", true);
    }

    private void addBooleanOption(List<String> args, String name, boolean value) {
        args.add("--" + name);
        args.add(Boolean.toString(value));
    }

    private void addClassPathOptions(List<String> args, Path input, DecompilerOptions options, Path nestedLibDir) {
        Set<String> classPath = new LinkedHashSet<>();
        classPath.addAll(options.getExtraClassPath());
        classPath.addAll(detectDirectClassPath(input));
        classPath.addAll(detectClassRoots(input, options));
        classPath.addAll(detectClassPath(input));
        classPath.addAll(extractNestedJarClassPath(input, nestedLibDir));
        classPath.addAll(extractWebInfClassPath(input, nestedLibDir));
        if (options.getInputPath() != null) {
            Path optionInput = Path.of(options.getInputPath());
            if (!optionInput.equals(input) && Files.exists(optionInput)) {
                classPath.addAll(detectDirectClassPath(optionInput));
                classPath.addAll(detectClassRoots(optionInput, options));
                classPath.addAll(detectClassPath(optionInput));
                classPath.addAll(extractNestedJarClassPath(optionInput, nestedLibDir));
                classPath.addAll(extractWebInfClassPath(optionInput, nestedLibDir));
            }
        }
        if (!classPath.isEmpty()) {
            args.add("--extraclasspath");
            args.add(String.join(File.pathSeparator, classPath));
        }
    }

    private List<String> detectDirectClassPath(Path input) {
        if (Files.isRegularFile(input) && isJar(input)) {
            return List.of(input.toAbsolutePath().toString());
        }
        return List.of();
    }

    private List<String> detectClassRoots(Path input, DecompilerOptions options) {
        List<String> roots = new ArrayList<>();
        if (Files.isRegularFile(input) && input.toString().toLowerCase().endsWith(".class")) {
            detectClassRoot(input).map(Path::toAbsolutePath).map(Path::toString).ifPresent(roots::add);
        }
        if (Files.isDirectory(input)) {
            roots.add(input.toAbsolutePath().toString());
        }
        if (options.getInputPath() != null) {
            Path optionInput = Path.of(options.getInputPath());
            if (Files.isDirectory(optionInput)) {
                roots.add(optionInput.toAbsolutePath().toString());
            }
        }
        return roots;
    }

    private Optional<Path> detectClassRoot(Path classFile) {
        try {
            String className = new ClassReader(Files.readAllBytes(classFile)).getClassName();
            if (className == null || className.isEmpty()) {
                return Optional.empty();
            }
            Path root = classFile.toAbsolutePath().normalize();
            int segmentCount = className.split("/").length;
            for (int i = 0; i < segmentCount; i++) {
                root = root.getParent();
                if (root == null) {
                    return Optional.empty();
                }
            }
            return Optional.of(root);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private List<String> detectClassPath(Path input) {
        List<String> jars = new ArrayList<>();
        Path base = Files.isDirectory(input) ? input : input.getParent();
        if (base == null) {
            return jars;
        }
        addJars(base.resolve("lib"), jars);
        addJars(base.resolve("BOOT-INF").resolve("lib"), jars);
        addJars(base.resolve("WEB-INF").resolve("lib"), jars);
        addSiblingJars(input, base, jars);
        return jars;
    }

    private void addJars(Path dir, List<String> jars) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .forEach(jars::add);
        } catch (IOException ignored) {
        }
    }

    private void addSiblingJars(Path input, Path base, List<String> jars) {
        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                    .filter(p -> !p.equals(input))
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .forEach(jars::add);
        } catch (IOException ignored) {
        }
    }

    private List<String> extractNestedJarClassPath(Path input, Path outputDir) {
        if (!Files.isRegularFile(input) || !input.toString().toLowerCase().endsWith(".jar")) {
            return List.of();
        }
        List<String> jars = new ArrayList<>();
        try (JarFile jarFile = new JarFile(input.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !(name.startsWith("BOOT-INF/lib/") || name.startsWith("WEB-INF/lib/"))
                        || !name.toLowerCase().endsWith(".jar")) {
                    continue;
                }
                Path target = outputDir.resolve(Path.of(name).getFileName().toString());
                try (var in = jarFile.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                jars.add(target.toAbsolutePath().toString());
            }
        } catch (IOException ignored) {
        }
        return jars;
    }

    private List<String> extractWebInfClassPath(Path input, Path outputDir) {
        if (!Files.isRegularFile(input) || !isJar(input)) {
            return List.of();
        }
        Path classesDir = outputDir.resolve("classes");
        boolean copied = false;
        try (JarFile jarFile = new JarFile(input.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                String prefix = null;
                if (name.startsWith("BOOT-INF/classes/")) {
                    prefix = "BOOT-INF/classes/";
                } else if (name.startsWith("WEB-INF/classes/")) {
                    prefix = "WEB-INF/classes/";
                }
                if (entry.isDirectory() || prefix == null || !name.toLowerCase().endsWith(".class")) {
                    continue;
                }
                Path target = classesDir.resolve(name.substring(prefix.length()));
                Files.createDirectories(target.getParent());
                try (var in = jarFile.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                copied = true;
            }
        } catch (IOException ignored) {
        }
        return copied ? List.of(classesDir.toAbsolutePath().toString()) : List.of();
    }

    private Path resolveContextInput(ClassFile classFile, DecompilerOptions options) {
        Path sourceJar = resolveSourceJar(classFile);
        if (options.getInputPath() != null) {
            Path inputPath = Path.of(options.getInputPath());
            if (Files.exists(inputPath)) {
                if (Files.isDirectory(inputPath) && sourceJar != null) {
                    return sourceJar;
                }
                return inputPath;
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

    private List<Path> resolveClassInputs(List<ClassFile> classFiles, Path root) {
        Set<Path> inputs = new LinkedHashSet<>();
        for (ClassFile classFile : classFiles) {
            Path input = resolveClassInput(classFile, root);
            if (input != null && Files.isRegularFile(input)) {
                inputs.add(input.toAbsolutePath().normalize());
            }
        }
        return List.copyOf(inputs);
    }

    private Path resolveClassInput(ClassFile classFile, Path root) {
        if (classFile.getSource() != null) {
            Path source = Path.of(classFile.getSource());
            if (Files.isRegularFile(source) && source.toString().toLowerCase().endsWith(".class")) {
                return source;
            }
        }
        if (root != null) {
            Path rootedClass = root.resolve(classFile.getClassName() + ".class");
            if (Files.isRegularFile(rootedClass)) {
                return rootedClass;
            }
        }
        return null;
    }

    private String cacheKey(Path input, DecompilerOptions options) throws IOException {
        long modified = Files.exists(input) ? Files.getLastModifiedTime(input).toMillis() : 0;
        long size = Files.isRegularFile(input) ? Files.size(input) : 0;
        return input.toAbsolutePath().normalize() + "|" + modified + "|" + size
                + "|" + options.getInputPath() + "|" + options.isBestEffort() + "|" + options.getExtraClassPath();
    }

    private String findCfrJar() throws IOException {
        URL url = CfrEngine.class.getClassLoader().getResource("org/benf/cfr/reader/Main.class");
        if (url == null) {
            throw new IOException("CFR not found on classpath");
        }
        String external = url.toExternalForm();
        if (external.startsWith("jar:file:")) {
            int idx = external.indexOf('!');
            URI jarUri = URI.create(external.substring("jar:".length(), idx));
            return new File(jarUri).getAbsolutePath();
        }
        throw new IOException("CFR is not packaged as a JAR: " + external);
    }

    private Map<String, String> readAllOutputs(Path outputDir) throws IOException {
        Map<String, String> sources = new HashMap<>();
        try (Stream<Path> stream = Files.walk(outputDir)) {
            List<Path> javaFiles = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
            for (Path file : javaFiles) {
                String internalName = outputDir.relativize(file).toString()
                        .replace(File.separatorChar, '/');
                internalName = internalName.substring(0, internalName.length() - ".java".length());
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
        String simpleMatch = findBySimpleName(sources, className);
        if (simpleMatch != null) {
            return simpleMatch;
        }
        String outerClassName = outerClassName(className);
        if (!outerClassName.equals(className)) {
            String outer = sources.get(outerClassName);
            if (outer != null) {
                return outer;
            }
        }
        return findBySimpleName(sources, outerClassName);
    }

    private String outerClassName(String className) {
        int idx = className.indexOf('$');
        return idx >= 0 ? className.substring(0, idx) : className;
    }

    private boolean isInnerClass(String className) {
        return className.indexOf('$') >= 0;
    }

    private boolean isJar(Path path) {
        return path.toString().toLowerCase().endsWith(".jar");
    }

    private String findBySimpleName(Map<String, String> sources, String className) {
        String simple = className.substring(className.lastIndexOf('/') + 1);
        return sources.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("/" + simple) || entry.getKey().equals(simple))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private void deleteQuietly(Path path) {
        try (var stream = Files.walk(path)) {
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
