# ByteScry

ByteScry is a desktop-first Java and Android decompiler workbench. It opens
`.class`, `.jar`, class directories, `.apk`, `.dex`, `.aab`, `.apks`, `.apkm`,
and `.xapk` artifacts, then lets you inspect source, compare engines, view JVM
bytecode, search inside classes, and export recovered sources.

Maven modules, launch scripts, Java packages, and documentation use the
`bytescry` name consistently.

## Features

- JavaFX GUI with native window controls, adaptive startup sizing, drag-to-move,
  maximize/restore, and high-DPI friendly layout.
- Native open dialogs for files and folders, plus drag-and-drop into the
  Project pane.
- Project tree grouped by collapsed package names, with right-click copy for
  package names, Java class names, and internal class names.
- Source view with engine switching, line numbers, and in-class search.
- Compare view with side-by-side output and match counters for search.
- Optional Bytecode and Diagnostics tabs controlled from the View menu.
- Export dialog with output directory selection and engine selection.
- Engines:
  - `cfr` for default Java `.class` / `.jar` source recovery.
  - `vineflower` for an alternate high-quality Java decompiler.
  - `simple` for a lightweight fallback and quick structural output.
  - `jadx` for Android artifacts.
- APK/DEX indexing through JADX, so Android projects show real generated source
  classes instead of a single artifact placeholder.
- Export tolerates partial JADX failures when source files were still produced.

## Supported Inputs

| Input | GUI | CLI | Notes |
| --- | --- | --- | --- |
| `.class` | Yes | Yes | CFR, Vineflower, Simple, Bytecode |
| `.jar` | Yes | Yes | CFR default; Spring Boot launcher classes are deprioritized for initial selection |
| Directory | Yes | Yes | Recursively reads `.class`; also reads direct child `.jar` / Android artifacts in GUI workflows |
| `.apk`, `.dex`, `.aab`, `.apks`, `.apkm`, `.xapk` | Yes | Limited | GUI is the intended Android workflow; JADX is selected automatically |

## Requirements

- Windows GUI release: no system Java required. The Windows release asset is a
  single self-extracting `.exe` with a bundled trimmed Java runtime.
- Linux CLI/GUI releases: Java 17 or newer is required on the target machine.
- JDK 17 or newer and Maven 3.8 or newer for building from source.
- Linux release builds provide shell scripts under `bin/`.

## Quick Start

Download one of the GitHub Release assets:

```text
bytescry.exe                                 # Windows single-file GUI
bytescry-cli-<version>-linux-x64.tar.gz      # Linux command-line package
bytescry-gui-<version>-linux-x64.zip         # Linux GUI package
```

Run the Windows GUI:

```bat
bytescry.exe
```

The Windows single-file exe can be moved and started by itself. On first launch
it extracts its embedded runtime and application files into the user cache.

Run the Linux CLI:

```bash
tar -xzf bytescry-cli-1.0.0-linux-x64.tar.gz
./bytescry-cli-1.0.0/bin/bytescry --help
```

Run the Linux GUI:

```bash
unzip bytescry-gui-1.0.0-linux-x64.zip
./bytescry-gui-1.0.0-linux-x64/bin/bytescry-gui
```

Build release archives from source:

```bash
mvn -Prelease-windows clean package
mvn -Prelease-linux clean package
```

Release assets are intentionally minimal:

- Windows GUI: `bytescry.exe`, a single self-extracting desktop executable.
- Linux CLI: `bytescry-cli-1.0.0-linux-x64.tar.gz`, with `bin/bytescry`.
- Linux GUI: `bytescry-gui-1.0.0-linux-x64.zip`, with `bin/bytescry-gui`.

The release archives do not include Windows `.bat` launchers.

## GUI Workflow

1. Use `Open...` or `File > Open...` to select a `.class`, `.jar`, or Android
   artifact.
2. Use `File > Open Folder...` to select a directory using the system folder
   picker.
3. Drag a supported file or directory into the Project pane as a shortcut.
4. Select a class in Project. Source is decompiled on demand.
5. Use the engine dropdown in Source to switch between available engines.
   Non-Android inputs show `cfr`, `vineflower`, and `simple`; Android inputs
   show `jadx`.
6. Use `Find in class` to search Source, Compare, Bytecode, or Diagnostics.
7. Use `View` to show or hide Compare, Bytecode, and Diagnostics.
8. Use `Export Sources` to choose an output directory and export engine.

## CLI Usage

The CLI is useful for Java `.class`, `.jar`, and class directory workflows:

```bash
bytescry [OPTIONS] <input>

Options:
  -e, --engine=<engine>   Engine name: cfr, vineflower, simple, or jadx
  -o, --output=<dir>      Output directory. If omitted, prints to stdout
  -b, --bytecode          Print bytecode alongside source
  -h, --help              Show help
  -V, --version           Show version
```

Examples:

```bash
# Print one class to stdout
bin/bytescry --engine cfr path/to/Demo.class

# Export a JAR with Vineflower
bin/bytescry --engine vineflower --output out path/to/app.jar

# Print Simple output and JVM bytecode
bin/bytescry --engine simple --bytecode path/to/Demo.class
```

For Android artifacts, prefer the GUI. The GUI uses the whole-artifact JADX
path, builds a class index from generated Java files, and exports the complete
JADX output more reliably than the current CLI wrapper.

## Project Layout

```text
.
├── bytescry-core/    # engine API, loaders, CFR, Vineflower, JADX, Simple, bytecode utilities
├── bytescry-cli/     # picocli command-line entry point
├── bytescry-gui/     # JavaFX desktop application
├── bytescry-tests/   # versioned Java sample tests
├── docs/             # usage, design, and build documentation
└── scripts/          # release helper scripts
```

## Documentation

- [Usage Guide](docs/USAGE.md)
- [Design Notes](docs/DESIGN.md)
- [Build Guide](docs/BUILD.md)

## Known Limitations

- Decompiled source is best-effort. Obfuscation, missing metadata, compiler
  transformations, and invalid bytecode can reduce fidelity.
- Android Bytecode view is intentionally disabled; APK/DEX inputs are handled
  through JADX source output instead of JVM `.class` bytecode rendering.
- JADX can finish with partial errors. ByteScry treats this as usable when Java
  source files were produced.
- Decompiled output may still contain original application package names from
  the inspected artifact.

## License

MIT License. See [LICENSE](LICENSE).

ByteScry depends on third-party open-source components. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency license notes
and release packaging obligations.
