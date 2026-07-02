package com.github.bytescry.report;

import com.github.bytescry.model.ClassFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactReportGeneratorTest {

    @Test
    void detectsStringsAndRiskyCalls() throws Exception {
        ClassFile classFile = new ClassFile("com/example/Risky", riskyClassBytes(), "test");
        Path tempInput = Files.createTempFile("bytescry-report-test", ".class");
        Files.write(tempInput, classFile.getBytes());

        AnalysisReport report = new ArtifactReportGenerator().generate(tempInput, List.of(classFile));

        assertThat(report.classCount()).isEqualTo(1);
        assertThat(report.methodCount()).isGreaterThan(0);
        assertThat(report.urls()).contains("https://api.example.com/v1");
        assertThat(report.permissions()).contains("android.permission.READ_CONTACTS");
        assertThat(report.findings()).anySatisfy(finding -> {
            assertThat(finding.title()).isEqualTo("Runtime command execution");
            assertThat(finding.severity()).isEqualTo(Severity.HIGH);
        });
        assertThat(report.findings()).anySatisfy(finding ->
                assertThat(finding.title()).isEqualTo("Cipher algorithm selection"));

        String markdown = new MarkdownReportRenderer().render(report);
        assertThat(markdown).contains("# ByteScry Analysis Report");
        assertThat(markdown).contains("Runtime command execution");
    }

    private byte[] riskyClassBytes() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Risky", null, "java/lang/Object", null);

        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "scan", "()V", null, new String[]{"java/lang/Exception"});
        mv.visitCode();
        mv.visitLdcInsn("https://api.example.com/v1");
        mv.visitInsn(Opcodes.POP);
        mv.visitLdcInsn("android.permission.READ_CONTACTS");
        mv.visitInsn(Opcodes.POP);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Runtime", "getRuntime", "()Ljava/lang/Runtime;", false);
        mv.visitLdcInsn("id");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Runtime", "exec", "(Ljava/lang/String;)Ljava/lang/Process;", false);
        mv.visitInsn(Opcodes.POP);
        mv.visitLdcInsn("AES/CBC/PKCS5Padding");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "javax/crypto/Cipher", "getInstance",
                "(Ljava/lang/String;)Ljavax/crypto/Cipher;", false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
