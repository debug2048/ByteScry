package com.github.bytescry.gui;

import com.github.bytescry.model.ClassFile;

/**
 * Tree node item used in the GUI class explorer.
 */
class ClassNode {

    private final String displayName;
    private final ClassFile classFile;
    private final boolean folder;

    ClassNode(String displayName, ClassFile classFile, boolean folder) {
        this.displayName = displayName;
        this.classFile = classFile;
        this.folder = folder;
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
