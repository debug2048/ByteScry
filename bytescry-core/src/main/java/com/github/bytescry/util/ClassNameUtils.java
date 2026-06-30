package com.github.bytescry.util;

import java.nio.file.Path;

/**
 * Utilities for converting between paths, internal names and source file names.
 */
public final class ClassNameUtils {

    private ClassNameUtils() {
        // utility
    }

    /**
     * Extract the internal class name from a .class file path.
     * For example: /foo/bar/Baz.class -> foo/bar/Baz
     */
    public static String classFilePathToInternalName(Path classFile, Path root) {
        Path relative = root.relativize(classFile);
        String name = relative.toString()
                .replace(classFile.getFileSystem().getSeparator(), "/");
        if (name.endsWith(".class")) {
            name = name.substring(0, name.length() - ".class".length());
        }
        return name;
    }

    /**
     * Convert an internal class name to a relative Java source file path.
     * For example: foo/bar/Baz -> foo/bar/Baz.java
     */
    public static String internalNameToJavaPath(String internalName) {
        return internalName + ".java";
    }

    /**
     * Convert an internal class name to a relative directory path.
     * For example: foo/bar/Baz -> foo/bar
     */
    public static String internalNameToDirectory(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(0, idx) : "";
    }
}
