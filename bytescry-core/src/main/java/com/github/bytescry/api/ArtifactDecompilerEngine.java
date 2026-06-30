package com.github.bytescry.api;

import com.github.bytescry.model.DecompilerOptions;
import com.github.bytescry.model.ClassFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Optional capability for engines that can decompile a whole artifact directly.
 */
public interface ArtifactDecompilerEngine extends DecompilerEngine {

    boolean supportsArtifact(Path input);

    int exportArtifact(Path input, Path outputDir, DecompilerOptions options) throws IOException, InterruptedException;

    default List<ClassFile> loadArtifactClasses(Path input, DecompilerOptions options) throws IOException, InterruptedException {
        return List.of();
    }
}
