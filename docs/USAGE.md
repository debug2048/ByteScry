# ByteScry Usage Guide

This guide describes the current GUI and CLI behavior. Maven modules, launch
scripts, Java packages, and documentation use the `bytescry` name consistently.

## Requirements

- Windows GUI release: no system Java or .NET Framework required. The release
  asset is a native self-extracting `.exe` with an embedded runtime.
- Linux CLI/GUI releases: Java 17+ is required.
- Maven is needed only when building from source.

## Start the GUI

Windows release package:

```bat
bytescry.exe
```

Linux GUI package:

```bash
unzip bytescry-gui-1.0.0-linux-x64.zip
./bytescry-gui-1.0.0-linux-x64/bin/bytescry-gui
```

The Windows `.exe` can be moved and started by itself. On first launch it
extracts the embedded runtime and application files into the user cache. Linux
packages use shell scripts and are intended to be started from a terminal.

## Open Files and Folders

Use `Open...` or `File > Open...` for files:

- `.class`
- `.jar`
- `.apk`
- `.dex`
- `.aab`
- `.apks`
- `.apkm`
- `.xapk`

Use `File > Open Folder...` for directories.

You can also drag a supported file or directory into the Project pane.

### Directory Loading Rules

When a directory is selected:

- Recursive `.class` files are loaded.
- Direct child `.jar` and Android artifact files are also considered by the
  loader.
- If the directory contains no `.class` files and exactly one supported direct
  child artifact, ByteScry opens that artifact directly.
- "Direct child" means files immediately inside the selected directory, not
  nested files in subdirectories.

## Project Pane

The Project pane shows packages and classes.

- Package chains are collapsed into names such as `com.example.service`.
- Classes are sorted inside each package.
- During loading, the previous project tree is cleared and the pane is disabled
  so stale classes cannot be selected.
- A spinner appears in the Project header while loading.
- Long workspace paths are truncated in the header; hover the path to see the
  full path.
- Right-click a package to copy the package name.
- Right-click a class to copy the Java class name or internal JVM name.

For Spring Boot JARs, loader classes under `org.springframework.boot.loader`
may appear because they are physically inside executable Spring Boot archives.
ByteScry avoids selecting those launcher classes first when a better
application entry class can be found.

## Source View

The Source tab shows the best source for the selected class.

Engine dropdown behavior:

- Before loading, available engines can include `cfr`, `vineflower`, `simple`,
  and `jadx`.
- For Java `.class`, `.jar`, and directories, the dropdown shows
  `cfr`, `vineflower`, and `simple`.
- For Android artifacts, the dropdown shows only `jadx`.
- The displayed engine follows the engine actually used for the current input.

Line numbers are shown in a nonselectable gutter, so copying source text does
not include line numbers.

Use `Find in class` and the previous/next buttons to search within the active
tab.

## Compare View

Compare is a two-column view:

- Java inputs compare CFR with Vineflower when available.
- If Vineflower is unavailable, Simple is used as the comparison side.
- Android inputs show JADX on the left and a message on the right because JVM
  bytecode comparison is not available for APK/DEX artifacts.

Search works across both columns. The status bar reports matches like:

```text
Compare left match 2/9
Compare right match 5/9
```

## Bytecode View

Bytecode uses ASM to render JVM `.class` bytecode.

This view is available for Java class files and JAR entries. It is disabled for
Android artifacts because APK/DEX inputs are not JVM `.class` files.

## Diagnostics View

Diagnostics collects engine failures and fallback messages for the current
session. Enable it from `View > Diagnostics`.

Examples of useful diagnostics:

- CFR failed and Vineflower was used.
- Vineflower failed and Simple fallback was written.
- A class could not be rendered as JVM bytecode.

## Export Sources

Click `Export Sources` to open the export dialog.

The dialog lets you choose:

- Output directory
- Export engine

Behavior:

- Java inputs can export with `cfr`, `vineflower`, `simple`, or the current
  selected engine.
- Non-Simple exports can fall back to Simple for individual failed classes.
- Android artifacts export through JADX.
- JADX can report partial errors. If Java files were produced, ByteScry keeps
  the usable output instead of failing the whole export.

Output paths preserve package/class names:

```text
out/
└── com/
    └── example/
        ├── App.java
        └── service/
            └── Worker.java
```

## Analysis Reports

Click `Report` to save an offline Markdown analysis report for the loaded
artifact. The report is generated locally and does not call an AI model or
external service.

The deterministic scanner currently reports:

- Package, class, method, and field counts
- Android permission strings
- URLs and domains
- Network, crypto, reflection, process execution, dynamic loading, native load,
  and selected Android sensitive API references
- Suspicious environment and instrumentation strings

## CLI

The CLI is best for Java `.class`, `.jar`, and class directory workflows.

Linux release package:

```bash
tar -xzf bytescry-cli-1.0.0-linux-x64.tar.gz
./bytescry-cli-1.0.0/bin/bytescry --help
```

The release CLI package contains the Unix-style `bin/bytescry` launcher only.
Windows desktop releases use the self-contained GUI `.exe` package.

```text
Usage: bytescry [OPTIONS] <input>

Parameters:
      <input>               Input .class file, .jar file, or directory

Options:
  -e, --engine=<engine>     cfr, vineflower, simple, or jadx
  -o, --output=<dir>        Output directory. If omitted, prints to stdout
  -b, --bytecode            Print bytecode alongside decompiled source
      --report=<file>       Write an offline Markdown analysis report
      --report-only         Generate only the report, without decompiling
  -h, --help                Show help
  -V, --version             Show version
```

Examples:

```bash
# Decompile one class to stdout
bin/bytescry --engine cfr path/to/Hello.class

# Export a JAR
bin/bytescry --engine vineflower --output out path/to/app.jar

# Inspect Simple output and bytecode
bin/bytescry --engine simple --bytecode path/to/Hello.class

# Generate an offline analysis report
bin/bytescry --report report.md --report-only path/to/app.jar
```

For Android artifacts, the GUI is still the recommended workflow because it
indexes the whole JADX output for browsing. The CLI can export Android sources
when `--engine jadx` and `--output` are used:

```bash
bin/bytescry --engine jadx --output out path/to/app.apk
```

## Troubleshooting

### The first class in a Spring Boot JAR is a loader class

This is normal for executable Spring Boot JARs. The archive includes boot
launcher classes. ByteScry tries to select `Start-Class`, `Application`, or
`Main` classes first, but loader classes remain visible because they are part of
the JAR.

### JADX says it finished with errors

JADX can fail on a few classes while still producing hundreds or thousands of
usable Java files. ByteScry treats the result as usable if generated `.java`
files exist.

### `libpng warning: iCCP: known incorrect sRGB profile`

This warning comes from image metadata in a dependency or platform resource. It
does not affect decompilation.

### Source is not identical to the original code

No decompiler can guarantee exact original source. Names, formatting, comments,
synthetic constructs, bridge methods, lambdas, and compiler rewrites may differ.
Use Compare and Diagnostics to evaluate confidence.
