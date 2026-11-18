package com.github.bytescry.gui;

import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.ResourceFile;

/**
 * Tree node item used in the GUI class explorer.
 */
class ClassNode {

    private final String displayName;
    private final ClassFile classFile;
    private final boolean folder;
    private final ResourceFile resourceFile;
    private final String path;

    ClassNode(String displayName, ClassFile classFile, boolean folder) {
        this.displayName = displayName;
        this.classFile = classFile;
        this.folder = folder;
        this.resourceFile = null;
        this.path = displayName;
    }

    private ClassNode(String path, ClassFile classFile, ResourceFile resourceFile, boolean folder) {
        this.displayName = path.substring(path.lastIndexOf('/') + 1);
        this.path = path;
        this.classFile = classFile;
        this.resourceFile = resourceFile;
        this.folder = folder;
    }

    static ClassNode directory(String path) {
        return new ClassNode(path, null, null, true);
    }

    static ClassNode classFile(String path, ClassFile classFile) {
        return new ClassNode(path, classFile, null, false);
    }

    static ClassNode resource(ResourceFile resource) {
        return new ClassNode(resource.relativePath(), null, resource, resource.directory());
    }

    String getPath() {
        return path;
    }

    ResourceFile getResourceFile() {
        return resourceFile;
    }

    String getDisplayName() {
        return displayName;
    }

    ClassFile getClassFile() {
        return classFile;
    }

    boolean isFolder() {
        return folder;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
