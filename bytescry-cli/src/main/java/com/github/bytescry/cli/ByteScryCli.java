package com.github.bytescry.cli;

import com.github.bytescry.api.ArtifactDecompilerEngine;
import com.github.bytescry.api.DecompilerEngine;
import com.github.bytescry.api.DecompilerEngineRegistry;
import com.github.bytescry.bytecode.BytecodePrinter;
import com.github.bytescry.loader.ClassFileLoader;
import com.github.bytescry.model.ClassFile;
import com.github.bytescry.model.DecompilationResult;
import com.github.bytescry.model.DecompilerOptions;
import com.github.bytescry.report.AnalysisReport;
import com.github.bytescry.report.ArtifactReportGenerator;
import com.github.bytescry.report.MarkdownReportRenderer;
import com.github.bytescry.util.ClassNameUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command-line entry point for ByteScry.
 */
@CommandLine.Command(
        name = "bytescry",
        mixinStandardHelpOptions = true,
        version = "bytescry 1.0.0",
        description = "Decompile Java .class/.jar files with CFR, Vineflower, Simple, or JADX."
)
public class ByteScryCli implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Input .class file, .jar file, or directory.")
    private Path input;

    @CommandLine.Option(names = {"-e", "--engine"}, description = "Decompiler engine: cfr (default), vineflower, simple, or jadx.", defaultValue = "cfr")
    private String engine;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output directory. If omitted, prints to stdout.")
    private Path output;

    @CommandLine.Option(names = {"-b", "--bytecode"}, description = "Print bytecode alongside decompiled source.")
    private boolean printBytecode;

    @CommandLine.Option(names = {"--report"}, description = "Write an offline Markdown analysis report to this file.")
    private Path reportOutput;

    @CommandLine.Option(names = {"--report-only"}, description = "Generate the analysis report without decompiling sources.")
    private boolean reportOnly;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ByteScryCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            if (reportOnly && reportOutput == null) {
                System.err.println("Error: --report-only requires --report <file>");
                return 1;
            }
            DecompilerEngine decompiler = DecompilerEngineRegistry.get(engine);
            DecompilerOptions options = new DecompilerOptions(null, printBytecode,
                    output != null ? output.toString() : null)
                    .withInputPath(input.toString())
                    .withBestEffort(true)
                    .withFallbackEnabled(true);

            if (reportOutput != null && reportOnly) {
                writeReport(new ArtifactReportGenerator().generate(input), reportOutput);
                return 0;
            }

            if (decompiler instanceof ArtifactDecompilerEngine artifactDecompiler
                    && artifactDecompiler.supportsArtifact(input)) {
                if (reportOutput != null) {
                    writeReport(new ArtifactReportGenerator().generate(input), reportOutput);
                }
                if (output != null) {
                    int written = artifactDecompiler.exportArtifact(input, output, options);
                    System.out.println("Exported " + written + " source file(s) to " + output.toAbsolutePath());
                    return 0;
                }
                return printArtifactToStdout(artifactDecompiler, options);
            }

            ClassFileLoader loader = new ClassFileLoader();
            List<ClassFile> classFiles = loader.load(input);

            if (classFiles.isEmpty()) {
                System.err.println("No .class files found in " + input);
                return 1;
            }
            if (reportOutput != null) {
                writeReport(new ArtifactReportGenerator().generate(input, classFiles), reportOutput);
            }

            List<DecompilationResult> results = decompiler.decompile(classFiles, options);

            boolean anyError = false;
            for (DecompilationResult result : results) {
                if (result.hasError()) {
                    anyError = true;
                    System.err.println("Failed to decompile " + result.getClassName() + ": " + result.getError().getMessage());
                    continue;
                }

                if (output != null) {
                    writeResult(result, output);
                } else {
                    System.out.println("// === " + result.getClassName() + " (engine=" + result.getEngine() + ", " + result.getElapsedMillis() + "ms) ===");
                    System.out.println(result.getSourceCode());
                    if (printBytecode) {
                        ClassFile cf = findClassFile(classFiles, result.getClassName());
                        if (cf != null) {
                            System.out.println("// === bytecode ===");
                            System.out.println(BytecodePrinter.print(cf.getBytes()));
                        }
                    }
                }
            }

            if (output != null) {
                System.out.println("Decompiled " + results.size() + " class(es) to " + output.toAbsolutePath());
            }

            return anyError ? 2 : 0;
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private void writeResult(DecompilationResult result, Path outputDir) throws IOException {
        Path file = outputDir.resolve(ClassNameUtils.internalNameToJavaPath(result.getClassName()));
        Files.createDirectories(file.getParent());
        Files.writeString(file, result.getSourceCode(), StandardCharsets.UTF_8);
    }

    private void writeReport(AnalysisReport report, Path outputFile) throws IOException {
        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputFile, new MarkdownReportRenderer().render(report), StandardCharsets.UTF_8);
        System.out.println("Wrote analysis report to " + outputFile.toAbsolutePath());
    }

    private Integer printArtifactToStdout(ArtifactDecompilerEngine decompiler, DecompilerOptions options)
            throws IOException, InterruptedException {
        List<ClassFile> classFiles = decompiler.loadArtifactClasses(input, options);
        if (classFiles.isEmpty()) {
            System.err.println("No source files produced by " + engine + " for " + input);
            return 1;
        }
        for (ClassFile classFile : classFiles) {
            DecompilationResult result = decompiler.decompile(classFile, options);
            if (result.hasError()) {
                System.err.println("Failed to decompile " + result.getClassName() + ": " + result.getError().getMessage());
                continue;
            }
            System.out.println("// === " + result.getClassName() + " (engine=" + result.getEngine()
                    + ", " + result.getElapsedMillis() + "ms) ===");
            System.out.println(result.getSourceCode());
        }
        return 0;
    }

    private ClassFile findClassFile(List<ClassFile> classFiles, String className) {
        return classFiles.stream()
                .filter(cf -> cf.getClassName().equals(className))
                .findFirst()
                .orElse(null);
    }
}
