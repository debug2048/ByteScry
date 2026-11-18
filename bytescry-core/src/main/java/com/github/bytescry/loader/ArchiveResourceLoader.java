package com.github.bytescry.loader;

import com.github.bytescry.model.ResourceFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;

/** Indexes JAR and Android archive entries without loading whole resources into memory. */
public class ArchiveResourceLoader {
    private static final int PREVIEW_LIMIT = 256 * 1024;

    public List<ResourceFile> load(Path input) throws IOException {
        List<ResourceFile> resources = new ArrayList<>();
        if (Files.isDirectory(input)) {
            // Match ClassFileLoader's direct-child artifact discovery. Keep archives separate.
            try (var paths = Files.list(input)) {
                for (Path jar : paths.filter(Files::isRegularFile).filter(ArchiveResourceLoader::isArchive).sorted().toList()) {
                    loadJar(jar, jar.getFileName() + "/", resources);
                }
            }
        } else if (isArchive(input)) {
            loadJar(input, "", resources);
        }
        resources.sort(Comparator.comparing(ResourceFile::relativePath));
        return List.copyOf(resources);
    }

    public static boolean isArchive(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".apk") || name.endsWith(".aab")
                || name.endsWith(".apks") || name.endsWith(".apkm") || name.endsWith(".xapk");
    }

    public static boolean isSplitArchive(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".apks") || name.endsWith(".apkm") || name.endsWith(".xapk");
    }

    private void loadJar(Path path, String prefix, List<ResourceFile> resources) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    String name = entry.getName().substring(0, entry.getName().length() - 1);
                    resources.add(new ResourceFile(path, entry.getName(), prefix + name, 0, true));
                } else if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".class")
                        || !path.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    resources.add(new ResourceFile(path, entry.getName(), prefix + entry.getName(), entry.getSize()));
                    if (isSplitArchive(path) && entry.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                        loadNestedApk(path, jar, entry, prefix, resources);
                    }
                }
            }
        }
    }

    private void loadNestedApk(Path archive, JarFile jar, JarEntry apk, String prefix,
                               List<ResourceFile> resources) throws IOException {
        Path temporary = Files.createTempFile("bytescry-resource-index-", ".apk");
        try {
            try (InputStream input = jar.getInputStream(apk)) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            try (JarFile nested = new JarFile(temporary.toFile())) {
                var entries = nested.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.isDirectory()
                            ? entry.getName().substring(0, entry.getName().length() - 1) : entry.getName();
                    resources.add(new ResourceFile(archive, entry.getName(), prefix + apk.getName() + "/" + name,
                            entry.getSize(), entry.isDirectory(), apk.getName()));
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public String preview(ResourceFile resource) throws IOException {
        try (JarFile jar = new JarFile(resource.archive().toFile());
             InputStream input = openResource(jar, resource)) {
            byte[] bytes = input.readNBytes(PREVIEW_LIMIT + 1);
            if (bytes.length > PREVIEW_LIMIT) {
                return "Resource is too large to preview (limit: 256 KiB).\n"
                        + "Export Sources saves the complete resource file.";
            }
            for (byte value : bytes) {
                int c = value & 0xff;
                if (c < 32 && c != '\t' && c != '\r' && c != '\n' && c != '\f') {
                    return binaryPreview();
                }
            }
            try {
                return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                return binaryPreview();
            }
        }
    }

    private String binaryPreview() {
        return "Binary or non-UTF-8 resource.\nExport Sources saves the original resource file.";
    }

    /** Copies resources beneath the given resource directory, preserving their archive paths. */
    public int export(List<ResourceFile> resources, Path directory) throws IOException {
        Map<Path, List<ResourceFile>> archives = new LinkedHashMap<>();
        // Validate all paths before writing any resource.
        for (ResourceFile resource : resources) {
            // The enclosing APK is exported intact; its browsable children are already included.
            if (resource.containerEntry() != null) {
                continue;
            }
            outputPath(directory, resource.relativePath());
            archives.computeIfAbsent(resource.archive(), ignored -> new ArrayList<>()).add(resource);
        }
        int written = 0;
        for (var archive : archives.entrySet()) {
            try (JarFile jar = new JarFile(archive.getKey().toFile())) {
                for (ResourceFile resource : archive.getValue()) {
                    Path target = outputPath(directory, resource.relativePath());
                    if (resource.directory()) {
                        Files.createDirectories(target);
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    try (InputStream input = openResource(jar, resource)) {
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    written++;
                }
            }
        }
        return written;
    }

    private InputStream openResource(JarFile jar, ResourceFile resource) throws IOException {
        JarEntry entry = jar.getJarEntry(resource.containerEntry() == null ? resource.entryName() : resource.containerEntry());
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Resource no longer exists: " + resource.entryName());
        }
        InputStream input = jar.getInputStream(entry);
        if (resource.containerEntry() == null) {
            return input;
        }
        ZipInputStream nested = new ZipInputStream(input);
        try {
            for (var child = nested.getNextEntry(); child != null; child = nested.getNextEntry()) {
                if (child.getName().equals(resource.entryName()) && !child.isDirectory()) {
                    return nested;
                }
            }
            throw new IOException("Resource no longer exists: " + resource.relativePath());
        } catch (IOException | RuntimeException e) {
            nested.close();
            throw e;
        }
    }

    private Path outputPath(Path directory, String name) throws IOException {
        // Reject both ZIP and Windows traversal/absolute-path forms, on every platform.
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")) {
            throw new IOException("Unsafe resource path: " + name);
        }
        for (String segment : name.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("Unsafe resource path: " + name);
            }
        }
        try {
            Path root = directory.toAbsolutePath().normalize();
            Path target = root.resolve(name).normalize();
            if (!target.startsWith(root) || target.equals(root)) {
                throw new IOException("Unsafe resource path: " + name);
            }
            for (Path current = target; current != null; current = current.getParent()) {
                if (Files.isSymbolicLink(current)) {
                    throw new IOException("Resource output contains a symbolic link: " + current);
                }
            }
            return target;
        } catch (InvalidPathException e) {
            throw new IOException("Invalid resource path: " + name, e);
        }
    }
}
