package com.github.bytescry.util;

/**
 * Utilities for converting JVM type descriptors to Java source types.
 */
public final class TypeUtils {

    private TypeUtils() {
        // utility
    }

    /**
     * Convert a JVM type descriptor to a Java source type.
     *
     * @param desc JVM descriptor, e.g. "I", "Ljava/lang/String;", "[[I"
     */
    public static String descriptorToSource(String desc) {
        if (desc == null || desc.isEmpty()) {
            return "void";
        }

        int arrayDimensions = 0;
        while (arrayDimensions < desc.length() && desc.charAt(arrayDimensions) == '[') {
            arrayDimensions++;
        }

        String baseDesc = desc.substring(arrayDimensions);
        String baseType = baseTypeToSource(baseDesc);

        StringBuilder sb = new StringBuilder(baseType);
        for (int i = 0; i < arrayDimensions; i++) {
            sb.append("[]");
        }
        return sb.toString();
    }

    private static String baseTypeToSource(String desc) {
        return switch (desc) {
            case "V" -> "void";
            case "Z" -> "boolean";
            case "B" -> "byte";
            case "C" -> "char";
            case "S" -> "short";
            case "I" -> "int";
            case "J" -> "long";
            case "F" -> "float";
            case "D" -> "double";
            default -> {
                if (desc.startsWith("L") && desc.endsWith(";")) {
                    yield desc.substring(1, desc.length() - 1).replace('/', '.');
                }
                yield desc.replace('/', '.');
            }
        };
    }

    /**
     * Convert an internal class name (slashes) to a fully qualified Java name (dots).
     */
    public static String internalToQualified(String internalName) {
        return internalName == null ? null : internalName.replace('/', '.');
    }

    /**
     * Convert a fully qualified Java name to an internal class name.
     */
    public static String qualifiedToInternal(String qualifiedName) {
        return qualifiedName == null ? null : qualifiedName.replace('.', '/');
    }
}
