package com.github.bytescry.jadx;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.DecompilerOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class JadxArchiveStructureTest {
    @TempDir Path temp;
    private final JadxEngine engine = new JadxEngine();
    private final DecompilerOptions options = new DecompilerOptions("17", false, null);

    @Test
    void indexesEachClassUnderItsActualDexAndStillDecompiles() throws Exception {
        Path apk = archive("sample.apk", Map.of(
                "classes.dex", dex("example/Main"), "classes2.dex", dex("other/Second")));
        List<ClassFile> classes = engine.loadArtifactClasses(apk, options);
        assertThat(classes).extracting(ClassFile::getEntryPath).containsExactly(
                "classes.dex/example/Main.java", "classes2.dex/other/Second.java");
        var source = engine.decompile(classes.get(0), options);
        assertThat(source.hasError()).isFalse();
        assertThat(source.getSourceCode()).contains("class Main");
    }

    @Test
    void retainsAabModuleAndDexDirectories() throws Exception {
        Path aab = archive("sample.aab", Map.of("base/dex/classes.dex", dex("example/Main")));
        assertThat(engine.loadArtifactClasses(aab, options)).extracting(ClassFile::getEntryPath)
                .containsExactly("base/dex/classes.dex/example/Main.java");
    }

    @Test
    void retainsNestedApkOwnersInSplitPackages() throws Exception {
        Path apks = archive("sample.apks", Map.of(
                "splits/base.apk", zip(Map.of("classes.dex", dex("example/Main"))),
                "splits/feature.apk", zip(Map.of("classes.dex", dex("feature/Feature")))));
        assertThat(engine.loadArtifactClasses(apks, options)).extracting(ClassFile::getEntryPath)
                .containsExactlyInAnyOrder("splits/base.apk/classes.dex/example/Main.java",
                        "splits/feature.apk/classes.dex/feature/Feature.java");
    }

    @Test
    void supportsStandaloneDexAndResourceOnlyApk() throws Exception {
        Path dex = temp.resolve("standalone.dex");
        Files.write(dex, dex("example/Main"));
        assertThat(engine.loadArtifactClasses(dex, options)).extracting(ClassFile::getEntryPath)
                .containsExactly("standalone.dex/example/Main.java");
        Path apk = archive("resources.apk", Map.of("assets/config.txt", new byte[]{65}));
        assertThat(engine.loadArtifactClasses(apk, options)).isEmpty();
    }

    private byte[] dex(String name) throws Exception {
        Path file = Files.createTempFile(temp, "fixture-", ".dex");
        ImmutableClassDef cls = new ImmutableClassDef("L" + name + ";", 1, "Ljava/lang/Object;",
                List.of(), null, List.of(), List.of(), List.of());
        DexPool.writeTo(file.toString(), new ImmutableDexFile(Opcodes.getDefault(), List.of(cls)));
        return Files.readAllBytes(file);
    }

    private Path archive(String name, Map<String, byte[]> entries) throws Exception {
        Path file = temp.resolve(name);
        Files.write(file, zip(entries));
        return file;
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
