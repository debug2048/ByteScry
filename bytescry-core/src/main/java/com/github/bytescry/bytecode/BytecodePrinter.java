package com.github.bytescry.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Prints human-readable bytecode for a class using ASM's TraceClassVisitor.
 */
public final class BytecodePrinter {

    private BytecodePrinter() {
        // utility
    }

    public static String print(byte[] classBytes) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            ClassReader reader = new ClassReader(classBytes);
            TraceClassVisitor tracer = new TraceClassVisitor(null, new Textifier(), pw);
            reader.accept(tracer, ClassReader.SKIP_DEBUG);
        } catch (RuntimeException e) {
            return "/* Bytecode view is unavailable: " + e.getMessage() + " */";
        }
        return sw.toString();
    }
}
