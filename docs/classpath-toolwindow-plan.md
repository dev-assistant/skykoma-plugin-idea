# Skykoma Classpath Tool Window — Implementation Plan

## 目标

kernel 运行后，在 IDEA 中提供一个实时查看 script classpath 的 Tool Window，支持：
- **文件视图**：按来源（System CP / Plugin CP / Extra CP）组织，展示 JAR 和目录，类似 External Libraries 视图
- **包视图**：按 Java 包名组织，展示包下的类
- **类视图**：按类名搜索定位，点击后打开临时编辑器查看反编译的 Java 文件，同时展示类结构（方法/字段）

参考现有 IDE 的 External Libraries、Structure 和文件树设计，**优先复用 IntelliJ Platform 已有组件**。

---

## 背景分析

### 当前 classpath 构建流程

`KotlinReplWrapper.main()` (line 87-147) 从 4 个来源构建 classpath：

| 来源 | 代码位置 | 说明 |
|------|---------|------|
| System CP | `System.getProperty("java.class.path")` | JVM 进程的完整 classpath |
| System CL CP | `getSystemClassPath()` → `classpathFromClassloader(systemClassLoader)` | 系统 ClassLoader 的 classpath |
| Plugin CP | `scriptCompilationClasspathFromContextOrStdlib(wholeClasspath=true, classLoader=pluginClassLoader)` | 插件 ClassLoader 的编译 classpath |
| Kotlin Std JARs | `KotlinJars.kotlinScriptStandardJars` | kotlin-stdlib, kotlin-script-runtime |
| Extra CP | `PropertiesComponent.getValue(JUPYTER_EXTRA_CLASSPATH)` | 用户配置的额外 classpath |

最终合并去重后传入 `KotlinKernelOwnParams.scriptClasspath`。

### 当前问题

1. `scriptClassPath` 是 `main()` 中的局部变量，从未存储或暴露
2. `messageHandler` / `repl` 是 `runServer()` 中的局部变量，外部无法访问
3. 没有任何 API 可以在运行时查询 classpath 或已加载的类
4. `AgentHttpApiVerticle` 只有 3 个端点

---

## 实现步骤

### Step 1: 暴露 kernel classpath

**文件：`KotlinReplWrapper.kt`**

- 添加 `@Volatile var scriptClassPath: List<File> = emptyList()` 字段
- 在 `main()` 中 line 113 之后赋值

**文件：`IdeaPluginAgentServer.java`**

- 添加接口方法：`List<String> getScriptClasspath();`

**文件：`IdeaPluginAgentServerImpl.java`**

- 实现 `getScriptClasspath()`

**文件：`AgentHttpApiVerticle.java`**

- 添加端点：`GET /getScriptClasspath`

---

### Step 2: 利用 IntelliJ VFS 访问 JAR 内容（替代手动扫描）

**不需要**自己写 `java.util.jar.JarFile` 扫描逻辑。IntelliJ Platform 提供了完整的 VFS 层：

#### JarFileSystem — JAR 文件的虚拟文件系统

```java
VirtualFile jarRoot = JarFileSystem.getInstance().getRootByLocal(new File("/path/to/lib.jar"));
VirtualFile[] children = jarRoot.getChildren();  // 顶层包/目录
VirtualFile classFile = jarRoot.findFileByRelativePath("com/example/MyClass.class");
```

#### VirtualFile 关键方法

| 方法 | 用途 |
|------|------|
| `getChildren()` | 获取目录/包下的所有条目 |
| `findChild(name)` | 按名称查找直接子项 |
| `findFileByRelativePath(path)` | 按相对路径查找嵌套条目 |
| `isDirectory()` | 判断是否为包/目录 |
| `getExtension()` | 获取文件扩展名（"class" 表示类文件） |
| `getFileSystem()` | 返回 JarFileSystem 或 LocalFileSystem 实例 |

#### VfsUtilCore 递归遍历

```java
VfsUtilCore.visitChildrenRecursively(jarRoot, new VirtualFileVisitor<Void>() {
    @Override
    public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory() && "class".equals(file.getExtension())) {
            // 处理 .class 文件
        }
        return true;
    }
});
```

**结论：** 不需要 `ClasspathScanService` 做 JAR 扫描，直接用 VFS 按需懒加载。`AbstractTreeBuilder` 天然支持按需展开。

---

### Step 3: 使用 AbstractTreeStructure + AbstractTreeBuilder 构建树

IntelliJ 的 Project View / External Libraries 都基于这套架构：

| 组件 | 职责 |
|------|------|
| `AbstractTreeStructure` | 定义树层级：root、children、parent。纯数据逻辑 |
| `AbstractTreeBuilder` | 异步构建树，管理展开/折叠/懒加载 |
| `Tree` (`com.intellij.ui.treeStructure.Tree`) | IntelliJ 增强版 JTree |
| `NodeDescriptor` | 每个树节点的描述符，设置 presentation |

#### 文件视图的树结构

```
Root (不可见)
├── System ClassPath (分类节点)
│   ├── /path/to/rt.jar (JAR 节点)
│   │   ├── java/ (包节点, VirtualFile)
│   │   │   ├── util/ (包节点, VirtualFile)
│   │   │   │   ├── ArrayList.class (类节点, VirtualFile)
│   │   │   │   └── HashMap.class
│   │   │   └── ...
│   │   └── ...
│   └── /path/to/tools.jar
├── Plugin ClassPath
│   └── ...
└── Extra ClassPath
    └── ...
```

#### 包视图的树结构

```
Root (不可见)
├── java.util (包节点)
│   ├── ArrayList.class (类节点)
│   └── HashMap.class
├── com.google.common.collect
│   └── ...
└── ...
```

