package com.github.bytescry.loader;

import com.github.bytescry.model.ClassFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ClassFileLoaderTest {

    private final ClassFileLoader loader = new ClassFileLoader();

    @Test
    void loadSingleClass(@TempDir Path temp) throws Exception {
        Path classFile = temp.resolve("com/example/Demo.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, minimalClassBytes("com/example/Demo"));

        List<ClassFile> loaded = loader.load(classFile);

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getClassName()).isEqualTo("com/example/Demo");
    }

    @Test
    void loadDirectory(@TempDir Path temp) throws Exception {
        Path classFile = temp.resolve("com/example/Demo.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, minimalClassBytes("com/example/Demo"));

        List<ClassFile> loaded = loader.load(temp);

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getClassName()).isEqualTo("com/example/Demo");
    }

    @Test
    void loadDirectoryWithJar(@TempDir Path temp) throws Exception {
        Path jarFile = temp.resolve("demo.jar");
        createJar(jarFile, "com/example/JarDemo");

        List<ClassFile> loaded = loader.load(temp);

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getClassName()).isEqualTo("com/example/JarDemo");
        assertThat(loaded.get(0).getSource()).isEqualTo(jarFile.toString());
    }

    @Test
    void loadDirectoryWithClassesAndJars(@TempDir Path temp) throws Exception {
        Path classFile = temp.resolve("com/example/DirDemo.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, minimalClassBytes("com/example/DirDemo"));
        Path jarFile = temp.resolve("demo.jar");
        createJar(jarFile, "com/example/JarDemo");

        List<ClassFile> loaded = loader.load(temp);

        assertThat(loaded).extracting(ClassFile::getClassName)
                .containsExactlyInAnyOrder("com/example/DirDemo", "com/example/JarDemo");
    }

    @Test
    void loadJar(@TempDir Path temp) throws Exception {
        Path jarFile = temp.resolve("demo.jar");
        createJar(jarFile, "com/example/Demo");

        List<ClassFile> loaded = loader.load(jarFile);

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getClassName()).isEqualTo("com/example/Demo");
    }

    private void createJar(Path jarFile, String internalName) throws Exception {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile))) {
            JarEntry entry = new JarEntry(internalName + ".class");
            jos.putNextEntry(entry);
            jos.write(minimalClassBytes(internalName));
            jos.closeEntry();
        }
    }

    private byte[] minimalClassBytes(String internalName) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(0xCAFEBABE);
            out.writeShort(0);
            out.writeShort(49); // Java 5
            out.writeShort(5); // constant pool count

            // constant pool
            out.writeByte(1); // UTF8
            out.writeUTF(internalName);
            out.writeByte(7); // Class
            out.writeShort(1);
            out.writeByte(1); // UTF8
            out.writeUTF("java/lang/Object");
            out.writeByte(7); // Class
            out.writeShort(3);

            out.writeShort(0x0021); // access
            out.writeShort(2); // this class
            out.writeShort(4); // super class
            out.writeShort(0); // interfaces
            out.writeShort(0); // fields
            out.writeShort(0); // methods
            out.writeShort(0); // attributes
        }
        return baos.toByteArray();
    }
}
