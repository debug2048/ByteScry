package com.github.bytescry.gui;

import com.github.bytescry.loader.ArchiveResourceLoader;
import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.ResourceFile;
import javafx.scene.control.TreeItem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A shared directory tree for actual archive entries and classes contained in DEX files. */
final class ProjectTreeBuilder {
    private ProjectTreeBuilder() {
    }

    static TreeItem<ClassNode> build(Path input, List<ClassFile> classes,
                                     List<ResourceFile> resources, String filter) {
        TreeItem<ClassNode> root = new TreeItem<>(new ClassNode("Project", null, true));
        Map<String, TreeItem<ClassNode>> nodes = new HashMap<>();
        nodes.put("", root);
        for (ResourceFile resource : resources) {
            put(nodes, resource.relativePath(), ClassNode.resource(resource));
        }
        for (ClassFile classFile : classes) {
            String path = classFile.getEntryPath();
            if (input != null && Files.isDirectory(input) && classFile.getSource() != null) {
                Path source = Path.of(classFile.getSource());
                if (ArchiveResourceLoader.isArchive(source)) {
                    path = input.toAbsolutePath().normalize().relativize(source.toAbsolutePath().normalize())
                            .toString().replace('\\', '/') + "/" + path;
                }
            }
            put(nodes, path, ClassNode.classFile(path, classFile));
        }
        String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        filterAndSort(root, query);
        root.setExpanded(true);
        return root;
    }

    private static void put(Map<String, TreeItem<ClassNode>> nodes, String path, ClassNode value) {
        TreeItem<ClassNode> node = ensure(nodes, path);
        node.setValue(value);
    }

    private static TreeItem<ClassNode> ensure(Map<String, TreeItem<ClassNode>> nodes, String path) {
        TreeItem<ClassNode> existing = nodes.get(path);
        if (existing != null) {
            return existing;
        }
        int slash = path.lastIndexOf('/');
        String parentPath = slash < 0 ? "" : path.substring(0, slash);
        TreeItem<ClassNode> parent = ensure(nodes, parentPath);
        TreeItem<ClassNode> node = new TreeItem<>(ClassNode.directory(path));
        nodes.put(path, node);
        parent.getChildren().add(node);
        return node;
    }

    private static boolean filterAndSort(TreeItem<ClassNode> node, String query) {
        node.getChildren().removeIf(child -> !filterAndSort(child, query));
        node.getChildren().sort(Comparator
                .comparing((TreeItem<ClassNode> child) -> !child.getValue().isFolder())
                .thenComparing(child -> child.getValue().getDisplayName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(child -> child.getValue().getDisplayName()));
        node.setExpanded(!query.isEmpty());
        ClassNode value = node.getValue();
        ClassFile classFile = value.getClassFile();
        return query.isEmpty() || !node.getChildren().isEmpty()
                || value.getPath().toLowerCase(Locale.ROOT).contains(query)
                || (classFile != null && classFile.getClassName().replace('/', '.').toLowerCase(Locale.ROOT).contains(query));
    }
}
