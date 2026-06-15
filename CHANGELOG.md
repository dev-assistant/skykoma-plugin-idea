# Changelog

涵盖范围：自 commit `2529313` 之后到当前最新已提交的内容（不含未提交修改）。

## 0.2.x 系列变更（2026.1 适配版）

### 平台与构建升级

- 升级目标 IDE 平台至 IntelliJ IDEA 2026.1，`sinceBuild` 保持在 242。
- 构建升级：
  - Kotlin 升级到 `2.3.10`，Kotlin Jupyter API/Kernel 升级到 `0.19.0-951`。
  - Gradle IntelliJ Platform 插件升级到 `2.16.0`，`intellijIdea("2026.1")`。
  - Gradle Wrapper 升级到 `9.5.1`（使用腾讯云镜像）。
  - 编译要求由 JDK 17 升级到 **JDK 21**。
  - `prepareSandbox` 增加冲突 jar 清理逻辑（kotlin-stdlib / kotlin-reflect / kotlinx-* 等），并自动复制 `product-info.json` 到 sandbox 根目录。
  - 在 plugin.xml 中新增 `depends org.jetbrains.kotlin` 与 `supportsKotlinPluginMode supportsK2="true"`，正式支持 K2 模式。
- 新增 GitHub Actions Workflow `gradle-publish-jdk21-2026.yml`，针对 `v0.2.*` tag 进行自动构建与发布；调整 Gradle Action 版本与 Gradle 版本（9.5）。

### Jupyter / Kernel 体系重构

- 重写 `KotlinReplWrapper`，配合新版 kotlin-jupyter-kernel `0.19.0` 完成 REPL 接入。
- 新增 `cn.hylstudio.skykoma.plugin.idea.jupyter.PatchedCompilerServiceProvider` 与 `ContextClassLoaderAwareScriptClassGetter`，修复在 IDEA 内嵌运行时的 ClassLoader 与脚本 class 解析问题。
- 新增 `cn.hylstudio.skykoma.plugin.idea.util.SelfFirstClassLoader`，为 Jupyter Kernel 提供"self-first"的类加载策略（详见 `docs/classloader-design.md`）。
- 注册为 Jupyter Kernel 的命令、Kernel 启动逻辑迁移到 `IdeaPluginAgentServer` / `AgentHttpApiVerticle`。

### Python 环境自动化（新功能）

- 新增 `PythonEnvService` / `PythonEnvServiceImpl` 应用级服务，提供：
  - 自动探测系统中的 Python 3.12 (`findPython312`)。
  - 在指定路径自动创建并初始化 venv (`initVenv`)。
  - 通过 pip（可配置镜像）安装预设依赖（jupyterlab、kotlin-jupyter-kernel、jupyterlab-lsp、jupyter-collaboration、jupyter-kernel-client、jupyter-nbmodel-client，以及 `run_kotlin_kernel_idea`、自定义 `jupyter_client`）。
  - 检测 venv 是否已初始化、获取 venv 内 python 可执行文件路径。
  - 启动/停止 Jupyter Lab 子进程，支持 `--ip` / `--allow-root` / `--IdentityProvider.token` / `notebook-dir`。
- 新增配置常量：`PYTHON312_EXECUTABLE`、`PYTHON_VENV_PATH`（默认 `~/.skykoma/venv`）、`PYTHON_DOWNLOAD_URL`、`PYTHON_PIP_PACKAGES`、`PYTHON_PIP_MIRROR`（默认清华源）、`JUPYTER_LAB_IP`、`JUPYTER_LAB_ALLOW_ROOT`、`JUPYTER_LAB_TOKEN`、`JUPYTER_LAB_WORKDIR`（默认 `~/.skykoma/notebooks`）。
- 在 plugin.xml 注册 `PythonEnvService` 应用级服务。

### Skykoma Tool Window（右侧面板）改造

