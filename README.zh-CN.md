# ByteScry

[English](README.md)

ByteScry 是一个桌面优先的 Java / Android 反编译工作台。它可以打开
`.class`、`.jar`、class 目录、`.apk`、`.dex`、`.aab`、`.apks`、`.apkm`
和 `.xapk`，用于查看反编译源码、对比不同引擎输出、查看字节码、搜索类和导出源码。

## 功能亮点

- JavaFX 桌面 GUI，带原生风格窗口控制，适配高 DPI。
- 多引擎支持：CFR、Vineflower、Simple、JADX。
- 折叠包名树、拖拽打开、类内搜索和行号。
- 双列引擎对比，可选 Bytecode 和 Diagnostics 视图。
- 支持按引擎导出源码。
- Android artifact 通过 JADX 建立源码索引，适合 APK / DEX 查看流程。

## 支持输入

| 输入 | GUI | CLI | 说明 |
| --- | --- | --- | --- |
| `.class` | 支持 | 支持 | CFR、Vineflower、Simple、Bytecode |
| `.jar` | 支持 | 支持 | 默认使用 CFR |
| 目录 | 支持 | 支持 | 递归读取 `.class`；GUI 也会读取目录第一层 `.jar` / Android artifact |
| `.apk`、`.dex`、`.aab`、`.apks`、`.apkm`、`.xapk` | 支持 | 支持 | GUI 默认使用 JADX；CLI 使用 `--engine jadx` |

## 下载

推荐从 GitHub Release 下载：

```text
bytescry.exe                            # Windows 单文件 GUI，无需安装 Java/.NET
bytescry-cli-<version>-linux-x64.tar.gz # Linux CLI
bytescry-gui-<version>-linux-x64.zip    # Linux GUI
```

Windows `bytescry.exe` 是可移动的自解压单文件程序。首次启动时，它会把内置 runtime
和应用文件解压到用户缓存目录，之后可以直接双击运行。

## 构建

要求：

- JDK 17+
- Apache Maven 3.8+

构建全部模块：

```bash
mvn clean package
```

构建 release 产物：

```bash
mvn -Prelease-windows clean package
mvn -Prelease-linux clean package
```

Windows release 输出：

```text
bytescry-gui/target/bytescry.exe
```

Linux release 输出：

```text
bytescry-cli/target/bytescry-cli-1.0.0-linux-x64.tar.gz
bytescry-gui/target/bytescry-gui-1.0.0-linux-x64.zip
```

## 文档

- [使用指南](docs/USAGE.md)
- [设计说明](docs/DESIGN.md)
- [构建指南](docs/BUILD.md)

## 限制

反编译源码是 best-effort。混淆、缺失元数据、编译器转换、非法字节码和 JADX
局部失败都可能降低还原质量。用户需要自行确认是否有权检查或反编译所打开的 artifact。

## 许可证

ByteScry 使用 [MIT License](LICENSE)。第三方依赖说明见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
