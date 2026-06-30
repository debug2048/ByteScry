package com.github.bytescry;

import com.github.bytescry.api.DecompilerEngineRegistry;
import com.github.bytescry.loader.ClassFileLoader;
import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.DecompilationResult;
import com.github.bytescry.model.DecompilerOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.*;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test: compile sample sources for each Java version and decompile with CFR.
 */
class VersionedDecompilationTest {

    private final ClassFileLoader loader = new ClassFileLoader();

    @Test
    void java7TryWithResources(@TempDir Path temp) throws Exception {
        List<ClassFile> classes = compileSample(temp, "samples/java7/TryWithResources.java", "7");

        DecompilationResult result = decompileFirst(classes);

        assertThat(result.hasError())
                .as("Decompilation error: %s", result.getError())
                .isFalse();
        assertThat(result.getSourceCode()).contains("TryWithResources");
        assertThat(result.getSourceCode()).contains("BufferedReader");
    }

    @Test
    void java8Lambda(@TempDir Path temp) throws Exception {
        List<ClassFile> classes = compileSample(temp, "samples/java8/LambdaDemo.java", "8");

        DecompilationResult result = decompileFirst(classes);

        assertThat(result.hasError())
                .as("Decompilation error: %s", result.getError())
                .isFalse();
        assertThat(result.getSourceCode()).contains("LambdaDemo");
        assertThat(result.getSourceCode()).contains("stream");
    }

    @Test
    void java11Var(@TempDir Path temp) throws Exception {
        List<ClassFile> classes = compileSample(temp, "samples/java11/VarDemo.java", "11");

        DecompilationResult result = decompileFirst(classes);

        assertThat(result.hasError())
                .as("Decompilation error: %s", result.getError())
                .isFalse();
        assertThat(result.getSourceCode()).contains("VarDemo");
    }

    @Test
    void java17Record(@TempDir Path temp) throws Exception {
        List<ClassFile> classes = compileSample(temp, "samples/java17/RecordDemo.java", "17");

        DecompilationResult result = decompileFirst(classes);

        assertThat(result.hasError())
                .as("Decompilation error: %s", result.getError())
                .isFalse();
        assertThat(result.getSourceCode()).contains("RecordDemo");
        assertThat(result.getSourceCode()).containsIgnoringCase("record");
    }

    @Test
    void vineflowerDecompilesJavaClass(@TempDir Path temp) throws Exception {
        List<ClassFile> classes = compileSample(temp, "samples/java8/LambdaDemo.java", "8");

        DecompilationResult result = DecompilerEngineRegistry.get("vineflower")
                .decompile(classes.get(0), new DecompilerOptions("17", false, null)
                        .withInputPath(temp.resolve("samples/java8").toString()));

        assertThat(result.hasError())
                .as("Decompilation error: %s", result.getError())
                .isFalse();
        assertThat(result.getSourceCode()).contains("LambdaDemo");
    }

    @Test
    void java17SealedClass(@TempDir Path temp) throws Exception {
        List<ClassFile> classes = compileSample(temp, "samples/java17/SealedClassDemo.java", "17");

        DecompilationResult sealed = classes.stream()
                .filter(cf -> cf.getClassName().equals("samples/java17/SealedClassDemo"))
                .findFirst()
                .map(this::decompile)
                .orElseThrow();

        assertThat(sealed.hasError())
                .as("Decompilation error: %s", sealed.getError())
                .isFalse();
        assertThat(sealed.getSourceCode()).containsIgnoringCase("sealed");
    }

