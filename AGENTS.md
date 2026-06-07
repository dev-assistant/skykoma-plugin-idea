# Skykoma Plugin for IntelliJ IDEA

## Overview
- **Plugin ID:** `cn.hylstudio.skykoma.plugin.idea`
- **Build System:** Gradle (Kotlin DSL), JDK 21
- **Target IDE:** IntelliJ IDEA 2026.1
- **Kotlin Plugin:** `org.jetbrains.kotlin.jvm` 2.3.10
- **IntelliJ Platform Plugin:** `org.jetbrains.intellij.platform` 2.16.0

## Key Features
1. Register IDEA as a custom Jupyter kernel (via `kotlin-jupyter-kernel`)
2. AST analysis and auto upload
3. HTTP API server (Vert.x) for external Jupyter client communication

## Architecture

### Jupyter Kernel Integration
The plugin embeds a Kotlin Jupyter kernel inside the IDE process:

1. **Registration:** `IdeaPluginAgentServerImpl.registerAsJupyterKernel()` runs `python -m kotlin_kernel add-kernel` to register the kernel, then patches `kernel.json` to point to the correct Python executable and `run_kotlin_kernel_idea` module.
2. **HTTP API:** Vert.x server exposes endpoints (`/startJupyterKernel`, `/stopJupyterKernel`, `/queryJupyterKernelStatus`) for external Jupyter client communication.
3. **Kernel Runtime:** `KotlinReplWrapper.kt` wraps `kotlin-jupyter-kernel`'s `runServer()` to create an embedded Kotlin REPL kernel inside the IDE process, using the plugin's classloader and classpath.

### Key Files
| File | Role |
|------|------|
| `build.gradle.kts` | Build config with Jupyter API/kernel dependencies |
| `src/main/kotlin/.../KotlinReplWrapper.kt` | Core Jupyter kernel wrapper, initializes and runs embedded kernel |
| `src/main/java/.../service/IdeaPluginAgentServer.java` | Service interface for Jupyter kernel lifecycle |
| `src/main/java/.../service/impl/IdeaPluginAgentServerImpl.java` | Service implementation: kernel registration, lifecycle, HTTP API |
| `src/main/java/.../service/verticle/AgentHttpApiVerticle.java` | HTTP API endpoints for Jupyter control |
| `src/main/java/.../toolwindow/SkykomaToolWindowFactory.java` | Tool window UI with Jupyter kernel status/controls |
| `src/main/java/.../config/IdeaPluginSettingsDialog.java` | Settings UI for Jupyter configuration |
| `src/main/java/.../SkykomaConstants.java` | Jupyter configuration constants and defaults |
| `src/main/resources/kernel-compiler-impl-0.19.0-948.jar` | Bundled kernel compiler implementation JAR |
| `src/main/resources/META-INF/plugin.xml` | Plugin descriptor |

### Jupyter Dependency Chain
- `kotlin-jupyter-api:0.19.0-948` (compileOnly)
- `kotlin-jupyter-kernel:0.19.0-948` (implementation, excludes `kernel-compiler-impl`, `kotlin-scripting-common`, `kotlin-scripting-compiler-embeddable`)
- `kernel-compiler-impl-0.19.0-948.jar` (local file dependency, copied to sandbox lib)
- `kotlin-compiler-embeddable:2.3.10` (implementation — provides `org.jetbrains.kotlin.com.intellij.openapi.util.Disposer` etc.)
- `kotlin-scripting-jvm:2.3.10` (compileOnly)

### ClassLoader Architecture
- The plugin runs inside IntelliJ's `PluginClassLoader`
- `KotlinReplWrapper` receives the plugin's `ClassLoader` and uses it to build the script classpath
- The Jupyter kernel thread uses `pluginClassLoader` as its context classloader
- **Critical issue:** The plugin bundles Kotlin compiler classes that may conflict with IntelliJ's built-in Kotlin plugin classes, causing `LinkageError` due to different `PluginClassLoader` instances loading the same classes

## Build Commands
```bash
gradlew buildPlugin        # Build the plugin ZIP
gradlew runIde             # Run IDE with plugin for testing
```
