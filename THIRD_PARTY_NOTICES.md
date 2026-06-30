# Third-Party Notices

This file summarizes third-party components used by ByteScry. It is not a
complete legal opinion; verify dependency metadata before publishing a release.

## Project License

ByteScry source code is licensed under the MIT License. See `LICENSE`.

## Runtime Dependencies

ByteScry uses and may redistribute these components in release packages:

| Component | Purpose | License |
| --- | --- | --- |
| CFR (`org.benf:cfr`) | Java decompiler engine | MIT |
| Vineflower (`org.vineflower:vineflower`) | Java decompiler engine | Apache License 2.0 |
| JADX (`io.github.skylot:*`) | Android DEX/APK decompiler engine | Apache License 2.0 |
| ASM (`org.ow2.asm:*`) | JVM bytecode parsing | BSD-style license |
| JavaFX / OpenJFX (`org.openjfx:*`) | GUI toolkit | GPLv2 with Classpath Exception |
| picocli (`info.picocli:picocli`) | CLI parsing | Apache License 2.0 |
| SLF4J, Logback, Gson, Guava, Kotlin, ANTLR, Commons IO, protobuf, Android tools, and JADX transitive dependencies | Runtime support for JADX and CLI/GUI functionality | See each artifact's bundled license metadata |

## Bundled Java Runtime

The Windows GUI release embeds a jlink-generated Java runtime image. The runtime
is derived from the JDK used to build the release and includes its `legal/`
directory. Keep that directory in any unpacked/internal runtime distribution.

## Release Packaging Requirements

When publishing release binaries:

- Include `LICENSE` and this `THIRD_PARTY_NOTICES.md` with archive packages.
- Do not remove dependency license files embedded in third-party JARs.
- For Windows single-file `bytescry.exe`, publish this notice file alongside
  the release asset on GitHub because the executable self-extracts its embedded
  runtime and libraries at first launch.
- If dependencies are added, removed, or upgraded, regenerate/review this file.

## Reverse Engineering Notice

ByteScry is a decompiler. Users are responsible for ensuring they have the
right to inspect or decompile any artifact they open with the tool.
