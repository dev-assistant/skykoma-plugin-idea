# Jupyter Kernel ClassLoader 冲突解决计划

## 问题描述

插件在 IntelliJ IDEA 中嵌入 Jupyter kernel 时，Kotlin 编译器类（如 `Disposer`、`CompilerConfigurationKey` 等）由两个不同的 `PluginClassLoader` 加载，导致 `LinkageError`。

## 问题根因

### 两个 ClassLoader 来源

1. **Skykoma 插件的 PluginClassLoader**
   - 加载 `kotlin-compiler-embeddable`（implementation 依赖）
   - 包含 `org.jetbrains.kotlin.com.intellij.openapi.util.Disposer` 等类

2. **IntelliJ Kotlin 插件的 PluginClassLoader**
   - IDEA 自带的 Kotlin 插件也包含相同包路径的类
   - 加载 `org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys` 等类

### 冲突链路

```
CompilationContextKt (来自 Skykoma ClassLoader)
  → 调用 ScriptingConfigurationKeys.getSCRIPT_DEFINITIONS()
    → ScriptingConfigurationKeys 在 Kotlin 插件 ClassLoader 中
    → 返回类型 CompilerConfigurationKey 在 Skykoma ClassLoader 中
    → 两个 ClassLoader 加载了"相同"的类但 JVM 认为是不同的 → LinkageError
```

## 版本兼容性分析

### 基准版本

| 组件 | 版本 | 来源 |
|------|------|------|
| IntelliJ IDEA | 2026.1 Build #IU-261.22158.277 | 用户 IDE |
| IntelliJ 内置 Kotlin 插件 | 编译器 `2.4.0-dev-2631`，stdlib `2.3.20-RC2` | intellij-community tag `idea/261.22158.277` |
| kotlin-jupyter 源码 | `0.19.0.950` | `D:\code\kotlin-jupyter` |

### kotlin-jupyter 兼容性表（来自 `docs/compatibility.md`）

0.19.0 系列（944-950）的版本映射：

| 属性 | 值 |
|------|-----|
| Kotlin 脚本引擎 | `2.4.0-dev-6891` |
| 内核编译器 | `2.3.10-RC` |
| Gradle 插件 Kotlin | `2.3.10-RC` |
| Kotlin 语言级别 | `2.3` |

### 关键结论

1. **嵌入式模式不需要与 IDEA Kotlin 版本对齐** — kotlin-jupyter 的 `embeddableKernel` 产物通过包重定位（shade）隔离了 Kotlin 依赖
2. **兼容表明确显示** 0.19.0 系列使用编译器 `2.3.10-RC` + 脚本引擎 `2.4.0-dev-6891`，两者版本不同是设计如此
3. **Python 端 jar 版本（0.19.0-944）落后于最新（0.19.0-950）**，但暂不更新 Python 端

## 最终方案：使用 kotlin-jupyter 官方 embeddableKernel 产物

### 方案原理

kotlin-jupyter 项目有一个专门的 **`embeddableKernel`** 构建产物，通过 `CompilerRelocatedJarConfigurator` 进行包重定位（package relocation/shading）：

```
重定位规则:
  org.jetbrains.kotlin.*           → 避免与 IntelliJ 内置 Kotlin 插件冲突
  org.jetbrains.kotlinx.serialization.*  → 避免序列化库冲突
```

同时，embeddableKernel 默认使用 **守护进程编译器模式**（daemon mode），编译器跑在独立进程中，不跟 IntelliJ 共享 ClassLoader。

SPI 服务提供者：`org.jetbrains.kotlinx.jupyter.compiler.daemon.client.DaemonCompilerServiceProvider`

### embeddableKernel 包含的组件（实测）

经实测，`embeddableKernel-0.19.0-950.jar`（约 76 MB）实际仅包含：

- shaded 后的 Kotlin 编译器（`ktnb.org.jetbrains.kotlin.*` 前缀），用于隔离与 IDEA 内置 Kotlin 插件的冲突
- shaded 后的 `kotlinx-serialization`
- 部分 `org.jetbrains.kotlinx.jupyter.*` 内核实现包（`codegen`、`compiler`、`dependencies`、`libraries`、`logging`、`magics`、`messaging`、`repl`）
- 入口类 `org.jetbrains.kotlinx.jupyter.IkotlinKt`

**不包含**：
- `org.jetbrains.kotlinx.jupyter.api.*`（在 `kotlin-jupyter-api` jar）
- `org.jetbrains.kotlinx.jupyter.config.*`（含 `DefaultKernelLoggerFactory`、`RuntimeKernelProperties`，在 `kotlin-jupyter-intellij-compiler-dependencies` jar）
- `org.jetbrains.kotlinx.jupyter.protocol.*`（含 `KernelArgs`，在 `kotlin-jupyter-protocol` jar）
- `org.jetbrains.kotlinx.jupyter.startup.*`（含 `KernelJupyterParamsSerializer`、`KotlinKernelOwnParams`）
- `org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings`、`org.jetbrains.kotlinx.jupyter.repl.ReplConfig`（在 `kotlin-jupyter-intellij-dependencies-shared` jar）
- `kernel-compiler-impl`（编译器实现，daemon 模式由 `kernel-compiler-impl-0.19.0-950.jar` 提供）

