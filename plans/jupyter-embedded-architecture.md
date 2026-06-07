# kotlin-jupyter Embedded Mode 架构分析

## 概述

kotlin-jupyter 提供两种运行模式：**Standalone**（独立进程）和 **Embedded**（嵌入宿主进程）。skykoma 插件使用 Embedded 模式将 Jupyter kernel 嵌入 IntelliJ IDEA 进程。

## 入口点

```kotlin
// Ikotlin.kt
fun embedKernel(
    cfgFile: File,
    resolutionInfoProviderFactory: ResolutionInfoProviderFactory?,
    scriptReceivers: List<Any>? = null,
)
```

- 读取 `System.getProperty("java.class.path")` 作为 `scriptClasspath`
- 创建 `KotlinKernelOwnParams` 并传入 classpath
- 使用 `EmbeddedKernelRunMode`
- 调用 `runServer(replSettings)` 启动内核

## KernelRunMode 对比

| 属性 | StandaloneKernelRunMode | EmbeddedKernelRunMode |
|------|------------------------|----------------------|
| `name` | `"Standalone"` | `"Embedded"` |
| `createIntermediaryClassLoader()` | 返回 `MultiDelegatingClassLoader` | **返回 `null`** |
| `shouldKillProcessOnShutdown` | `true` | `false` |
| `inMemoryOutputsSupported` | `false` | `false` |
| `isRunInsideIntellijProcess` | `false` | `false` |
| `streamSubstitutionType` | `BLOCKING` | `BLOCKING` |
| `threadLocalStreamSubstitution` | `false` | `true` |

### 关键差异

1. **无中间 ClassLoader** — `createIntermediaryClassLoader()` 返回 `null`，snippet 代码直接共享宿主 ClassLoader 层级
2. **不杀进程** — `shouldKillProcessOnShutdown = false`，内核关闭时抛 `InterruptedException` 而非 `exitProcess(0)`
3. **线程级流替换** — 输出流仅对执行线程替换，不影响宿主其他线程

## ClassLoader 架构

### Standalone 模式（对比参考）

```
Bootstrap ClassLoader
  └── System ClassLoader
        └── Kernel ClassLoader (加载 kotlin-jupyter-kernel)
              └── MultiDelegatingClassLoader (中间层)
                    ├── DelegatingClassLoader (策略: kotlin.* → kernel, 其他 → system)
                    └── URLClassLoader (scriptClasspath, parent=DelegatingClassLoader)
                          └── Snippet ClassLoader
```

`createDefaultDelegatingClassLoader` 将以下前缀委托给 kernel ClassLoader：
`kotlin.`, `org.jetbrains.kotlin.`, `ktnb.`, `jupyter.kotlin.`, `org.jetbrains.kotlinx.jupyter.api.`, `org.jetbrains.kotlinx.jupyter.protocol.api.`, `org.jetbrains.kotlinx.jupyter.util.`, `kotlinx.serialization.`, `org.slf4j.`

### Embedded 模式（skykoma 使用）

```
Bootstrap ClassLoader
  └── Platform ClassLoader
        └── PluginClassLoader (IntelliJ Kotlin 插件)
        └── PluginClassLoader (skykoma 插件, 加载 embeddableKernel)
              └── Snippet ClassLoader (直接以 PluginClassLoader 为 parent)
```

**没有中间 ClassLoader 层**，`baseClassLoader` 未显式设置，Kotlin scripting runtime 使用默认 ClassLoader 解析。

## ModifiableParentsClassLoader 机制

kotlin-jupyter 提供了 `ModifiableParentsClassLoader` 抽象类，允许宿主动态添加 parent ClassLoader：

```kotlin
abstract class ModifiableParentsClassLoader : ClassLoader(null) {
    abstract fun addParent(parent: ClassLoader)
}
```

- 初始 parent 为 `null`（Bootstrap ClassLoader）
- 宿主可通过 `notebook.intermediateClassLoader` 访问
- 可调用 `addParent()` 动态注册插件 ClassLoader
- **但 `EmbeddedKernelRunMode` 默认返回 `null`，不使用此机制**

## 编译器模式

### 两种编译器服务

| Provider | 优先级 | 模块 | 说明 |
|----------|--------|------|------|
| `InProcessCompilerServiceProvider` | 100 | `kernel-compiler-impl` | 进程内编译 |
| `DaemonCompilerServiceProvider` | 10 | `kernel-compiler-daemon-client` | 守护进程编译 |

通过 SPI 发现：`META-INF/services/org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider`

### embeddableKernel 的编译器选择

embeddableKernel **不包含** `kernel-compiler-impl`（in-process 编译器），只包含 `kernel-compiler-daemon-client`。因此默认使用 **守护进程编译器模式**，编译器在独立进程中运行。

