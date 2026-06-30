package com.github.bytescry.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeUtilsTest {

    @Test
    void primitiveDescriptors() {
        assertThat(TypeUtils.descriptorToSource("V")).isEqualTo("void");
        assertThat(TypeUtils.descriptorToSource("Z")).isEqualTo("boolean");
        assertThat(TypeUtils.descriptorToSource("B")).isEqualTo("byte");
        assertThat(TypeUtils.descriptorToSource("C")).isEqualTo("char");
        assertThat(TypeUtils.descriptorToSource("S")).isEqualTo("short");
        assertThat(TypeUtils.descriptorToSource("I")).isEqualTo("int");
        assertThat(TypeUtils.descriptorToSource("J")).isEqualTo("long");
        assertThat(TypeUtils.descriptorToSource("F")).isEqualTo("float");
        assertThat(TypeUtils.descriptorToSource("D")).isEqualTo("double");
    }

    @Test
    void referenceDescriptor() {
        assertThat(TypeUtils.descriptorToSource("Ljava/lang/String;")).isEqualTo("java.lang.String");
    }

    @Test
    void arrayDescriptor() {
        assertThat(TypeUtils.descriptorToSource("[[I")).isEqualTo("int[][]");
        assertThat(TypeUtils.descriptorToSource("[Ljava/lang/Object;")).isEqualTo("java.lang.Object[]");
    }

    @Test
    void internalToQualified() {
        assertThat(TypeUtils.internalToQualified("com/example/Demo")).isEqualTo("com.example.Demo");
    }
}
