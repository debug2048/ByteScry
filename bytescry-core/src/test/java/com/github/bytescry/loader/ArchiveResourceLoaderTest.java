package com.github.bytescry.loader;

import com.github.bytescry.model.ResourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveResourceLoaderTest {
    @TempDir Path temp;
    private final ArchiveResourceLoader loader = new ArchiveResourceLoader();

    @Test
    void preservesAllNonClassEntriesAndTheirBytes() throws Exception {
        Map<String, byte[]> entries = Map.of(
                "application.yml", "name: 测试\n".getBytes(StandardCharsets.UTF_8),
                "templates/index.html", "<html>demo</html>".getBytes(StandardCharsets.UTF_8),
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8),
                "META-INF/services/example.Service", "example.Impl\n".getBytes(StandardCharsets.UTF_8),
                "static/logo.png", new byte[]{0, 1, 2, (byte) 255},
                "com/example/Demo.java", "original source resource".getBytes(StandardCharsets.UTF_8),
                "com/example/Demo.class", new byte[]{1},
                "com/example/Other.CLASS", new byte[]{2});
        Path jar = createJar("demo.jar", entries);
        List<ResourceFile> resources = loader.load(jar);
        assertThat(resources).hasSize(6);
        assertThat(resources).extracting(ResourceFile::relativePath)
                .doesNotContain("com/example/Demo.class", "com/example/Other.CLASS");

        Path output = temp.resolve("out/resources");
        assertThat(loader.export(resources, output)).isEqualTo(6);
        for (ResourceFile resource : resources) {
            assertThat(Files.readAllBytes(output.resolve(resource.relativePath())))
                    .isEqualTo(entries.get(resource.entryName()));
        }
        assertThat(loader.preview(find(resources, "application.yml"))).isEqualTo("name: 测试\n");
        assertThat(loader.preview(find(resources, "static/logo.png"))).contains("Binary");
    }

    @Test
    void retainsSpringBootPrefixesAndNestedJarBytes() throws Exception {
        byte[] nestedJar = {80, 75, 3, 4, 0, 0};
        Path jar = createJar("boot.jar", Map.of(
                "BOOT-INF/classes/application.properties", new byte[]{65},
                "BOOT-INF/lib/dependency.jar", nestedJar));
        List<ResourceFile> resources = loader.load(jar);
        Path output = temp.resolve("boot-output");
        assertThat(loader.export(resources, output)).isEqualTo(2);
        assertThat(Files.readAllBytes(output.resolve("BOOT-INF/lib/dependency.jar"))).isEqualTo(nestedJar);
        assertThat(output.resolve("BOOT-INF/classes/application.properties")).exists();
    }

    @Test
    void keepsResourcesFromDirectChildJarsSeparate() throws Exception {
        createJar("a.jar", Map.of("config.txt", new byte[]{65}));
        createJar("b.JAR", Map.of("config.txt", new byte[]{66}));
        Files.createDirectories(temp.resolve("nested"));
        createJar("nested/ignored.jar", Map.of("config.txt", new byte[]{67}));
        List<ResourceFile> resources = loader.load(temp);
        assertThat(resources).extracting(ResourceFile::relativePath)
                .containsExactly("a.jar/config.txt", "b.JAR/config.txt");
        Path output = temp.resolve("out");
        loader.export(resources, output);
        assertThat(Files.readString(output.resolve("a.jar/config.txt"))).isEqualTo("A");
        assertThat(Files.readString(output.resolve("b.JAR/config.txt"))).isEqualTo("B");
    }

    @Test
    void boundsPreviewButExportsCompleteLargeResource() throws Exception {
        byte[] bytes = new byte[300_000];
        java.util.Arrays.fill(bytes, (byte) 'a');
        ResourceFile resource = loader.load(createJar("large.jar", Map.of("large.txt", bytes))).get(0);
        assertThat(loader.preview(resource)).contains("too large to preview");
        Path output = temp.resolve("out");
        loader.export(List.of(resource), output);
        assertThat(Files.readAllBytes(output.resolve("large.txt"))).isEqualTo(bytes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escape.txt", "/absolute.txt", "dir/../../escape.txt", "..\\escape.txt",
            "C:/escape.txt", "file.txt:stream", "dir/./file.txt", "dir//file.txt"})
    void rejectsUnsafeArchivePathsBeforeWriting(String name) throws Exception {
        Path jar = createJar("unsafe.jar", Map.of(name, new byte[]{1}, "safe.txt", new byte[]{2}));
        Path output = temp.resolve("out");
        assertThatThrownBy(() -> loader.export(loader.load(jar), output))
                .isInstanceOf(IOException.class).hasMessageContaining("Unsafe resource path");
        assertThat(output).doesNotExist();
        assertThat(temp.resolve("escape.txt")).doesNotExist();
    }

    @Test
    void handlesEmptyAndNonJarInputs() throws Exception {
        assertThat(loader.load(createJar("empty.jar", Map.of()))).isEmpty();
        Path classFile = temp.resolve("Demo.class");
        Files.write(classFile, new byte[]{1});
        assertThat(loader.load(classFile)).isEmpty();
        Path output = temp.resolve("unused");
        assertThat(loader.export(List.of(), output)).isZero();
        assertThat(output).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"apk", "aab", "apks", "apkm", "xapk"})
    void indexesAndroidArchiveEntries(String extension) throws Exception {
        Path archive = createJar("android." + extension, Map.of(
                "classes.dex", new byte[]{1}, "assets/config.json", new byte[]{2},
                "res/layout/main.xml", new byte[]{3}, "lib/arm64-v8a/libsample.so", new byte[]{4},
                "AndroidManifest.xml", new byte[]{5}));
        assertThat(loader.load(archive)).extracting(ResourceFile::relativePath).containsExactly(
                "AndroidManifest.xml", "assets/config.json", "classes.dex", "lib/arm64-v8a/libsample.so", "res/layout/main.xml");
    }

    @Test
    void preservesExplicitEmptyDirectories() throws Exception {
        Path archive = createJar("directories.jar", Map.of("assets/empty/", new byte[0]));
        List<ResourceFile> resources = loader.load(archive);
        assertThat(resources).hasSize(1);
        assertThat(resources.get(0).directory()).isTrue();
        assertThat(resources.get(0).relativePath()).isEqualTo("assets/empty");
        Path output = temp.resolve("out");
        assertThat(loader.export(resources, output)).isZero();
        assertThat(output.resolve("assets/empty")).isDirectory();
    }

    @Test
    void browsesNestedApkResourcesButExportsTheOriginalApk() throws Exception {
        Path apk = createJar("base.apk", Map.of("assets/config.txt", "hello".getBytes(StandardCharsets.UTF_8)));
        byte[] apkBytes = Files.readAllBytes(apk);
        Path bundle = createJar("bundle.apks", Map.of("splits/base.apk", apkBytes));
        List<ResourceFile> resources = loader.load(bundle);
        assertThat(resources).extracting(ResourceFile::relativePath)
                .containsExactly("splits/base.apk", "splits/base.apk/assets/config.txt");
        assertThat(loader.preview(resources.get(1))).isEqualTo("hello");
        Path output = temp.resolve("out");
        assertThat(loader.export(resources, output)).isEqualTo(1);
        assertThat(Files.readAllBytes(output.resolve("splits/base.apk"))).isEqualTo(apkBytes);
    }

    private ResourceFile find(List<ResourceFile> resources, String path) {
        return resources.stream().filter(resource -> resource.relativePath().equals(path)).findFirst().orElseThrow();
    }

    private Path createJar(String name, Map<String, byte[]> entries) throws IOException {
        Path jar = temp.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return jar;
    }
}
