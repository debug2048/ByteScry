package com.github.bytescry.api;

import com.github.bytescry.cfr.CfrEngine;
import com.github.bytescry.jadx.JadxEngine;
import com.github.bytescry.simple.SimpleDecompiler;
import com.github.bytescry.vineflower.VineflowerEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registry of available decompiler engines.
 */
public class DecompilerEngineRegistry {

    private static final Map<String, DecompilerEngine> ENGINES = new HashMap<>();

    static {
        register(new CfrEngine());
        register(new VineflowerEngine());
        register(new JadxEngine());
        register(new SimpleDecompiler());
    }

    public static void register(DecompilerEngine engine) {
        ENGINES.put(engine.getName().toLowerCase(), engine);
    }

    public static DecompilerEngine get(String name) {
        DecompilerEngine engine = ENGINES.get(Objects.requireNonNull(name, "engine name").toLowerCase());
        if (engine == null) {
            throw new IllegalArgumentException("Unknown engine: " + name + ". Available: " + availableEngines());
        }
        return engine;
    }

    public static DecompilerEngine getDefault() {
        return ENGINES.get("cfr");
    }

    public static Set<String> availableEngines() {
        return Set.copyOf(ENGINES.keySet());
    }

    private DecompilerEngineRegistry() {
        // utility
    }
}
