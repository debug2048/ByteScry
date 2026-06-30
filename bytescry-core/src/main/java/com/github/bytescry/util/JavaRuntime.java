package com.github.bytescry.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the Java launcher from the runtime currently running ByteScry.
 */
public final class JavaRuntime {

    public static String javaExecutable() {
        String javaHome = System.getProperty("java.home", "");
        if (!javaHome.isBlank()) {
            Path executable = Path.of(javaHome, "bin", isWindows() ? "java.exe" : "java");
            if (Files.isRegularFile(executable)) {
                return executable.toString();
            }
        }
        return isWindows() ? "java.exe" : "java";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private JavaRuntime() {
        // utility
    }
}
