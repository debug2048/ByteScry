package com.github.bytescry.gui;

import javafx.application.Application;

/**
 * Launcher entry point for the ByteScry GUI.
 *
 * JavaFX classpath applications should separate the launcher from the
 * Application subclass to avoid "JavaFX runtime components are missing" errors
 * when the application is packaged as a fat jar.
 */
public class ByteScryGuiLauncher {

    public static void main(String[] args) {
        Application.launch(ByteScryGui.class, args);
    }
}
