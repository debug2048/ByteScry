# ByteScry Build Guide

This repository builds the `bytescry-*` artifacts for the ByteScry application.

## Prerequisites

- JDK 17+
- Apache Maven 3.8+
- Internet access for Maven dependencies
- Windows release builds require a JDK with `jlink` / `jmods` and Visual Studio
  Build Tools with the MSVC C++ toolchain. They produce a Windows runtime image
  and should be run on Windows.

Check the active JDK:

```bash
java -version
mvn -version
```

Both should report JDK 17 or newer.

## Build Everything

```bash
mvn clean package
```

This compiles all modules, runs tests, and creates portable distributable
archives for the default platform settings.

## Build Individual Modules

Core only:

```bash
mvn -pl bytescry-core test
```

GUI with dependencies:

```bash
mvn -pl bytescry-gui -am package
```

CLI with dependencies:

```bash
mvn -pl bytescry-cli -am package
```

## Test

```bash
mvn test
```

The tests cover loaders, Simple engine behavior, type utilities, and versioned
Java sample decompilation.

## Release Packaging

Use release profiles for GitHub Release assets.

Windows release:

```bash
mvn -Prelease-windows clean package
```

Add a traceable hidden build watermark to the Windows single-file exe and GUI
metadata by passing `watermark.id`:

```bash
mvn -Prelease-windows -Dwatermark.id=github-v1.0.0-<commit> clean package
```

GitHub tag builds set this value automatically from the tag, commit SHA, and
workflow run id.

Primary Windows asset:

```text
bytescry-gui/target/bytescry.exe
```

The Windows GUI exe is a native self-extracting executable. It embeds the
portable GUI zip and extracts it to the user cache on first launch, so users can
move and run the exe by itself. Users do not need to install Java or the .NET
Framework.

The build also creates an internal portable zip:

```text
bytescry-gui/target/bytescry-gui-1.0.0-windows-x64.zip
```

The portable zip contains:

```text
bytescry-gui-1.0.0-windows-x64/
├── bin/
│   └── bytescry.exe
├── runtime/
├── javafx/
└── lib/
```

Run:

```bat
bytescry.exe
```

Users do not need to install Java separately for the Windows GUI package.

Linux release:

```bash
mvn -Prelease-linux clean package
```

Primary Linux CLI asset:

```text
bytescry-cli/target/bytescry-cli-1.0.0-linux-x64.tar.gz
```

Additional Linux assets:

```text
bytescry-cli/target/bytescry-cli-1.0.0-linux-x64.zip
bytescry-gui/target/bytescry-gui-1.0.0-linux-x64.zip
```

The Linux CLI archives contain `bin/bytescry` only. They do not include a
Windows `.bat` launcher.

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

Recommended GitHub Release uploads:

```text
bytescry.exe
bytescry-cli-1.0.0-linux-x64.tar.gz
bytescry-gui-1.0.0-linux-x64.zip
LICENSE
THIRD_PARTY_NOTICES.md
SHA256SUMS-*.txt
```

Tag builds (`v*`) are also covered by GitHub Actions. The workflow runs tests,
builds Windows and Linux release artifacts, generates SHA-256 checksum files,
and publishes the assets to the GitHub Release.

## Generic GUI Packaging

The parent POM sets:

```xml
<javafx.classifier>win</javafx.classifier>
```

So the default GUI package is Windows-oriented unless a release profile or
`-Djavafx.classifier=...` overrides it.

Windows:

```bash
mvn -pl bytescry-gui -am clean package -Djavafx.classifier=win
```

Linux:

```bash
mvn -pl bytescry-gui -am clean package -Djavafx.classifier=linux
```

macOS:

```bash
mvn -pl bytescry-gui -am clean package -Djavafx.classifier=mac
```

The generic GUI archive is created at:

```text
bytescry-gui/target/bytescry-gui-1.0.0-portable.zip
```

Unpacked directory:

```text
bytescry-gui/target/bytescry-gui-1.0.0-portable/
```

Run it:

```bash
./bytescry-gui-1.0.0-portable/bin/bytescry-gui
```

## CLI Packaging

Build:

```bash
mvn -pl bytescry-cli -am clean package
```

Artifacts:

```text
bytescry-cli/target/bytescry-cli-1.0.0-portable.zip
bytescry-cli/target/bytescry-cli-1.0.0-portable.tar.gz
bytescry-cli/target/bytescry-cli-1.0.0-portable/
```

Run:

```bash
cd bytescry-cli/target
unzip bytescry-cli-1.0.0-portable.zip
./bytescry-cli-1.0.0/bin/bytescry --help
```

Windows:

The CLI distribution is intended for terminal use on Linux and other Unix-like
systems. Windows desktop releases use the self-contained GUI `.exe` package.

## Dependency Notes

Important versions are defined in the parent `pom.xml`:

- ASM 9.7
- CFR 0.152
- Vineflower 1.12.0
- JADX 1.5.5
- JavaFX 17.0.2
- picocli 4.7.6
- JUnit 5.10.2
- Launch4j Maven Plugin 2.5.3 for the internal Windows GUI launcher
- MSVC Build Tools for the outer native single-file Windows launcher

Dependencies are resolved from Maven repositories. To upgrade CFR or another
engine dependency, update the version property in the parent POM and rebuild.

## Clean Generated Files

```bash
mvn clean
```

This removes Maven `target/` directories.

## Release Checklist

Before uploading to GitHub or attaching a release artifact:

1. Run `mvn -Prelease-windows clean package` on Windows.
2. Run `mvn -Prelease-linux clean package`.
3. Confirm the Windows single-file exe exists:
   `bytescry-gui/target/bytescry.exe`.
4. Confirm the internal Windows zip contains `bin/bytescry.exe`.
5. Confirm the internal Windows zip contains `runtime/bin/javaw.exe`.
6. Confirm the Linux CLI tarball contains `bin/bytescry`.
7. Confirm the Windows release does not publish a CLI `.bat` package.
8. Confirm the Windows GUI zip does not contain `bytescry-gui.bat` or a Unix
   shell launcher.
9. Confirm the Linux CLI tarball does not contain `bytescry.bat`.
10. Move the Windows single-file exe to another directory and start it.
11. Open a `.jar` in the Windows GUI.
12. Open an Android artifact and confirm Project indexes JADX classes.
13. Test Source search and Compare search.
14. Export Java sources.
15. Export Android sources.
16. Confirm README and docs mention the current artifact names.
17. Confirm `LICENSE` and `THIRD_PARTY_NOTICES.md` are present in archive
    packages.
18. Attach `THIRD_PARTY_NOTICES.md` alongside the Windows single-file exe on
    GitHub Releases.

The Launch4j-generated Windows `.exe` is unsigned. For public releases, code
signing is recommended to reduce antivirus false positives.

## Known Build Warnings

Maven may print warnings such as:

```text
Failed to build parent project for org.openjfx:javafx-controls:jar:17.0.2
```

These come from dependency metadata resolution during assembly packaging. If the
reactor finishes with `BUILD SUCCESS`, the package is usable.
