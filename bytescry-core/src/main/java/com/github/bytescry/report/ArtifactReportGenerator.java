package com.github.bytescry.report;

import com.github.bytescry.loader.ClassFileLoader;
import com.github.bytescry.model.ClassFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Deterministic artifact scanner for offline reports.
 */
public class ArtifactReportGenerator {

    private static final int MAX_SCAN_BYTES_PER_FILE = 8 * 1024 * 1024;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>\\\\)\\]}]+");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "\\b(?:[a-zA-Z0-9-]+\\.)+(?:com|net|org|io|cn|co|dev|app|xyz|top|info|biz|ru|uk|de|jp|kr)\\b");
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("android\\.permission\\.[A-Z0-9_]+");
    private static final Set<String> SUSPICIOUS_STRING_NEEDLES = Set.of(
            "/system/bin/su",
            "/system/xbin/su",
            "magisk",
            "frida",
            "xposed",
            "substrate",
            "ro.debuggable",
            "ro.secure",
            "getprop",
            "ptrace",
            "TracerPid"
    );

    public AnalysisReport generate(Path input) throws IOException {
        List<ClassFile> classes = loadClassesBestEffort(input);
        return generate(input, classes);
    }

    public AnalysisReport generate(Path input, List<ClassFile> classes) throws IOException {
        AnalysisReport report = new AnalysisReport(input, artifactType(input), Instant.now());
        List<ClassFile> classSnapshot = classes == null ? List.of() : List.copyOf(classes);
        report.setClassCount(classSnapshot.size());

        for (ClassFile classFile : classSnapshot) {
            analyzeClass(report, classFile);
        }
        scanArtifactBytes(report, input);
        addSummaryFindings(report);
        return report;
    }

    private List<ClassFile> loadClassesBestEffort(Path input) {
        try {
            return new ClassFileLoader().load(input);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String artifactType(Path input) {
        String fileName = input.getFileName() == null ? "" : input.getFileName().toString().toLowerCase(Locale.ROOT);
        if (Files.isDirectory(input)) {
            return "directory";
        }
        if (fileName.endsWith(".class")) {
            return "class";
        }
        if (fileName.endsWith(".jar")) {
            return "jar";
        }
        if (fileName.endsWith(".apk")) {
            return "apk";
        }
        if (fileName.endsWith(".dex")) {
            return "dex";
        }
        if (fileName.endsWith(".aab")) {
            return "android-app-bundle";
        }
        if (fileName.endsWith(".apks") || fileName.endsWith(".apkm") || fileName.endsWith(".xapk")) {
            return "android-package-set";
        }
        return "artifact";
    }

    private void analyzeClass(AnalysisReport report, ClassFile classFile) {
        try {
            ClassReader reader = new ClassReader(classFile.getBytes());
            reader.accept(new ReportClassVisitor(report, classFile), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (RuntimeException ignored) {
            addFinding(report, Severity.LOW, "Bytecode", "Could not parse class metadata",
                    classFile.getClassName(), classFile.getSource());
        }
    }

    private void scanArtifactBytes(AnalysisReport report, Path input) throws IOException {
        if (!Files.exists(input)) {
            return;
        }
        if (Files.isDirectory(input)) {
            try (var stream = Files.walk(input)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    scanFile(report, file);
                }
            }
            return;
        }
        scanFile(report, input);
    }

    private void scanFile(AnalysisReport report, Path file) throws IOException {
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (isZipLike(lowerName)) {
            scanZip(report, file);
            return;
        }
        scanBytes(report, file.toString(), readScanBytes(file));
    }

    private boolean isZipLike(String lowerName) {
        return lowerName.endsWith(".jar")
                || lowerName.endsWith(".apk")
                || lowerName.endsWith(".aab")
                || lowerName.endsWith(".apks")
                || lowerName.endsWith(".apkm")
                || lowerName.endsWith(".xapk")
                || lowerName.endsWith(".zip");
    }

    private byte[] readScanBytes(Path file) throws IOException {
        long size = Files.size(file);
        if (size <= MAX_SCAN_BYTES_PER_FILE) {
            return Files.readAllBytes(file);
        }
        try (InputStream input = Files.newInputStream(file)) {
            return input.readNBytes(MAX_SCAN_BYTES_PER_FILE);
        }
    }

    private void scanZip(AnalysisReport report, Path file) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] bytes = zip.readNBytes(MAX_SCAN_BYTES_PER_FILE);
                scanBytes(report, file + "!" + entry.getName(), bytes);
                if (entry.getName().endsWith(".dex")) {
                    addFinding(report, Severity.INFO, "Android", "DEX bytecode present",
                            entry.getName(), "Android code payload");
                }
                if (entry.getName().endsWith(".so")) {
                    addFinding(report, Severity.MEDIUM, "Native", "Native library present",
                            entry.getName(), "JNI/native code may require separate review");
                }
            }
        }
    }

    private void scanBytes(AnalysisReport report, String location, byte[] bytes) {
        if (bytes.length == 0) {
            return;
        }
        List<String> strings = new ArrayList<>();
        strings.addAll(extractAsciiStrings(bytes));
        strings.addAll(extractUtf16LeStrings(bytes));
        for (String value : strings) {
            scanText(report, location, value);
        }
    }

    private List<String> extractAsciiStrings(byte[] bytes) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (byte b : bytes) {
            int value = b & 0xff;
            if (value >= 32 && value <= 126) {
                current.append((char) value);
            } else {
                flushString(result, current);
            }
        }
        flushString(result, current);
        return result;
    }

    private List<String> extractUtf16LeStrings(byte[] bytes) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i + 1 < bytes.length; i += 2) {
            int low = bytes[i] & 0xff;
            int high = bytes[i + 1] & 0xff;
            if (high == 0 && low >= 32 && low <= 126) {
                current.append((char) low);
            } else {
                flushString(result, current);
            }
        }
        flushString(result, current);
        return result;
    }

    private void flushString(List<String> result, StringBuilder current) {
        if (current.length() >= 4) {
            result.add(current.toString());
        }
        current.setLength(0);
    }

    private void scanText(AnalysisReport report, String location, String text) {
        Matcher urlMatcher = URL_PATTERN.matcher(text);
        while (urlMatcher.find()) {
            String url = trimToken(urlMatcher.group());
            report.addUrl(url);
            report.addDomain(domainFromUrl(url));
        }

        Matcher domainMatcher = DOMAIN_PATTERN.matcher(text);
        while (domainMatcher.find()) {
            report.addDomain(domainMatcher.group().toLowerCase(Locale.ROOT));
        }

        Matcher permissionMatcher = PERMISSION_PATTERN.matcher(text);
        while (permissionMatcher.find()) {
            String permission = permissionMatcher.group();
            report.addPermission(permission);
            addPermissionFinding(report, permission, location);
        }

        String lower = text.toLowerCase(Locale.ROOT);
        for (String needle : SUSPICIOUS_STRING_NEEDLES) {
            if (lower.contains(needle.toLowerCase(Locale.ROOT))) {
                report.addSuspiciousString(needle);
                addFinding(report, Severity.MEDIUM, "Evasion", "Suspicious environment string",
                        location, needle);
            }
        }
    }

    private String trimToken(String value) {
        return value.replaceAll("[,.;:]+$", "");
    }

    private String domainFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return "";
        }
    }

    private void addPermissionFinding(AnalysisReport report, String permission, String location) {
        Severity severity = switch (permission) {
            case "android.permission.READ_CONTACTS",
                 "android.permission.WRITE_CONTACTS",
                 "android.permission.READ_SMS",
                 "android.permission.SEND_SMS",
                 "android.permission.RECEIVE_SMS",
                 "android.permission.RECORD_AUDIO",
                 "android.permission.CAMERA" -> Severity.HIGH;
            case "android.permission.ACCESS_FINE_LOCATION",
                 "android.permission.ACCESS_COARSE_LOCATION",
                 "android.permission.READ_PHONE_STATE",
                 "android.permission.GET_ACCOUNTS",
                 "android.permission.QUERY_ALL_PACKAGES" -> Severity.MEDIUM;
            default -> Severity.INFO;
        };
        addFinding(report, severity, "Android Permission", "Android permission declared or referenced",
                location, permission);
    }

    private void addSummaryFindings(AnalysisReport report) {
        if (!report.urls().isEmpty()) {
            addFinding(report, Severity.INFO, "Network", "Network endpoints found",
                    "", report.urls().size() + " URL(s)");
        }
        if (!report.permissions().isEmpty()) {
            addFinding(report, Severity.INFO, "Android", "Android permissions found",
                    "", report.permissions().size() + " permission(s)");
        }
    }

    private void addFinding(AnalysisReport report, Severity severity, String category, String title,
                            String location, String evidence) {
        report.addFinding(new ReportFinding(severity, category, title, location, evidence));
    }

    private final class ReportClassVisitor extends ClassVisitor {
        private final AnalysisReport report;
        private final ClassFile classFile;
        private String className;

        private ReportClassVisitor(AnalysisReport report, ClassFile classFile) {
            super(Opcodes.ASM9);
            this.report = report;
            this.classFile = classFile;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            className = name == null ? classFile.getClassName() : name;
            int slash = className.lastIndexOf('/');
            if (slash > 0) {
                report.addPackage(className.substring(0, slash).replace('/', '.'));
            }
            if (superName != null) {
                scanOwner(report, className, superName, "<extends>");
            }
            if (interfaces != null) {
                for (String iface : interfaces) {
                    scanOwner(report, className, iface, "<implements>");
                }
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            report.incrementFieldCount();
            if (value instanceof String text) {
                scanText(report, className + "." + name, text);
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            report.incrementMethodCount();
            String methodLocation = className + "." + name + descriptor;
            return new ReportMethodVisitor(report, methodLocation);
        }
    }

    private final class ReportMethodVisitor extends MethodVisitor {
        private final AnalysisReport report;
        private final String methodLocation;

        private ReportMethodVisitor(AnalysisReport report, String methodLocation) {
            super(Opcodes.ASM9);
            this.report = report;
            this.methodLocation = methodLocation;
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof String text) {
                scanText(report, methodLocation, text);
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            scanOwner(report, methodLocation, type, opcodeName(opcode));
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            scanOwner(report, methodLocation, owner, name);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            scanOwner(report, methodLocation, owner, name + descriptor);
            scanMethodCall(report, methodLocation, owner, name, descriptor);
        }
    }

    private void scanOwner(AnalysisReport report, String location, String owner, String evidence) {
        if (owner == null) {
            return;
        }
        if (owner.startsWith("java/net/")
                || owner.startsWith("okhttp3/")
                || owner.startsWith("retrofit2/")
                || owner.startsWith("org/apache/http/")) {
            addFinding(report, Severity.MEDIUM, "Network", "Network API reference", location, owner + "." + evidence);
        } else if (owner.startsWith("java/lang/reflect/")
                || owner.equals("java/lang/reflect/Method")
                || owner.equals("java/lang/reflect/Field")) {
            addFinding(report, Severity.LOW, "Reflection", "Reflection API reference", location, owner + "." + evidence);
        } else if (owner.startsWith("javax/crypto/")
                || owner.startsWith("java/security/")) {
            addFinding(report, Severity.LOW, "Crypto", "Cryptography API reference", location, owner + "." + evidence);
        } else if (owner.startsWith("dalvik/system/")) {
            addFinding(report, Severity.HIGH, "Dynamic Loading", "Dynamic class loading reference",
                    location, owner + "." + evidence);
        } else if (owner.startsWith("android/telephony/")) {
            addFinding(report, Severity.HIGH, "Android API", "Telephony API reference", location, owner + "." + evidence);
        } else if (owner.startsWith("android/location/")) {
            addFinding(report, Severity.MEDIUM, "Android API", "Location API reference", location, owner + "." + evidence);
        } else if (owner.startsWith("android/provider/ContactsContract")) {
            addFinding(report, Severity.HIGH, "Android API", "Contacts API reference", location, owner + "." + evidence);
        } else if (owner.startsWith("android/accounts/")) {
            addFinding(report, Severity.HIGH, "Android API", "Account API reference", location, owner + "." + evidence);
        }
    }

    private void scanMethodCall(AnalysisReport report, String location, String owner, String name, String descriptor) {
        if ("java/lang/Runtime".equals(owner) && "exec".equals(name)) {
            addFinding(report, Severity.HIGH, "Process", "Runtime command execution", location, owner + "." + name + descriptor);
        } else if ("java/lang/ProcessBuilder".equals(owner) && ("<init>".equals(name) || "start".equals(name))) {
            addFinding(report, Severity.HIGH, "Process", "ProcessBuilder usage", location, owner + "." + name + descriptor);
        } else if ("java/lang/Class".equals(owner) && "forName".equals(name)) {
            addFinding(report, Severity.LOW, "Reflection", "Class.forName usage", location, owner + "." + name + descriptor);
        } else if ("java/lang/System".equals(owner) && ("load".equals(name) || "loadLibrary".equals(name))) {
            addFinding(report, Severity.MEDIUM, "Native", "Native library loading", location, owner + "." + name + descriptor);
        } else if ("android/os/Debug".equals(owner) && "isDebuggerConnected".equals(name)) {
            addFinding(report, Severity.MEDIUM, "Evasion", "Debugger detection API", location, owner + "." + name + descriptor);
        } else if ("javax/crypto/Cipher".equals(owner) && "getInstance".equals(name)) {
            addFinding(report, Severity.MEDIUM, "Crypto", "Cipher algorithm selection", location, owner + "." + name + descriptor);
        }
    }

    private String opcodeName(int opcode) {
        return switch (opcode) {
            case Opcodes.NEW -> "new";
            case Opcodes.ANEWARRAY -> "new-array";
            case Opcodes.CHECKCAST -> "checkcast";
            case Opcodes.INSTANCEOF -> "instanceof";
            default -> "type-op";
        };
    }
}
