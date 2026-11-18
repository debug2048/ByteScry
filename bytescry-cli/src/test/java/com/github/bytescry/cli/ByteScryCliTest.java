package com.github.bytescry.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ByteScryCliTest {
    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(strings = {"simple", "cfr", "vineflower"})
    void exportsClassesAndOriginalResources(String engine) throws Exception {
        Path jar = createJar(true);
        Path output = temp.resolve("out");
        int exit = new CommandLine(new ByteScryCli()).execute(jar.toString(), "-e", engine, "-o", output.toString());
        assertEquals(0, exit);
        assertTrue(Files.readString(output.resolve("example/Demo.java")).contains("class Demo"));
        assertEquals("name: demo\n", Files.readString(output.resolve("resources/application.yml")));
        assertArrayEquals(new byte[]{0, 1, (byte) 255}, Files.readAllBytes(output.resolve("resources/static/icon.bin")));
        assertEquals("original resource", Files.readString(output.resolve("resources/example/Demo.java")));
        assertFalse(Files.exists(output.resolve("resources/example/Demo.class")));
    }

    @Test
    void exportsJarContainingOnlyResources() throws Exception {
        Path jar = createJar(false);
        Path output = temp.resolve("out");
        assertEquals(0, new CommandLine(new ByteScryCli()).execute(jar.toString(), "-o", output.toString()));
        assertEquals("name: demo\n", Files.readString(output.resolve("resources/application.yml")));
    }

    @Test
    void reportOnlyDoesNotExportSourcesOrResources() throws Exception {
        Path jar = createJar(true);
        Path output = temp.resolve("out");
        Path report = temp.resolve("report.md");
        assertEquals(0, new CommandLine(new ByteScryCli()).execute(jar.toString(), "-o", output.toString(),
                "--report", report.toString(), "--report-only"));
        assertTrue(Files.exists(report));
        assertFalse(Files.exists(output));
    }

    private Path createJar(boolean includeClass) throws Exception {
        Path jar = temp.resolve("sample.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            if (includeClass) {
                ClassWriter writer = new ClassWriter(0);
                writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Demo", null, "java/lang/Object", null);
                writer.visitEnd();
                add(out, "example/Demo.class", writer.toByteArray());
            }
            add(out, "application.yml", "name: demo\n".getBytes(StandardCharsets.UTF_8));
            add(out, "static/icon.bin", new byte[]{0, 1, (byte) 255});
            add(out, "example/Demo.java", "original resource".getBytes(StandardCharsets.UTF_8));
        }
        return jar;
    }

    private void add(JarOutputStream out, String path, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(path));
        out.write(bytes);
        out.closeEntry();
    }
}
