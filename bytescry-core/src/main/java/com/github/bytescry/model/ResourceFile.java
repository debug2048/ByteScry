package com.github.bytescry.model;

import java.nio.file.Path;

/** An archive entry descriptor. Contents are read only when previewing or exporting. */
public record ResourceFile(Path archive, String entryName, String relativePath, long size,
                           boolean directory, String containerEntry) {
    public ResourceFile(Path archive, String entryName, String relativePath, long size) {
        this(archive, entryName, relativePath, size, false, null);
    }

    public ResourceFile(Path archive, String entryName, String relativePath, long size, boolean directory) {
        this(archive, entryName, relativePath, size, directory, null);
    }
}
