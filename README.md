# ByteScry

[中文](README.zh-CN.md)

ByteScry is a desktop-first Java and Android decompiler workbench. It opens
`.class`, `.jar`, class directories, `.apk`, `.dex`, `.aab`, `.apks`, `.apkm`,
and `.xapk` artifacts for source inspection, engine comparison, bytecode view,
class search, and source export.

## Highlights

- JavaFX desktop GUI with native window controls and high-DPI friendly layout.
- Pluggable engines: CFR, Vineflower, Simple, and JADX.
- Collapsed package tree, drag-and-drop open, in-class search, and line numbers.
- Side-by-side engine compare, optional Bytecode and Diagnostics views.
- Source export with engine selection.
- Offline analysis reports for permissions, endpoints, risky APIs, and strings.
- Android artifact indexing through JADX for APK/DEX-oriented workflows.

## Supported Inputs

| Input | GUI | CLI | Notes |
| --- | --- | --- | --- |
| `.class` | Yes | Yes | CFR, Vineflower, Simple, Bytecode |
| `.jar` | Yes | Yes | CFR default |
| Directory | Yes | Yes | Recursively reads `.class`; GUI also reads direct child `.jar` / Android artifacts |
| `.apk`, `.dex`, `.aab`, `.apks`, `.apkm`, `.xapk` | Yes | Yes | GUI uses JADX by default; CLI uses `--engine jadx` |

## Download

Recommended GitHub Release assets:

```text
bytescry.exe                            # Windows single-file GUI, no Java/.NET install required
bytescry-cli-<version>-linux-x64.tar.gz # Linux CLI
bytescry-gui-<version>-linux-x64.zip    # Linux GUI
```

The Windows `bytescry.exe` is a portable self-extracting executable. It can be
moved and launched by itself; on first start it extracts its embedded runtime
and libraries into the user cache.

## Build

Requirements:

- JDK 17+
- Apache Maven 3.8+

Build everything:

```bash
mvn clean package
```

Build release artifacts:

```bash
mvn -Prelease-windows clean package
mvn -Prelease-linux clean package
```

Windows release output:

```text
bytescry-gui/target/bytescry.exe
```

Linux release outputs:

```text
bytescry-cli/target/bytescry-cli-1.0.0-linux-x64.tar.gz
bytescry-gui/target/bytescry-gui-1.0.0-linux-x64.zip
```

## Documentation

- [Usage Guide](docs/USAGE.md)
- [Design Notes](docs/DESIGN.md)
- [Build Guide](docs/BUILD.md)

## Limitations

Decompiled source is best-effort. Obfuscation, missing metadata, compiler
transformations, invalid bytecode, and partial JADX failures can reduce output
fidelity. Users are responsible for ensuring they have the right to inspect or
decompile artifacts opened with ByteScry.

## License

ByteScry is licensed under the [MIT License](LICENSE). Third-party dependency
notices are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
