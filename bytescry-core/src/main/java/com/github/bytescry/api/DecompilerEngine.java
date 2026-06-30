package com.github.bytescry.api;

import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.DecompilationResult;
import com.github.bytescry.model.DecompilerOptions;

import java.util.List;

/**
 * Pluggable Java decompiler engine abstraction.
 */
public interface DecompilerEngine {

    /**
     * Decompile a single class file.
     */
    DecompilationResult decompile(ClassFile classFile, DecompilerOptions options);

    /**
     * Decompile a batch of class files.
     */
    default List<DecompilationResult> decompile(List<ClassFile> classFiles, DecompilerOptions options) {
        return classFiles.stream()
                .map(cf -> decompile(cf, options))
                .toList();
    }

    /**
     * Unique engine name used by the CLI to select the engine.
     */
    String getName();
}
