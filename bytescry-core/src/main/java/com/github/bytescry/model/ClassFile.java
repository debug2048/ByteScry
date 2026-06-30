package com.github.bytescry.model;

import java.util.Objects;

/**
 * Internal representation of a loaded Java class file.
 */
public class ClassFile {

    private final String className;
    private final byte[] bytes;
    private final String source;

    public ClassFile(String className, byte[] bytes, String source) {
        this.className = Objects.requireNonNull(className, "className");
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
        this.source = source;
    }

    public String getClassName() {
        return className;
    }

    public byte[] getBytes() {
        return bytes.clone();
    }

    public String getSource() {
        return source;
    }

    public String getSimpleName() {
        int idx = className.lastIndexOf('/');
        return idx >= 0 ? className.substring(idx + 1) : className;
    }

    public String getPackageName() {
        int idx = className.lastIndexOf('/');
        return idx >= 0 ? className.substring(0, idx).replace('/', '.') : "";
    }

    public String getJavaFileName() {
        return getSimpleName() + ".java";
    }

    @Override
    public String toString() {
        return "ClassFile{" + "className='" + className + '\'' + ", source='" + source + '\'' + '}';
    }
}
