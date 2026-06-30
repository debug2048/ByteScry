package com.github.bytescry.loader;

import com.github.bytescry.model.ClassFile;
import com.github.bytescry.util.ClassNameUtils;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads {@link ClassFile} instances from .class files, .jar files, or directories.
 */
public class ClassFileLoader {

    public List<ClassFile> load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Path does not exist: " + path);
        }

        if (Files.isDirectory(path)) {
            return loadDirectory(path);
        }

        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".jar")) {
            return loadJar(path);
        }
        if (isAndroidArtifact(name)) {
            return loadAndroidArtifact(path);
        }
        if (name.endsWith(".class")) {
            return List.of(loadClassFile(path, path.getParent()));
        }

        throw new IOException("Unsupported input: " + path + " (expected .class, .jar, .apk, .dex or directory)");
    }

    public List<ClassFile> loadDirectory(Path directory) throws IOException {
        List<ClassFile> result = new ArrayList<>();
        try (var stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".class"))
                    .forEach(p -> {
                        try {
                            result.add(loadClassFile(p, directory));
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to load " + p, e);
                        }
                    });
        }
        for (Path artifact : directArtifacts(directory)) {
            String name = artifact.getFileName().toString().toLowerCase();
            if (name.endsWith(".jar")) {
                result.addAll(loadJar(artifact));
            } else if (isAndroidArtifact(name)) {
                result.addAll(loadAndroidArtifact(artifact));
            }
        }
        return result;
    }

    private List<Path> directArtifacts(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(this::isSupportedArtifact)
                    .sorted()
                    .toList();
        }
    }

    private boolean isSupportedArtifact(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".jar") || isAndroidArtifact(name);
    }

    public List<ClassFile> loadJar(Path jarPath) throws IOException {
        List<ClassFile> result = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            for (JarEntry entry : jarFile.stream().toList()) {
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".class")) {
                    continue;
                }
                String internalName = entry.getName().substring(0, entry.getName().length() - ".class".length());
                byte[] bytes;
                try (InputStream is = jarFile.getInputStream(entry)) {
                    bytes = is.readAllBytes();
                }
                String bytecodeName = new ClassReader(bytes).getClassName();
                if (bytecodeName != null && !bytecodeName.isEmpty()) {
                    internalName = bytecodeName;
                }
                result.add(new ClassFile(internalName, bytes, jarPath.toString()));
            }
        }
        return result;
    }

    private List<ClassFile> loadAndroidArtifact(Path path) throws IOException {
        byte[] marker = Files.readAllBytes(path);
        if (marker.length > 1024) {
            marker = java.util.Arrays.copyOf(marker, 1024);
        }
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        String safeName = baseName.replaceAll("[^A-Za-z0-9_$.-]", "_");
        return List.of(new ClassFile("artifact/" + safeName, marker, path.toString()));
    }

    private boolean isAndroidArtifact(String lowerName) {
        return lowerName.endsWith(".apk")
                || lowerName.endsWith(".dex")
                || lowerName.endsWith(".aab")
                || lowerName.endsWith(".apks")
                || lowerName.endsWith(".apkm")
                || lowerName.endsWith(".xapk");
    }

    private ClassFile loadClassFile(Path classFile, Path root) throws IOException {
        byte[] bytes = Files.readAllBytes(classFile);
        String internalName = ClassNameUtils.classFilePathToInternalName(classFile, root);
        // For single files the path may not contain the package, so read the real name from bytecode.
        if (!internalName.contains("/")) {
            ClassReader reader = new ClassReader(bytes);
            String bytecodeName = reader.getClassName();
            if (bytecodeName != null && !bytecodeName.isEmpty()) {
                internalName = bytecodeName;
            }
        }
        return new ClassFile(internalName, bytes, classFile.toString());
    }
}