#### 节点数据对象

```java
class FileViewNode {
    enum Type { CATEGORY, JAR, DIRECTORY, PACKAGE, CLASS }
    Type type;
    String name;
    VirtualFile vFile;
    File sourceFile;
    String categoryLabel;
}

class PackageViewNode {
    String packageName;
    List<VirtualFile> classes;
}
```

---

### Step 4: 利用 IDEA 内置反编译器打开类文件

```java
VirtualFile classFile = jarRoot.findFileByRelativePath("com/example/MyClass.class");
FileEditorManager.getInstance(project).openFile(classFile, true);
```

IDEA 内置 FernFlower 自动反编译，效果与 External Libraries 中点击类完全一致。

---

### Step 5: 利用 PSI 获取类结构

```java
VirtualFile classFile = ...;
PsiFile psiFile = PsiManager.getInstance(project).findFile(classFile);
if (psiFile instanceof PsiClassOwner) {
    PsiClass[] classes = ((PsiClassOwner) psiFile).getClasses();
    for (PsiClass cls : classes) {
        PsiMethod[] methods = cls.getMethods();
        PsiField[] fields = cls.getFields();
        PsiClass[] innerClasses = cls.getInnerClasses();
    }
}
```

---

### Step 6: Tool Window 布局

#### Tab 1: File View

```
┌──────────────────────────────────────────┐
│ [Refresh] [SearchTextField]              │ ← Toolbar
├──────────────────────────────────────────┤
│ Tree (AbstractTreeBuilder)               │
│  📁 System ClassPath                     │
│    📦 rt.jar                             │
│  📁 Plugin ClassPath                     │
│    📦 kotlin-stdlib.jar                  │
│  📁 Extra ClassPath                      │
│    📁 /path/to/classes/                  │
│    📦 mylib.jar                          │
└──────────────────────────────────────────┘
```

#### Tab 2: Package View

```
┌──────────────────────────────────────────┐
│ [Refresh] [SearchTextField]              │ ← Toolbar
├──────────────────────────────────────────┤
│ Tree (AbstractTreeBuilder)               │
│  📦 java.util                            │
│    📄 ArrayList                          │
│    📄 HashMap                            │
│  📦 com.google.common.collect            │
│    📄 ImmutableList                      │
└──────────────────────────────────────────┘
```

#### Tab 3: Class View

```
┌──────────────────────────────────────────┐
│ [Refresh] [SearchTextField]              │ ← Toolbar
├────────────────────┬─────────────────────┤
│ JBList (类列表)     │ Tree (结构预览)      │
│  + ListSpeedSearch │  AbstractTreeBuilder │
│  + 实时过滤         │  + PsiClass 结构     │
│                    │                     │
│  双击 → 打开反编译   │  双击 → 打开反编译    │
└────────────────────┴─────────────────────┘
         ↑ JBSplitter 分割
```

---

### Step 7: 注册到 plugin.xml

```xml
<toolWindow id="Skykoma Classpath" anchor="right" 
    icon="/skykoma-tool-window-icon.svg"
    factoryClass="cn.hylstudio.skykoma.plugin.idea.toolwindow.SkykomaClasspathToolWindowFactory"/>
```

---

### Step 8: 刷新机制

- Toolbar "Refresh" 按钮：获取最新 classpath → 重建数据模型 → `queueUpdate()` 刷新树
- 自动刷新：kernel 状态变为 RUNNING 时触发初始加载
- 后台线程获取 classpath，`invokeLater` 更新 UI

---

## 文件清单

### 新建文件

| 文件路径 | 用途 |
|---------|------|
| `toolwindow/SkykomaClasspathToolWindowFactory.java` | Tool Window 工厂 |
| `toolwindow/ui/ClasspathFileTreePanel.java` | 文件视图面板 |
| `toolwindow/ui/ClasspathPackageTreePanel.java` | 包视图面板 |
| `toolwindow/ui/ClassSearchPanel.java` | 类搜索+结构预览面板 |
| `toolwindow/model/FileViewNode.java` | 文件视图节点数据对象 |
| `toolwindow/model/PackageViewNode.java` | 包视图节点数据对象 |
| `toolwindow/structure/ClasspathFileTreeStructure.java` | 文件视图 AbstractTreeStructure |
| `toolwindow/structure/ClasspathPackageTreeStructure.java` | 包视图 AbstractTreeStructure |
| `toolwindow/structure/ClassStructureTreeStructure.java` | 类结构预览 AbstractTreeStructure |

### 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `KotlinReplWrapper.kt` | 存储 scriptClassPath，添加 getter |
| `service/IdeaPluginAgentServer.java` | 添加 getScriptClasspath() |
| `service/impl/IdeaPluginAgentServerImpl.java` | 实现 getScriptClasspath() |
| `service/verticle/AgentHttpApiVerticle.java` | 添加 /getScriptClasspath 端点 |
| `META-INF/plugin.xml` | 注册新 Tool Window |

---

## 关键设计决策

1. **复用 VFS 而非手动扫描** — 使用 JarFileSystem + VirtualFile.getChildren() 访问 JAR 内容
2. **复用反编译器而非自定义预览** — FileEditorManager.openFile() 自动触发 FernFlower
3. **复用 PSI 获取结构而非 ASM** — PsiManager.findFile() + PsiClass.getMethods()/getFields()
4. **使用 AbstractTreeStructure + AbstractTreeBuilder** — 与 External Libraries 相同架构
5. **线程安全** — scriptClassPath 标记 @Volatile，树构建由 AbstractTreeBuilder 后台执行
6. **UI 风格** — SimpleToolWindowPanel、JBSplitter、SearchTextField、TreeSpeedSearch
