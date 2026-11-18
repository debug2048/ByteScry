package com.github.bytescry.gui;

import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.ResourceFile;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTreeBuilderTest {
    @Test
    void mixesClassesAndResourcesInTheirActualArchiveDirectories() {
        ClassFile app = javaClass("BOOT-INF/classes/com/example/Main.class");
        ClassFile versioned = javaClass("META-INF/versions/17/com/example/Main.class");
        ResourceFile settings = resource("BOOT-INF/classes/com/example/settings.xml");
        TreeItem<ClassNode> root = ProjectTreeBuilder.build(Path.of("sample.jar"), List.of(app, versioned), List.of(
                resource("application.yml"), settings, resource("BOOT-INF/lib/library.jar")), "");
        assertNull(find(root, "Resources"));
        assertNotNull(find(root, "application.yml"));
        assertSame(app, find(root, app.getEntryPath()).getValue().getClassFile());
        assertSame(versioned, find(root, versioned.getEntryPath()).getValue().getClassFile());
        assertSame(settings, find(root, settings.relativePath()).getValue().getResourceFile());
        assertEquals(2, find(root, "BOOT-INF/classes/com/example").getChildren().size());
        assertEquals("BOOT-INF/classes/com/example", find(root, "BOOT-INF/classes/com/example").getValue().getPath());
    }

    @Test
    void androidTreeKeepsDexFilesAndTheirClassesAlongsideResources() {
        ResourceFile dex = resource("classes2.dex");
        ClassFile clazz = new ClassFile("com/example/Main", new byte[0], "sample.apk",
                "classes2.dex/com/example/Main.java");
        TreeItem<ClassNode> root = ProjectTreeBuilder.build(Path.of("sample.apk"), List.of(clazz), List.of(
                resource("AndroidManifest.xml"), dex, resource("res/layout/main.xml"),
                resource("assets/config.json"), resource("lib/arm64-v8a/app.so")), "");
        assertEquals(5, root.getChildren().size());
        assertSame(dex, find(root, "classes2.dex").getValue().getResourceFile());
        assertFalse(find(root, "classes2.dex").getValue().isFolder());
        assertSame(clazz, find(root, clazz.getEntryPath()).getValue().getClassFile());
        assertNotNull(find(root, "res/layout/main.xml"));
    }

    @Test
    void keepsCaseSensitivePathsAndEmptyDirectories() {
        ResourceFile empty = new ResourceFile(Path.of("sample.jar"), "empty/", "empty", 0, true);
        TreeItem<ClassNode> root = ProjectTreeBuilder.build(null, List.of(), List.of(
                resource("Config/a.txt"), resource("config/a.txt"), empty), "");
        assertEquals(3, root.getChildren().size());
        assertNotSame(find(root, "Config"), find(root, "config"));
        assertTrue(find(root, "empty").getValue().isFolder());
    }

    @Test
    void searchesClassNamesAndFullResourcePathsWithoutChangingHierarchy() {
        ClassFile clazz = javaClass("BOOT-INF/classes/com/example/Main.class");
        List<ResourceFile> resources = List.of(resource("templates/index.html"), resource("templates/error.html"));
        TreeItem<ClassNode> filtered = ProjectTreeBuilder.build(null, List.of(clazz), resources, " TEMPLATES/INDEX ");
        assertEquals(1, filtered.getChildren().size());
        assertTrue(find(filtered, "templates").isExpanded());
        assertNotNull(find(filtered, "templates/index.html"));
        assertNull(find(filtered, "templates/error.html"));
        assertSame(clazz, find(ProjectTreeBuilder.build(null, List.of(clazz), resources, "com.example.Main"),
                clazz.getEntryPath()).getValue().getClassFile());
        assertTrue(ProjectTreeBuilder.build(null, List.of(clazz), resources, "missing").getChildren().isEmpty());
    }

    @Test
    void directoryWorkspacesKeepEachArtifactsEntriesTogether(@TempDir Path directory) {
        ClassFile clazz = new ClassFile("com/example/Main", new byte[0], directory.resolve("sample.jar").toString(),
                "com/example/Main.class");
        ResourceFile resource = new ResourceFile(directory.resolve("sample.jar"), "com/example/config.txt",
                "sample.jar/com/example/config.txt", 10);
        TreeItem<ClassNode> root = ProjectTreeBuilder.build(directory, List.of(clazz), List.of(resource), "");
        assertNotNull(find(root, "sample.jar/com/example/Main.class"));
        assertNotNull(find(root, "sample.jar/com/example/config.txt"));
        assertEquals(1, root.getChildren().size());
    }

    private ClassFile javaClass(String path) {
        return new ClassFile("com/example/Main", new byte[0], "sample.jar", path);
    }

    private ResourceFile resource(String path) {
        return new ResourceFile(Path.of("sample.jar"), path, path, 10);
    }

    private TreeItem<ClassNode> find(TreeItem<ClassNode> parent, String path) {
        TreeItem<ClassNode> node = parent;
        for (String part : path.split("/")) {
            node = node.getChildren().stream().filter(child -> child.getValue().getDisplayName().equals(part))
                    .findFirst().orElse(null);
            if (node == null) {
                return null;
            }
        }
        return node;
    }
}