- `SkykomaToolWindowFactory` 大幅重写：
  - 所有分组（Agent Server / Python Environment / Jupyter / Project Structure）改为可折叠 `TitledSeparator`，折叠状态持久化到 `PropertiesComponent`。
  - 表单字段（Python 3.12 路径、Venv 路径、Kernel Python、Notebook 目录等）改为 `TextFieldWithBrowseButton` 并在编辑时自动保存（`bindSaveOnChange`）。
  - 操作按钮统一替换为 IDEA 图标工具栏（`AnActionButton` + `ActionToolbar`），覆盖 start / stop / restart / refresh / initVenv / registerKernel / startLab / stopKernelAndLab。
  - `initVenv` 使用 `AtomicReference` 状态标记，避免重复触发；执行过程实时输出到 Skykoma Console。
  - Jupyter Lab 由插件直接拉起，进程引用通过 `jupyterLabProcessRef` 管理，停止时调用 `killProcessTree` 递归终止。
  - Agent Server / Kernel 状态由后台定时任务每 1s 刷新一次。
  - 移除旧的"showCmd / hideCmd"行内命令编辑器（保留 API 但 UI 不再展示）。

### 新增 Skykoma Console（底部面板）

- 新增 `SkykomaConsoleService` / `SkykomaConsoleServiceImpl` 与 `SkykomaConsoleToolWindowFactory`。
- 在 plugin.xml 注册 `Skykoma Console` 底部 ToolWindow，用于聚合输出 Python 环境初始化、Kernel 注册、Jupyter Lab 等子进程日志。
- 后续 commit 进一步把 console 拆分为独立标签页（如 `appendLabInfo` / `appendLabError` / `clearLab`），同时为 console 工具栏注入 IDEA 自带的 wordWrap、scrollToEnd、clear 三个按钮。

### Classpath 编辑能力增强

- 设置面板 `IdeaPluginSettingsDialog` 中，"Kernel Extra Classpath" 由单行文本框升级为 `JBTable + ToolbarDecorator`：
  - 支持通过 JAR 文件选择对话框新增条目。
  - 支持双击/编辑按钮修改条目。
  - 持久化时使用 `File.pathSeparator` 拼接。
- "Venv Path"、"Work Dir" 字段升级为 `TextFieldWithBrowseButton`，可直接选择目录。

### 新增 Skykoma Classpath ToolWindow（已暂存，未提交）

> 已 `git add` 但尚未 commit，列在此处供参考，**当前 changelog 范围按用户要求不包含未提交内容**，如需包含可单独追加。

### 文档

- 新增 `docs/classloader-design.md`：详细描述 Skykoma Kernel 在 IDEA 进程内运行时的类加载策略与冲突规避方案。
- README 编译说明由 JDK 17 更新为 JDK 21。

## 完整 Commit 列表

| commit | 摘要 |
| --- | --- |
| `694169c` | update to 2026.1（核心：平台 + 构建 + Kernel 体系重构 + classloader 设计文档） |
| `54aca42` | update README.md（JDK 17 -> JDK 21） |
| `55e5313` | auto init python venv（新增 PythonEnvService、venv/pip/Jupyter Lab 自动化、配置项扩充） |
| `c7ec634` | add jupyter panel（新增 SkykomaConsoleService、Console ToolWindow、JupyterLab 启动配置） |
| `7515728` | add jupyter panel（workflow 调整） |
| `5baa51e` | update gradle（新增 `gradle-publish-jdk21-2026.yml` workflow） |
| `de89d2a` | update gradle |
| `1615608` | update gradle |
| `45226e7` | update gradle |
| `da07523` | update gradle |
| `2084c31` | support edit classpath（设置面板 Classpath 表格化编辑） |
| `7a97605` | support split console / support icon btns（ToolWindow 折叠分组、图标工具栏、Lab 进程独立日志） |
| `fad7248` | update icon（更新 ToolWindow 图标，新增 classpath class/file 图标） |
