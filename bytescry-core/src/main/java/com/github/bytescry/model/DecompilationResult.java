package com.github.bytescry.model;

/**
 * Result of decompiling a single class file.
 */
public class DecompilationResult {

    private final String className;
    private final String sourceCode;
    private final String engine;
    private final long elapsedMillis;
    private final Throwable error;

    public DecompilationResult(String className, String sourceCode, String engine, long elapsedMillis) {
        this(className, sourceCode, engine, elapsedMillis, null);
    }

    public DecompilationResult(String className, String sourceCode, String engine, long elapsedMillis, Throwable error) {
        this.className = className;
        this.sourceCode = sourceCode;
        this.engine = engine;
        this.elapsedMillis = elapsedMillis;
        this.error = error;
    }

    public String getClassName() {
        return className;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getEngine() {
        return engine;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public Throwable getError() {
        return error;
    }

    public boolean hasError() {
        return error != null;
    }

    public static DecompilationResult error(String className, String engine, Throwable error) {
        return new DecompilationResult(className, null, engine, 0, error);
    }
}