因此必须保留 `kotlin-jupyter-kernel` 依赖以拉取上述传递依赖，仅排除原始未 shaded 的 Kotlin 编译器，由 embeddableKernel 提供 shaded 替代品。

### 最终依赖版本

| 组件 | 版本 | 说明 |
|------|------|------|
| **kotlin-jupyter-api Gradle 插件** | `0.19.0-950` | 保留 |
| **kotlin-jupyter-api** | `0.19.0-950` | compileOnly |
| **kotlin-jupyter-kernel** | `0.19.0-950` | implementation，排除非 shaded 编译器 |
| **embeddableKernel jar (local)** | `0.19.0-950` | 提供 shaded Kotlin 编译器，避免 ClassLoader 冲突 |
| **kernel-compiler-impl jar (local)** | `0.19.0-950` | 守护进程编译器实现 |
| **kotlin-compiler-embeddable** | **不再需要** | 由 embeddableKernel 提供 shaded 版 |
| **kotlin-scripting-compiler-embeddable** | **不再需要** | 由 embeddableKernel 提供 shaded 版 |
| **kotlin-scripting-jvm** | **不再需要** | embeddableKernel 已内置 |
| **Kotlin Gradle Plugin** | `2.3.10` | 保持不变 |

### 改动清单

#### 1. `build.gradle.kts`

- 升级 `kotlin-jupyter-api` Gradle 插件到 `0.19.0-950`
- 升级 `kotlin-jupyter-api` 与 `kotlin-jupyter-kernel` 依赖到 `0.19.0-950`
- **保留** `kotlin-jupyter-kernel` implementation 依赖（提供 `IkotlinKt`/`KernelArgs`/`DefaultReplSettings`/`RuntimeKernelProperties` 等启动入口类，这些类未被 embeddableKernel jar 重新打包）
- 在 `kotlin-jupyter-kernel` 的 exclude 中追加排除非 shaded 的 Kotlin 编译器：
  - `kernel-compiler-impl`（由本地 `kernel-compiler-impl-0.19.0-950.jar` 提供，daemon 模式使用）
  - `kotlin-scripting-common`
  - `kotlin-compiler-embeddable`
  - `kotlin-scripting-compiler-embeddable`
- 移除全局 `configurations.all { exclude(...) }`（exclude 已收敛到具体依赖块内）
- 添加 `embeddableKernel-0.19.0-950.jar` 为 implementation file 依赖，提供 shaded（`ktnb.` 前缀）的 Kotlin 编译器实现，避免与 IDEA 内置 Kotlin 插件类冲突
- 更新 `prepareSandbox` 同时复制 `embeddableKernel` jar 与 `kernel-compiler-impl` jar 到 sandbox `lib/` 目录

#### 2. `KotlinReplWrapper.kt`

- **无需修改**（当前代码已正确使用 `EmbeddedKernelRunMode` 与 `kotlinVersion = "2.3.10"`）
- 注意事项：embeddableKernel jar 不包含 `DefaultReplSettings`/`KernelArgs`/`RuntimeKernelProperties` 等启动 API，这些类仍由 `kotlin-jupyter-kernel` 的传递依赖提供，因此编译和运行不受影响

#### 3. 资源文件

- 添加 `src/main/resources/embeddableKernel-0.19.0-950.jar`（已从远程复制）
- 替换 `src/main/resources/kernel-compiler-impl-0.19.0-948.jar` 为 `0.19.0-950`（已从远程复制）
- 删除旧的 `kernel-compiler-impl-0.19.0-948.jar`

### 与旧方案的对比

| 旧方案 | 新方案 |
|--------|--------|
| 直接依赖 `kotlin-jupyter-kernel` | 使用官方 `embeddableKernel` 产物 |
| 手动排除传递依赖避免冲突 | 包重定位自动隔离，无需手动排除 |
| 使用 in-process 编译器 | 使用 daemon 编译器（独立进程） |
| 需要 `kotlin-compiler-embeddable` | 不需要，embeddableKernel 已内置 shade 版 |
| 需要 `kotlin-scripting-jvm` | 不需要，embeddableKernel 已内置 |
| 版本 0.19.0-948 | 版本 0.19.0-950 |

### 为什么不用 Maven Central 直接下载

`embeddable-kernel` 产物发布到 JetBrains Space 私有仓库（`https://packages.jetbrains.team/maven/p/ij/intellij-dependencies`），不在 Maven Central。因此从已编译的远程 build 服务器获取 jar 文件。

## 当前状态

- [x] 从远程 build 服务器获取 `embeddableKernel-0.19.0-950.jar`
- [x] 从远程 build 服务器获取 `kernel-compiler-impl-0.19.0-950.jar`
- [x] 更新 `build.gradle.kts` 依赖
- [x] 删除旧资源文件 `kernel-compiler-impl-0.19.0-948.jar`
- [x] 构建验证（`gradlew buildPlugin` 通过）
- [ ] 运行时验证（`gradlew runIde` 内启动 Jupyter kernel）

## 下一步

1. 运行 `gradlew runIde` 测试 Jupyter kernel 启动
2. 在 IDE 中触发 Jupyter kernel 注册与启动，验证不再发生 `LinkageError`
3. 如运行时仍发生类冲突，检查 sandbox `lib/` 下两个 jar 的加载顺序，必要时调整 `prepareSandbox` 让 embeddableKernel jar 优先加载