SPI 服务文件内容：
```
org.jetbrains.kotlinx.jupyter.compiler.daemon.client.DaemonCompilerServiceProvider
```

## embeddableKernel 产物

### 构建配置

```kotlin
// build.gradle.kts
embeddableKernel(projects.kotlinJupyterKernel) { isTransitive = false }
embeddableKernel(libs.kotlin.dev.scriptRuntime) { isTransitive = false }
embeddableKernel(projects.kernelCompilerApi) { isTransitive = false }
embeddableKernel(libs.java.websocket) { isTransitive = false }
embeddableKernel(libs.kotlinx.rpc.krpc.client) { ... }
embeddableKernel(libs.kotlinx.rpc.krpc.server) { ... }
embeddableKernel(libs.kotlinx.rpc.krpc.serialization.cbor) { ... }
embeddableKernel(projects.kernelCompilerDaemonApi) { isTransitive = false }
embeddableKernel(projects.kernelCompilerDaemonClient) { isTransitive = false }
addSharedEmbeddedDependenciesTo(embeddableKernel)
// shared embedded: kotlin-scripting-compiler-embeddable, dependencies-resolution-shadowed
```

### 包重定位（Shade）

`CompilerRelocatedJarConfigurator` 执行以下重定位：

```kotlin
relocatePackages {
    +"org.jetbrains.kotlin."              // 避免与 IntelliJ 内置 Kotlin 插件冲突
    +"org.jetbrains.kotlinx.serialization." // 避免序列化库冲突
}
```

重定位后的包前缀为 `ktnb.`（例如 `ktnb.org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor`）。

### 不包含的组件

- `kernel-compiler-impl`（in-process 编译器实现，需单独提供）
- `kotlin-compiler-embeddable`（通过 shade 的 scripting compiler 提供编译器能力）

## 版本兼容性

### IntelliJ IDEA 2026.1 (Build #IU-261.22158.277)

| 组件 | 版本 |
|------|------|
| IDE Build | `261.22158.SNAPSHOT` |
| Kotlin 编译器（内置插件） | `2.4.0-dev-2631` |
| Kotlin stdlib（IDE 运行时） | `2.3.20-RC2` |
| Kotlin 脚本引擎 | `2.4.0-dev-2631` |

### kotlin-jupyter 0.19.0 系列兼容性表

| 属性 | 值 |
|------|-----|
| Kotlin 脚本引擎 | `2.4.0-dev-6891` |
| 内核编译器 | `2.3.10-RC` |
| Gradle 插件 Kotlin | `2.3.10-RC` |
| Kotlin 语言级别 | `2.3` |

### 关键结论

- **嵌入式模式不需要与 IDEA Kotlin 版本对齐** — embeddableKernel 的包重定位已隔离冲突
- **内核编译器（2.3.10-RC）与脚本引擎（2.4.0-dev-6891）版本不同是设计如此**
- **守护进程编译器模式**进一步隔离了编译器 ClassLoader

## 对 skykoma 插件的启示

1. **使用 embeddableKernel 产物**而非原始 `kotlin-jupyter-kernel`，利用官方包重定位解决 ClassLoader 冲突
2. **守护进程编译器**需要 `kernel-compiler-impl` jar 在 classpath 中（用于启动守护进程）
3. **入口 API 不变** — `runServer(replSettings)` 和 `EmbeddedKernelRunMode` 保持不变
4. **无需手动管理 ClassLoader** — embeddableKernel 的 shade 已处理隔离
5. **修正 `kotlinVersion`** — `KotlinReplWrapper.kt` 中硬编码的 `"3.1.10"` 应改为 `"2.3.10"`

## 相关文件

| 文件 | 说明 |
|------|------|
| `kotlin-jupyter/src/main/kotlin/.../Ikotlin.kt` | `embedKernel()` 入口 |
| `kotlin-jupyter/jupyter-lib/api/.../KernelRunMode.kt` | `EmbeddedKernelRunMode` 定义 |
| `kotlin-jupyter/jupyter-lib/api/.../ModifiableParentsClassLoader.kt` | 动态 ClassLoader 机制 |
| `kotlin-jupyter/jupyter-lib/api/.../ClassLoading.kt` | `createDefaultDelegatingClassLoader` |
| `kotlin-jupyter/src/main/kotlin/.../ReplForJupyterImpl.kt` | ClassLoader 链构建逻辑 |
| `kotlin-jupyter/build-plugin/src/build/CompilerRelocatedJarConfigurator.kt` | 包重定位配置 |
| `kotlin-jupyter/build.gradle.kts` | embeddableKernel 构建配置 |
| `kotlin-jupyter/docs/compatibility.md` | 版本兼容性表 |