    @Test
    void innerClassUsesOuterCfrOutput(@TempDir Path temp) throws Exception {
        Path sourceFile = temp.resolve("samples/java17/AgentConfig.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package samples.java17;

                public class AgentConfig {
                    public static class Budget {
                        public int limit() {
                            return 42;
                        }
                    }

                    public static class Approval {
                        public boolean allowed() {
                            return true;
                        }
                    }
                }
                """, StandardCharsets.UTF_8);

        compileSource(temp, sourceFile, "17");
        List<ClassFile> classes = loader.load(temp.resolve("samples/java17"));

        List<DecompilationResult> results = DecompilerEngineRegistry.get("cfr")
                .decompile(classes, new DecompilerOptions("17", false, null).withInputPath(temp.toString()));

        DecompilationResult inner = results.stream()
                .filter(result -> result.getClassName().equals("samples/java17/AgentConfig$Budget"))
                .findFirst()
                .orElseThrow();

        assertThat(inner.hasError())
                .as("Decompilation error: %s", inner.getError())
                .isFalse();
        assertThat(inner.getSourceCode()).contains("AgentConfig.Budget");
        assertThat(inner.getSourceCode()).contains("limit");
        assertThat(inner.getSourceCode()).doesNotContain("Could not load");
    }

    @Test
    void jarInnerClassUsesExactInnerCfrOutput(@TempDir Path temp) throws Exception {
        Path sourceFile = temp.resolve("src/com/harnex/config/AgentConfig.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package com.harnex.config;

                public class AgentConfig {
                    public static class Budget {
                        public int limit() {
                            return 42;
                        }
                    }

                    public static class Approval {
                        public boolean allowed() {
                            return true;
                        }
                    }
                }
                """, StandardCharsets.UTF_8);

        Path classesDir = temp.resolve("classes");
        compileSource(classesDir, sourceFile, "17");
        Path jar = temp.resolve("agent.jar");
        createBootJar(classesDir, jar);
        List<ClassFile> classes = loader.load(jar);
        assertThat(classes).extracting(ClassFile::getClassName)
                .contains("com/harnex/config/AgentConfig$Approval");

        List<DecompilationResult> results = DecompilerEngineRegistry.get("cfr")
                .decompile(classes, new DecompilerOptions("17", false, null).withInputPath(jar.toString()));

        DecompilationResult approval = results.stream()
                .filter(result -> result.getClassName().equals("com/harnex/config/AgentConfig$Approval"))
                .findFirst()
                .orElseThrow();

        assertThat(approval.hasError())
                .as("Decompilation error: %s", approval.getError())
                .isFalse();
        assertThat(approval.getSourceCode()).contains("AgentConfig.Approval");
        assertThat(approval.getSourceCode()).contains("allowed");
        assertThat(approval.getSourceCode()).doesNotContain("Could not load");
    }

    @Test
    void jarInnerClassUsesExactInnerVineflowerOutput(@TempDir Path temp) throws Exception {
        Path sourceFile = temp.resolve("src/com/harnex/config/AgentConfig.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package com.harnex.config;

                public class AgentConfig {
                    public static class Budget {
                        public int limit() {
                            return 42;
                        }
                    }

                    public static class Approval {
                        public boolean allowed() {
                            return true;
                        }
                    }
                }
                """, StandardCharsets.UTF_8);

        Path classesDir = temp.resolve("classes");
        compileSource(classesDir, sourceFile, "17");
        Path jar = temp.resolve("agent.jar");
        createBootJar(classesDir, jar);
        ClassFile approvalClass = loader.load(jar).stream()
                .filter(cf -> cf.getClassName().equals("com/harnex/config/AgentConfig$Approval"))
                .findFirst()
                .orElseThrow();

        DecompilationResult approval = DecompilerEngineRegistry.get("vineflower")
                .decompile(approvalClass, new DecompilerOptions("17", false, null).withInputPath(jar.toString()));

        assertThat(approval.hasError())
                .as("Decompilation error: %s", approval.getError())
                .isFalse();
        assertThat(approval.getSourceCode()).contains("Approval");
        assertThat(approval.getSourceCode()).contains("allowed");
    }

    private DecompilationResult decompileFirst(List<ClassFile> classes) {
        return decompile(classes.get(0));
    }

    private DecompilationResult decompile(ClassFile classFile) {
        return DecompilerEngineRegistry.get("cfr")
                .decompile(classFile, new DecompilerOptions("17", false, null));
    }

    private List<ClassFile> compileSample(Path temp, String resourcePath, String version) throws Exception {
        Path sourceFile = temp.resolve(resourcePath);
        Files.createDirectories(sourceFile.getParent());

        try (var is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Missing resource: " + resourcePath);
            }
            Files.writeString(sourceFile, new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available");
        }

        List<String> options = List.of(
                "-source", version,
                "-target", version,
                "-d", temp.toString()
        );

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));

        StringWriter sw = new StringWriter();
        boolean success = compiler.getTask(sw, fileManager, null, options, null, sources).call();
        fileManager.close();

        if (!success) {
            throw new IllegalStateException("Compilation failed for " + resourcePath + ": " + sw);
        }

        return loader.load(temp.resolve(resourcePath.substring(0, resourcePath.lastIndexOf('/'))));
    }

    private void compileSource(Path outputDir, Path sourceFile, String version) throws Exception {
        Files.createDirectories(outputDir);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available");
        }

        List<String> options = List.of(
                "-source", version,
                "-target", version,
                "-d", outputDir.toString()
        );

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));

        StringWriter sw = new StringWriter();
        boolean success = compiler.getTask(sw, fileManager, null, options, null, sources).call();
        fileManager.close();

        if (!success) {
            throw new IllegalStateException("Compilation failed for " + sourceFile + ": " + sw);
        }
    }

    private void createBootJar(Path classesDir, Path jar) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar));
             var stream = Files.walk(classesDir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String entryName = "BOOT-INF/classes/" + classesDir.relativize(file).toString().replace('\\', '/');
                out.putNextEntry(new JarEntry(entryName));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
    }
}
