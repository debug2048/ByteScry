package com.github.bytescry.simple;

import com.github.bytescry.api.DecompilerEngine;
import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.DecompilationResult;
import com.github.bytescry.model.DecompilerOptions;
import com.github.bytescry.util.TypeUtils;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

/**
 * A simplified educational decompiler engine for basic methods.
 *
 * This engine intentionally does not handle the full Java bytecode language.
 * For complex classes, use the CFR engine.
 */
public class SimpleDecompiler extends ClassVisitor implements DecompilerEngine {

    public static final String NAME = "simple";

    private static final int ASM_VERSION = Opcodes.ASM9;

    private final List<MethodResult> methods = new ArrayList<>();
    private String currentClassName;
    private String currentSuperName;
    private int currentAccess;
    private boolean unsupported;

    public SimpleDecompiler() {
        super(ASM_VERSION);
    }

    @Override
    public DecompilationResult decompile(ClassFile classFile, DecompilerOptions options) {
        long start = System.currentTimeMillis();
        try {
            SimpleDecompiler visitor = new SimpleDecompiler();
            ClassReader reader = new ClassReader(classFile.getBytes());
            reader.accept(visitor, ClassReader.SKIP_FRAMES);
            String source = visitor.generateSource(classFile.getClassName(), options);
            long elapsed = System.currentTimeMillis() - start;
            return new DecompilationResult(classFile.getClassName(), source, NAME, elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return DecompilationResult.error(classFile.getClassName(), NAME, e);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.currentClassName = name;
        this.currentAccess = access;
        this.currentSuperName = superName;
        this.unsupported = false;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodNode methodNode = new MethodNode(ASM_VERSION, access, name, descriptor, signature, exceptions);
        methods.add(new MethodResult(name, descriptor, access, methodNode));
        return methodNode;
    }

    private String generateSource(String internalName, DecompilerOptions options) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Decompiled by ByteScry simple engine (educational)\n");
        if (unsupported) {
            sb.append("// This class contains unsupported constructs; output is best-effort.\n");
        }

        String qualified = TypeUtils.internalToQualified(internalName);
        String packageName = packageName(qualified);
        if (!packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }

        sb.append(accessFlags(currentAccess & ~Opcodes.ACC_SUPER)).append("class ")
                .append(simpleName(qualified));
        if (currentSuperName != null && !"java/lang/Object".equals(currentSuperName)) {
            sb.append(" extends ").append(TypeUtils.internalToQualified(currentSuperName));
        }
        sb.append(" {\n");

        for (MethodResult method : methods) {
            sb.append("\n");
            try {
                sb.append(decompileMethod(method, options, simpleName(qualified)));
            } catch (UnsupportedOperationException e) {
                sb.append("    // ").append(e.getMessage()).append("\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String decompileMethod(MethodResult method, DecompilerOptions options, String classSimpleName) {
        MethodNode mn = method.node;
        StringBuilder sb = new StringBuilder();

        if ("<clinit>".equals(method.name)) {
            sb.append("    static {\n");
            sb.append("        // Static initializer omitted by simple engine.\n");
            sb.append("    }\n");
            return sb.toString();
        }

        boolean constructor = "<init>".equals(method.name);
        sb.append("    ").append(accessFlags(method.access & ~Opcodes.ACC_STATIC));
        if (!constructor) {
            sb.append(TypeUtils.descriptorToSource(Type.getReturnType(method.descriptor).getDescriptor()))
                    .append(" ");
        }
        sb.append(constructor ? classSimpleName : method.name).append("(");

        Type[] argTypes = Type.getArgumentTypes(method.descriptor);
        List<String> argNames = resolveArgNames(mn, argTypes.length, (method.access & Opcodes.ACC_STATIC) != 0);
        for (int i = 0; i < argTypes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(TypeUtils.descriptorToSource(argTypes[i].getDescriptor())).append(" ").append(argNames.get(i));
        }
        sb.append(") {\n");

        if (mn.instructions == null || mn.instructions.size() == 0) {
            sb.append("    }\n");
            return sb.toString();
        }

        ControlFlowGraph cfg = new ControlFlowGraph(mn);
        MethodEmitter emitter = new MethodEmitter(mn, argNames, (method.access & Opcodes.ACC_STATIC) != 0);
        String body = emitter.emit(cfg, options);
        sb.append(body);
        sb.append("    }\n");
        return sb.toString();
    }

    private static List<String> resolveArgNames(MethodNode mn, int argCount, boolean isStatic) {
        List<String> names = new ArrayList<>();
        if (mn.localVariables != null) {
            int slotOffset = isStatic ? 0 : 1;
            for (int i = 0; i < argCount; i++) {
                String found = null;
                for (LocalVariableNode lv : mn.localVariables) {
                    if (lv.index == i + slotOffset) {
                        found = lv.name;
                        break;
                    }
                }
                names.add(found != null ? found : "arg" + i);
            }
        } else {
            for (int i = 0; i < argCount; i++) {
                names.add("arg" + i);
            }
        }
        return names;
    }

    private static String accessFlags(int access) {
        StringBuilder sb = new StringBuilder();
        if ((access & Opcodes.ACC_PUBLIC) != 0) sb.append("public ");
        if ((access & Opcodes.ACC_PROTECTED) != 0) sb.append("protected ");
        if ((access & Opcodes.ACC_PRIVATE) != 0) sb.append("private ");
        if ((access & Opcodes.ACC_STATIC) != 0) sb.append("static ");
        if ((access & Opcodes.ACC_FINAL) != 0) sb.append("final ");
        if ((access & Opcodes.ACC_ABSTRACT) != 0) sb.append("abstract ");
        return sb.toString();
    }

    private static String packageName(String qualifiedName) {
        int idx = qualifiedName.lastIndexOf('.');
        return idx >= 0 ? qualifiedName.substring(0, idx) : "";
    }

    private static String simpleName(String qualifiedName) {
        int idx = qualifiedName.lastIndexOf('.');
        return idx >= 0 ? qualifiedName.substring(idx + 1) : qualifiedName;
    }

    private record MethodResult(String name, String descriptor, int access, MethodNode node) {
    }
}
