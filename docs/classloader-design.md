# Skykoma IDEA Plugin - 类加载器设计分析

## 项目目标

将 IntelliJ IDEA 注册为自定义 Jupyter Kernel，使用户可以在 Jupyter Notebook 中**动态执行 IDEA 内部的 Kotlin/Java 代码**，直接调用 IDE 的 API（如 `ApplicationManager.getApplication()`、PSI 操作等），实现 IDE 的脚本化/自动化。

## 架构概览

```
┌─────────────────────────────────────────────────────────┐
│  Jupyter Notebook (Python)                              │
│  └─ run_kotlin_kernel (Python)                          │
│     └─ HTTP ──► http://127.0.0.1:2333/startJupyterKernel│
└─────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│  Skykoma Plugin (IntelliJ IDEA)                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │ IdeaPluginAgentServerImpl.startJupyterKernel()   │   │
│  │  └─ new Thread { KotlinReplWrapper.main() }      │   │
│  │     └─ runServer() → JupyterZmqServerRunner      │   │
│  │        └─ REPL 执行用户代码                        │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

## 涉及的五个类加载器

```
Bootstrap CL (JVM 核心类)
  │
  ├── Platform CL (JDK 扩展)
  │     │
  │     ├── App CL (IDE 启动类, lib/*.jar)
  │     │     │
  │     │     ├── PluginCL[Kotlin]  ←── Kotlin 插件类加载器
  │     │     │   - kotlin-plugin.jar (含旧版 K2ReplCompiler)
  │     │     │   - kotlinc.*.jar
  │     │     │   - kotlin-compiler-embeddable-2.4.0-dev-6891.jar
  │     │     │   - kotlin-stdlib, kotlin-reflect (IDE 版本)
  │     │     │
  │     │     └── PluginCL[Skykoma] ←── 本项目插件类加载器
  │     │         - skykoma-plugin-idea.jar (本项目代码)
  │     │         - kotlin-jupyter-kernel.jar
  │     │         - kotlin-jupyter-api.jar
  │     │         - kotlin-compiler-embeddable-2.4.0-dev-6891.jar (jupyter 传递)
  │     │         - kotlin-scripting-compiler-embeddable-2.4.0-dev-6891.jar (jupyter 传递)
  │     │         - kotlin-stdlib-2.4.0-dev-6891.jar  ← prepareSandbox 删除
  │     │         - kotlin-reflect-2.3.10-RC.jar       ← prepareSandbox 删除
  │     │         - vertx, okhttp, netty 等
  │     │
  │     └── (IDE 其他类加载器...)
  │
  └── Thread Context CL ──► PluginCL[Skykoma]
```

### 类加载器 1: Python Jupyter Core 指定的 JAR

Python 端 `run_kotlin_kernel` 通过 `-classpath` 参数传入的 JAR：
- `lib-0.19.0-944.jar`, `api-0.19.0-944.jar` (jupyter 核心)
- `kotlin-stdlib-2.3.10-RC.jar`, `kotlin-reflect-2.3.10-RC.jar`
- `kotlinx-serialization-*.jar`, `kotlin-script-runtime-*.jar`

这些 JAR 被 KotlinReplWrapper 合并到 `scriptClasspath` 中，供 REPL 编译器编译用户脚本时使用。

### 类加载器 2: IDE 自身的类加载器 (App CL)

IDE 启动时加载的所有 JAR（`idea-2026.1/lib/*.jar`），包含：
- IntelliJ Platform API
- IDE 内置的 Kotlin 库（`intellij.libraries.kotlin.reflect.jar` 等）

### 类加载器 3: Kotlin 插件类加载器 (PluginCL[Kotlin])

IDE 内置 Kotlin 插件的 PluginClassLoader，加载：
- `kotlin-plugin.jar`（Kotlin IDE 支持，**2026 版本内含旧版 K2ReplCompiler**）
- `kotlinc.*.jar`（Kotlin 编译器各模块）
- `kotlin-compiler-embeddable-2.4.0-dev-6891.jar`（包含 repackaged 的 IntelliJ 类，如 `Disposer`）
- IDE 版本的 `kotlin-stdlib`、`kotlin-reflect`

**关键**: `org.jetbrains.kotlin.com.intellij.openapi.util.Disposer` 实际位于 `kotlin-compiler-embeddable` 中。

### 类加载器 4: Skykoma 插件类加载器 (PluginCL[Skykoma])

本项目的 PluginClassLoader，通过 `<depends>org.jetbrains.kotlin</depends>` 声明依赖，可以委托到 PluginCL[Kotlin]。

根据 IntelliJ PluginClassLoader 源码分析：
- PluginCL 先在自己的 classpath 中查找（`loadClassInsideSelf`）
- 找不到则遍历所有 parent PluginCL（来自 `<depends>` 链）
- `kotlin.reflect.*`、`kotlin.jvm.functions.*` 等被强制从 platform classloader 加载（`mustBeLoadedByPlatform`）

### 类加载器 5: REPL 脚本类加载器

Jupyter REPL 编译器使用 `scriptClasspath` 编译和执行用户脚本。这个 classpath 由 `KotlinReplWrapper.main()` 拼接：
- `System.getProperty("java.class.path")` → IDE 所有 JAR
- `classpathFromClassloader(SystemClassLoader)` → 系统类路径
- `scriptCompilationClasspathFromContextOrStdlib(pluginClassLoader)` → 插件 lib 的 Kotlin JAR
- `KotlinJars.kotlinScriptStandardJars` → kotlin-stdlib + kotlin-script-runtime
- Python 端传入的 `-classpath` JAR

## 类加载器冲突分析

### 冲突 1: kotlin-stdlib / kotlin-reflect 版本不一致

| 来源 | kotlin-stdlib 版本 |
|------|-------------------|
| Kotlin 插件 (IDE 内置) | 2.4.0-dev-6891 (IDE 版本) |
| Skykoma 插件 lib/ | 2.4.0-dev-6891 (jupyter 传递依赖) |
| Python Jupyter 端 | 2.3.10-RC |

虽然版本号相近，但来自不同 classloader 的同一个类会被 JVM 视为不同类型，导致 `LinkageError: loader constraint violation`。

**解决方案**: `prepareSandbox` 删除 skykoma lib/ 中的 `kotlin-stdlib-*`、`kotlin-reflect-*` 等，统一使用 Kotlin 插件提供的版本。

### 冲突 2: K2ReplCompiler 方法签名不匹配（核心问题）

`K2ReplCompiler` 和其参数类型 `ScriptCompilationConfiguration` 分布在不同的 JAR 中：

| 类 | 所在 JAR |
|----|---------|
| `K2ReplCompiler` | `kotlin-compiler-embeddable` |
| `ScriptCompilationConfiguration` | `kotlin-scripting-compiler-embeddable` |
| `Disposer` | `kotlin-compiler-embeddable` |

**现象**: `NoSuchMethodError: K2ReplCompiler$Companion.createCompilationState(...)`

**根因**: 方法本身存在，但**方法签名中的参数类型来自不同 classloader**。当 `kotlin-scripting-*` 被 prepareSandbox 删除后，`ScriptCompilationConfiguration` 只能从 Kotlin 插件 classloader 加载，而 `K2ReplCompiler` 从 skykoma lib 加载，JVM 认为参数类型不匹配 → `NoSuchMethodError`。

**解决方案**: 保留 `kotlin-scripting-*` JAR，让 PluginCL[Skykoma] 的 `loadClassInsideSelf` 从自己的 classpath 加载所有 compiler/scripting 类，确保它们来自同一套 JAR。

### 冲突 3: ServiceLoader SPI 机制

`ServiceLoader` 使用调用类的 classloader 去加载 SPI 实现。如果 `KotlinReplWrapper` 由 PluginCL[Skykoma] 加载，但 SPI 实现类（如 `JupyterZmqServerRunner`）和接口（`JupyterServerRunner`）来自不同 classloader，会导致 `not a subtype` 错误。

**解决方案**: 所有 jupyter 类由 PluginCL[Skykoma] 统一加载，SPI 实现和接口在同一 classloader 中。

## 已尝试的方案

| 方案 | 策略 | 结果                                                                |
|------|------|-------------------------------------------------------------------|
| Self-first URLClassLoader | 先从插件 lib 加载，再委托 parent | `LinkageError`: kotlin.reflect.KClass 跨 classloader 类型不匹配         |
| Parent-first URLClassLoader (parent=PlatformCL) | 先委托 PlatformCL，再自己加载 | `NoClassDefFoundError: Gson` - PlatformCL 太窄                      |
| Parent-first URLClassLoader (parent=PluginCL[Skykoma]) | 先委托 skykomaCL，再自己加载 | `LinkageError`: KClass itable 不匹配                                 |
| Parent-first URLClassLoader (parent=PluginCL[Kotlin]) | 先委托 kotlinCL，再自己加载 | `NoClassDefFoundError: Disposer` - kotlinCL 不暴露子 CL               |
| Hybrid (Disposer 包 self-first) | Disposer 包 self-first，其余 parent-first | Disposer 由自定义 CL 加载，依赖链跨 CL 失败                                    |
| prepareSandbox 删除冲突 JAR + PluginCL | 删除 kotlin-stdlib 等，用 PluginCL | `NoClassDefFoundError: Disposer` - kotlin-compiler-embeddable 被误删 |
| prepareSandbox 保留 compiler-embeddable + PluginCL | 保留 kotlin-compiler-embeddable | `NoSuchMethodError: K2ReplCompiler` - scripting JAR 被误删，参数类型跨 CL  |
| **prepareSandbox 保留 compiler+scripting + PluginCL（当前）** | 保留 kotlin-compiler-embeddable 和 kotlin-scripting-* | 可行                                                                |

## 当前方案

### 核心思路

**利用 PluginCL 自身的 `loadClassInsideSelf` 机制**，让所有 compiler/scripting 类从 skykoma lib 的同一套 JAR 加载，避免跨 classloader 类型不匹配。

不需要自定义 classloader，不需要反射，不需要复杂的包过滤策略。

### 构建时 (build.gradle.kts)

```kotlin
// kotlin-compiler-embeddable 和 kotlin-scripting-jvm 声明为 compileOnly
// 编译时用 2.3.10，运行时由 jupyter-kernel 传递依赖提供 2.4.0-dev-6891
compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.10")
compileOnly("org.jetbrains.kotlin:kotlin-scripting-jvm:2.3.10")

// prepareSandbox: 只删除会和 Kotlin 插件冲突的基础库 JAR
// 保留 kotlin-compiler-embeddable 和 kotlin-scripting-* (jupyter kernel 运行时需要)
prepareSandbox {
    doLast {
        val conflictingPrefixes = listOf(
            "kotlin-stdlib-", "kotlin-reflect-", "kotlin-script-runtime-",
            "kotlinx-serialization-", "kotlinx-coroutines-",
            "kotlin-daemon-embeddable-",
            "kotlin-build-tools-api-", "kotlin-serialization-compiler-plugin-"
        )
        // 保留: kotlin-compiler-embeddable, kotlin-scripting-*
    }
}
```

### 运行时

直接使用 `PluginCL[Skykoma]` 作为线程 context classloader：

```java
ClassLoader pluginClassLoader = ideaPluginDescriptor.getPluginClassLoader();
Thread jupyterThread = new Thread(() -> {
    KotlinReplWrapper wrapper = KotlinReplWrapper.getInstance(pluginClassLoader);
    wrapper.makeEmbeddedRepl(payload);
});
jupyterThread.setContextClassLoader(pluginClassLoader);
jupyterThread.start();
```

### JAR 保留/删除策略

| JAR 前缀 | 操作 | 原因 |
|---------|------|------|
| `kotlin-stdlib-*` | 删除 | 和 Kotlin 插件版本冲突 |
| `kotlin-reflect-*` | 删除 | 和 Kotlin 插件版本冲突 |
| `kotlin-script-runtime-*` | 删除 | 和 Kotlin 插件版本冲突 |
| `kotlinx-serialization-*` | 删除 | 和 Kotlin 插件版本冲突 |
| `kotlinx-coroutines-*` | 删除 | 和 Kotlin 插件版本冲突 |
| `kotlin-daemon-embeddable-*` | 删除 | jupyter kernel 不需要 |
| `kotlin-build-tools-api-*` | 删除 | jupyter kernel 不需要 |
| `kotlin-serialization-compiler-plugin-*` | 删除 | jupyter kernel 不需要 |
| `kotlin-compiler-embeddable-*` | **保留** | K2ReplCompiler、Disposer 在此 |
| `kotlin-scripting-*` | **保留** | ScriptCompilationConfiguration 等在此 |

## 关于 2025 vs 2026

2025 版本的 Kotlin 插件可能尚未在 `kotlin-plugin.jar` 中打包 `K2ReplCompiler` 相关类，因此 PluginCL[Skykoma] 的 `loadClassInsideSelf` 能直接从自己的 classpath 加载，没有冲突。

2026 版本 Kotlin 插件新增了 `K2ReplCompiler` 到 `kotlin-plugin.jar`，导致同一个类在两个 classloader 的 classpath 中都存在。当 `kotlin-scripting-*` 被误删后，`ScriptCompilationConfiguration` 回退到 Kotlin 插件 classloader 加载，而 `K2ReplCompiler` 仍在 skykoma classloader 中，触发跨 classloader 方法签名不匹配。

**和官方 Kotlin Notebook 功能无关**——官方 Notebook 是 IDE 内置的 Jupyter 前端（`.ipynb` 文件编辑器），不涉及自定义 kernel 的 classloader 问题。

## 方案设计评审

**优点**:
1. 极简——不需要自定义 classloader，不需要反射，不需要包过滤
2. 利用 PluginCL 自身的 `loadClassInsideSelf` 机制，compiler/scripting 类天然来自同一套 JAR
3. 只删除真正冲突的基础库 JAR（stdlib、reflect 等），保留 jupyter kernel 运行时需要的 compiler/scripting JAR

**潜在风险**:
1. 如果未来 Kotlin 插件在 `kotlin-plugin.jar` 中打包更多 compiler 类，可能需要扩展保留列表
2. `kotlin-compiler-embeddable` 和 `kotlin-scripting-*` 版本必须与 jupyter kernel 编译时版本一致（当前都是 `2.4.0-dev-6891`）

## 当前状态

### 已解决

1. **Kernel 启动**: `println("succ")` 正常执行并输出
2. **类加载器冲突**: 通过 `prepareSandbox` 保留 `kotlin-compiler-embeddable-*` 和 `kotlin-scripting-*` JAR，PluginCL[Skykoma] 的 `loadClassInsideSelf` 统一加载所有 compiler/scripting 类
3. **product-info.json**: 通过 `platformPath` 从 IDE 安装目录复制到 sandbox 根目录
4. **线程 context classloader**: `KotlinReplWrapper.main()` 开头设置 `Thread.currentThread().contextClassLoader = pluginClassLoader`

### 已解决: DependsOn 注解加载

**现象**: `unable to load class jupyter.kotlin.DependsOn`

**根因**: `JvmGetScriptingClass` 内部 classloader 链校验逻辑：

```kotlin
// JvmGetScriptingClass 内部
if (fromClass != null) {
    if (fromClass.java.classLoader == null) return fromClass
    val actualClassLoadersChain = generateSequence(contextClassLoader) { it.parent }
    if (actualClassLoadersChain.any { it == fromClass.java.classLoader }) return fromClass
}
```

- `fromClass` = `DependsOn`（已找到，classloader = PluginCL[Skykoma]）
- `contextClassLoader` 参数：可能为 `null`，或者为 Kotlin 插件 CL（PluginCL[Kotlin]）
- 无论哪种情况，链中都不包含 PluginCL[Skykoma] → 校验失败，报错

**实测发现**: 即使 `Thread.currentThread().contextClassLoader` 已被设置为 PluginCL[Skykoma]，`getCompilationConfiguration` 内部传入 `JvmGetScriptingClass` 的 `contextClassLoader` 参数来自 `ScriptDefinition.contextClassLoader` 或 `ScriptingHostConfiguration[jvm.baseClassLoader]`——这些值在 K2 REPL 路径中可能是 `null`，也可能被 jupyter 设为 Kotlin 插件 CL。Kotlin 插件 CL 的 `loadClassInsideSelf` 中**没有** `jupyter.kotlin.DependsOn`（该类只在 skykoma lib 里），所以即便回退到 `Thread.currentThread().contextClassLoader` 也不够——必须**强制覆盖**为 PluginCL[Skykoma]。

**调用链**:
```
getScriptCollectedData()
  → hostConfiguration[getScriptingClass] = JvmGetScriptingClass
  → jvmGetScriptingClass(ann, contextClassLoader=null/KotlinPluginCL, hostConfiguration)
    → Class.forName(ann, false, Thread.currentThread().contextClassLoader) // 找到 DependsOn
    → classloader 链检查: 链中无 PluginCL[Skykoma] → 失败
```

### 当前修复方案

不修改 `kotlin-jupyter` 源码，全部修复落在 `skykoma-plugin-idea` 内：

#### 1. 自定义 `GetScriptingClassByClassLoader`

`cn.hylstudio.skykoma.plugin.idea.jupyter.ContextClassLoaderAwareScriptClassGetter`：包装 `JvmGetScriptingClass`，**强制使用插件 ClassLoader**（PluginCL[Skykoma]），忽略上游传入的 `contextClassLoader` 参数。

```kotlin
class ContextClassLoaderAwareScriptClassGetter(
    private val fallbackClassLoader: ClassLoader,
) : GetScriptingClassByClassLoader {
    private val delegate = JvmGetScriptingClass()

    override fun invoke(classType, contextClass, hostConfiguration): KClass<*> {
        // contextClass 来自插件代码（KClass），其 classLoader 通常已是插件 CL
        val cl = contextClass.java.classLoader ?: fallbackClassLoader
        return delegate(classType, cl, hostConfiguration)
    }

    override fun invoke(classType, contextClassLoader, hostConfiguration): KClass<*> {
        // contextClassLoader 来自 ScriptDefinition/jvm.baseClassLoader，
        // 可能为 null 或 Kotlin 插件 CL，二者均无法加载 DependsOn
        // → 直接强制使用插件 CL
        val cl = fallbackClassLoader
        return delegate(classType, cl, hostConfiguration)
    }
}
```

> **设计权衡**: 第一版回退逻辑是 `contextClassLoader ?: fallbackClassLoader`，但实测 `contextClassLoader` 即使非 null 也是 Kotlin 插件 CL，仍然找不到 `jupyter.kotlin.DependsOn`。所以第二个重载直接丢弃参数，强制覆盖。

#### 2. 自定义 `CompilerServiceProvider`

`cn.hylstudio.skykoma.plugin.idea.jupyter.PatchedCompilerServiceProvider`：

- 通过 `ServiceLoader.load(CompilerServiceProvider, pluginCL)` 找到真实的 `InProcessCompilerServiceProvider`（priority=100）作为 delegate
- 调用 delegate 创建真实 `CompilerServiceImpl` 实例
- 反射读取 `compilationConfig` 私有字段（`ScriptCompilationConfiguration`）
- 用 `with { hostConfiguration.update { it.with { getScriptingClass(customGetter) } } }` 构造新配置
- 反射写回 `compilationConfig` 字段

#### 3. 内联 `runServer` + 注入自定义 Provider

`KotlinReplWrapper.kt` 内联 `org.jetbrains.kotlinx.jupyter.runServer`、`createMessageHandler`、`initializeKernelSession`（原 `@PublishedApi internal`，无法直接调用）。在 `createMessageHandler` 中用匿名子类覆盖 `DefaultReplComponentsProvider`：

```kotlin
val replProvider = object : DefaultReplComponentsProvider(
    replSettings, communicationFacility, commManager, NoOpInMemoryReplResultsHolder,
) {
    override fun provideForceCompilerServiceProvider(): CompilerServiceProvider =
        PatchedCompilerServiceProvider(pluginClassLoader)

    override fun provideCompilerServiceSpiClassloader(): ClassLoader = pluginClassLoader
}
```

`ReplForJupyterImpl` 优先使用 `forceCompilerServiceProvider`，绕过 SPI 查找直接走 `PatchedCompilerServiceProvider`。

#### 关键文件

| 文件 | 作用 |
|------|------|
| `jupyter/ContextClassLoaderAwareScriptClassGetter.kt` | 强制使用插件 CL 的 `GetScriptingClassByClassLoader` |
| `jupyter/PatchedCompilerServiceProvider.kt` | 反射 patch `CompilerServiceImpl.compilationConfig` |
| `KotlinReplWrapper.kt` | 内联 `runServer`，匿名子类注入自定义 provider |
| `service/impl/IdeaPluginAgentServerImpl.java:301` | `jupyterServerThread.setContextClassLoader(pluginClassLoader)` |
