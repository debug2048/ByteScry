package com.github.bytescry.simple;

import com.github.bytescry.api.DecompilerEngineRegistry;
import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.DecompilationResult;
import com.github.bytescry.model.DecompilerOptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleDecompilerTest {

    @Test
    void decompileAddMethod() {
        byte[] bytes = generateAdderClass();
        ClassFile cf = new ClassFile("com/example/Adder", bytes, "test");

        DecompilationResult result = DecompilerEngineRegistry.get("simple").decompile(cf, new DecompilerOptions());

        assertThat(result.hasError()).isFalse();
        String source = result.getSourceCode();
        assertThat(source).contains("class Adder");
        assertThat(source).contains("int add(");
        assertThat(source).contains("return");
    }

    @Test
    void decompileUnsupportedSwitch() {
        byte[] bytes = generateSwitchClass();
        ClassFile cf = new ClassFile("com/example/Switcher", bytes, "test");

        DecompilationResult result = DecompilerEngineRegistry.get("simple").decompile(cf, new DecompilerOptions());

        assertThat(result.hasError()).isFalse();
        assertThat(result.getSourceCode()).contains("Method body omitted by simple engine");
    }

    @Test
    void decompileConstructorUsesClassName() {
        byte[] bytes = generateConstructorClass();
        ClassFile cf = new ClassFile("com/example/CtorDemo", bytes, "test");

        DecompilationResult result = DecompilerEngineRegistry.get("simple").decompile(cf, new DecompilerOptions());

        assertThat(result.hasError()).isFalse();
        assertThat(result.getSourceCode()).contains("public CtorDemo(");
        assertThat(result.getSourceCode()).doesNotContain("void <init>");
    }

    @Test
    void decompileStaticInitializerDoesNotExposeClinit() {
        byte[] bytes = generateStaticInitializerClass();
        ClassFile cf = new ClassFile("com/example/StaticInitDemo", bytes, "test");

        DecompilationResult result = DecompilerEngineRegistry.get("simple").decompile(cf, new DecompilerOptions());

        assertThat(result.hasError()).isFalse();
        assertThat(result.getSourceCode()).contains("static {");
        assertThat(result.getSourceCode()).doesNotContain("<clinit>");
    }

    @Test
    void decompileIntegerComparisonKeepsOperandOrder() {
        byte[] bytes = generateComparisonClass();
        ClassFile cf = new ClassFile("com/example/CompareDemo", bytes, "test");

        DecompilationResult result = DecompilerEngineRegistry.get("simple").decompile(cf, new DecompilerOptions());

        assertThat(result.hasError()).isFalse();
        assertThat(result.getSourceCode()).contains("arg0 > arg1");
        assertThat(result.getSourceCode()).doesNotContain("arg1 > arg0");
    }

    private byte[] generateAdderClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Adder", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "add", "(II)I", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitInsn(Opcodes.IADD);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] generateSwitchClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Switcher", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "pick", "(I)I", null, null);
        mv.visitCode();
        org.objectweb.asm.Label l1 = new org.objectweb.asm.Label();
        org.objectweb.asm.Label l2 = new org.objectweb.asm.Label();
        org.objectweb.asm.Label l3 = new org.objectweb.asm.Label();
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitLookupSwitchInsn(l3, new int[]{1, 2}, new org.objectweb.asm.Label[]{l1, l2});
        mv.visitLabel(l1);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(l2);
        mv.visitInsn(Opcodes.ICONST_2);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(l3);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] generateConstructorClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/CtorDemo", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] generateStaticInitializerClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/StaticInitDemo", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] generateComparisonClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/CompareDemo", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "greater", "(II)I", null, null);
        Label trueLabel = new Label();
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitJumpInsn(Opcodes.IF_ICMPGT, trueLabel);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(trueLabel);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
