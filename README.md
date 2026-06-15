- [Skykoma Plugin For IDEA](#skykoma-plugin-for-idea)
  - [1.作为jupyter kernel注册](#1作为jupyter-kernel注册)
    - [1.1.功能概述](#11功能概述)
    - [1.2.推荐方式：在 Skykoma ToolWindow 中一键初始化](#12推荐方式在-skykoma-toolwindow-中一键初始化)
      - [1.2.1.Agent Server](#121agent-server)
      - [1.2.2.Python Environment](#122python-environment)
      - [1.2.3.Jupyter](#123jupyter)
      - [1.2.4.Skykoma Console](#124skykoma-console)
    - [1.3.可在设置中调整的 Jupyter 配置项](#13可在设置中调整的-jupyter-配置项)
    - [1.4.手动安装 jupyter kotlin kernel（兼容旧流程）](#14手动安装-jupyter-kotlin-kernel兼容旧流程)
    - [1.5.手动启动 jupyterlab](#15手动启动-jupyterlab)
    - [1.6.类加载与 Extra Classpath](#16类加载与-extra-classpath)
  - [2.模板代码自动生成](#2模板代码自动生成)
    - [2.1.静态生成](#21静态生成)
    - [2.2.动态生成](#22动态生成)
  - [3.获取当前项目语义并上报](#3获取当前项目语义并上报)
      - [3.1.项目信息](#31项目信息)
    - [3.2.语法信息](#32语法信息)
    - [3.3.语义信息](#33语义信息)
  - [编译](#编译)
# Skykoma Plugin For IDEA

尝试尽可能减少人工对IDE的重复操作
- 1.支持将idea作为jupyter-kotlin-kernel注册
- 2.模板代码自动生成
- 3.获取当前项目语义并上报
## 1.作为jupyter kernel注册

把运行中的 IDEA 实例本身暴露为 Kotlin Jupyter Kernel，结合插件内置的 Python 环境管理与 Jupyter Lab 启动器，可以做到“打开 IDEA 即可获得一个能直接访问当前工程类与 PSI 的 Jupyter 环境”。

### 1.1.功能概述

- 将 IDEA 进程作为 Kotlin Jupyter Kernel 注册（kernel 名可在配置中修改），由插件内置的 `IdeaPluginAgentServer` 通过 HTTP/ZMQ 与外部 Jupyter 客户端通信。
- 内置 `PythonEnvService`：自动探测系统 Python 3.12、创建独立 venv、按可配置的 pip 镜像安装运行所需依赖（jupyterlab、kotlin-jupyter-kernel、jupyterlab-lsp、jupyter-collaboration、jupyter-kernel-client、jupyter-nbmodel-client，以及 fork 版的 `run_kotlin_kernel_idea`、`jupyter_client`）。
- 一键启动/停止 Jupyter Lab 子进程，输出实时打印到底部 `Skykoma Console` 面板。
- 通过 `SelfFirstClassLoader` + `PatchedCompilerServiceProvider` + `ContextClassLoaderAwareScriptClassGetter` 解决 IDEA 进程内嵌 Kernel 时的 ClassLoader 冲突，使 K2 模式下也能稳定运行。
- 支持以表格形式编辑 Kernel 的 Extra Classpath，把项目编译产物或依赖 jar 暴露给 Notebook。

> 编译/运行要求：JDK 21（与 IDEA 2026.1 一致），目标 IDEA 版本 >= 2026.1。

### 1.2.推荐方式：在 Skykoma ToolWindow 中一键初始化

打开右侧 `Skykoma` ToolWindow，从上到下依次是 4 个可折叠分组（折叠状态会被持久化）：`Agent Server` / `Python Environment` / `Jupyter` / `Project Structure`。所有路径输入框均带原生文件选择器，并在编辑后即时保存。

#### 1.2.1.Agent Server

- 状态条显示 `RUNNING / STOPPED`，每秒自动刷新。
- 工具栏按钮：`Start` / `Stop` / `Restart` / `Refresh`，对应 `IdeaPluginAgentServer` 的生命周期。
- Agent Server 是 Kernel 与外部 Jupyter 通信的服务端，必须先启动。

#### 1.2.2.Python Environment

- `Python 3.12`：选择 Python 3.12 解释器路径。点击工具栏 `Refresh` 可自动从 PATH/常见位置探测；探测失败时通知中会附带可下载链接（`PYTHON_DOWNLOAD_URL`）。
- `Venv Path`：插件管理的虚拟环境目录，默认 `~/.skykoma/venv`。
- `Venv Status`：显示当前 venv 是否已经初始化。
- 工具栏 `Init Venv`：
  1. 校验 Python 3.12 路径，必要时自动探测；
  2. 创建 venv；
  3. 使用配置的 pip 镜像（默认清华源）安装预设依赖；
  4. 安装完成后将 `Kernel Python` 自动指向 venv 内的 python。
- 同一时间只允许一个 `Init Venv` 任务运行，重复点击会被忽略并提示；全过程实时输出到 `Skykoma Console`。

#### 1.2.3.Jupyter

- `Kernel Python`：注册 Kernel 时使用的 python 可执行文件，默认指向 venv 内的 python。
- `Notebook Dir`：Jupyter Lab 的工作目录，默认 `~/.skykoma/notebooks`。
- `Kernel Status`：显示 Kernel 是否已注册并连接，每秒刷新。
- 工具栏按钮：
  - `Register Kernel`：调用 `registerAsJupyterKernel` 写入 kernel.json，使外部 Jupyter Lab 能发现并连上 IDEA 内的 Kernel。
  - `Start Lab`：使用 venv 中的 jupyter 启动 `jupyter lab`，自动透传 `JUPYTER_LAB_IP` / `JUPYTER_LAB_ALLOW_ROOT` / `JUPYTER_LAB_TOKEN` / `Notebook Dir`，输出写入 Console 的 Lab 标签页。
  - `Stop Kernel`：停止 Kernel 并递归 kill 之前启动的 Lab 进程（`killProcessTree`）。
  - `Refresh`：手动刷新状态。

#### 1.2.4.Skykoma Console

底部 ToolWindow `Skykoma Console` 聚合 venv 初始化日志、kernel 注册日志、Jupyter Lab 进程的 stdout/stderr。工具栏自带 `Word Wrap` / `Scroll to End` / `Clear` 三个按钮，便于查看长输出。

### 1.3.可在设置中调整的 Jupyter 配置项

`Settings | Tools | Skykoma Plugin` 中可以修改的关键项：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| Python 3.12 | `python` 或环境变量 `SKYKOMA_JUPYTER_PYTHON_EXECUTABLE` | 用于创建 venv 的解释器 |
| Venv Path | `~/.skykoma/venv` | 插件管理的 venv 目录 |
| Python Download URL | `https://www.python.org/downloads/release/python-3129/` | 自动探测失败时提示下载的链接 |
| Pip Packages | 见下方默认列表 | 多行字符串，按行解析后传给 `pip install` |
| Pip Mirror | `https://pypi.tuna.tsinghua.edu.cn/simple` | 不需要镜像可清空 |
| Kernel Python | venv 内 python | 注册到 kernel.json 的 python 可执行 |
| Kernel Name | 默认值 | 注册到 Jupyter 的 kernel 名 |
| Kernel Extra Classpath | 空 | 表格化编辑，每一行一个 jar/目录，持久化时按 `File.pathSeparator` 拼接 |
| Kernel Hb/Shell/Iopub/Stdin/Control Port | `2334-2338` | Kernel ZMQ 端口 |
| Jupyter Lab IP | `127.0.0.1` | 启动 Lab 的 `--ip` |
| Jupyter Lab Allow Root | `true` | 启动 Lab 的 `--allow-root` |
| Jupyter Lab Token | 空 | 启动 Lab 的 `--IdentityProvider.token` |
| Jupyter Lab Workdir | `~/.skykoma/notebooks` | 启动 Lab 的 `--notebook-dir` |

默认 pip 包列表：

```
jupyterlab==4.5.8
kotlin-jupyter-kernel==0.19.0.944
jupyterlab-lsp==5.3.0
jupyter-collaboration==4.4.1
jupyter-kernel-client==0.9.0
jupyter-nbmodel-client==0.14.8
git+https://github.com/956237586/run_kotlin_kernel_idea.git@v0.2
git+https://github.com/956237586/jupyter_client.git@v8.9.2
```

### 1.4.手动安装 jupyter kotlin kernel（兼容旧流程）

如果不希望使用插件内置的 venv 流程，可以按下面步骤手动准备 Python 环境：

```bash
# 安装jupyterlab
# virtualenv skykoma
conda create -n skykoma python=3.12 -y
SKYKOMA_PYTHON_HOME=$CONDA_HOME/envs/skykoma
SKYKOMA_PYTHON_BIN=$SKYKOMA_PYTHON_HOME/bin
$SKYKOMA_PYTHON_BIN/python -m pip install jupyterlab==4.5.8 kotlin-jupyter-kernel==0.19.0.944 jupyterlab-lsp==5.3.0 jupyter-collaboration==4.4.1 jupyter-kernel-client==0.9.0 jupyter-nbmodel-client==0.14.8 git+https://github.com/956237586/run_kotlin_kernel_idea.git@v0.2 git+https://github.com/956237586/jupyter_client.git@v8.9.2
# 清华镜像加速安装jupyterlab选项
-i https://pypi.tuna.tsinghua.edu.cn/simple
```

随后在设置中把 `Kernel Python` 指向上述环境的 python，再点击 ToolWindow 的 `Register Kernel`。

### 1.5.手动启动 jupyterlab（兼容旧流程）
```
#default cmd
$SKYKOMA_PYTHON_BIN/jupyter lab
#set ip
$SKYKOMA_PYTHON_BIN/jupyter lab --ip=0.0.0.0 
#allow root
$SKYKOMA_PYTHON_BIN/jupyter lab --ip=0.0.0.0 --allow-root
#using jupyter mcp client
$SKYKOMA_PYTHON_BIN/jupyter lab --IdentityProvider.token=MY_TOKEN --ip=0.0.0.0 --allow-root
```

如果通过 ToolWindow 的 `Start Lab` 启动，则上述参数会从设置中读取并自动拼接，无需手动维护命令行。

demo见[这个文件](./demo/demo.ipynb)

### 1.6.类加载与 Extra Classpath

由于 Kernel 直接运行在 IDEA JVM 中，存在 IDE 自身、Kotlin 插件、jupyter-kernel 三方 jar 之间的类冲突。插件采用如下策略解决：

- `SelfFirstClassLoader`：Kernel 运行所在的类加载器优先加载自身捆绑的 kotlin-compiler / kotlin-jupyter-kernel 等依赖，避免落入 IDEA 平台 ClassLoader。
- `PatchedCompilerServiceProvider`：补丁版的脚本编译服务发现，绕开 ServiceLoader 在沙箱中的可见性限制。
- `ContextClassLoaderAwareScriptClassGetter`：执行用户脚本时按上下文 ClassLoader 解析脚本类，保证 cell 之间符号可见。
- 详细方案见 [`docs/classloader-design.md`](./docs/classloader-design.md)。

如果希望在 Notebook 中直接 `import` 当前项目代码，可在设置面板的 `Kernel Extra Classpath` 表格中追加：

- 项目编译输出（如 `target/classes`、`build/classes/java/main`）；
- maven/gradle 解析出的依赖 jar；
- 任意外部 jar。

支持通过工具栏 “+” 按钮选择文件（多选生效），双击或点击编辑按钮可修改条目；持久化时按 `File.pathSeparator` 拼接，运行期由 Kernel 加载器整体注入。

## 2.模板代码自动生成
模板代码生成主要依赖于LiveTemplate实现，分为静态生成和动态生成，静态生成是上下文无关的，动态生成可感知代码上下文。

### 2.1.静态生成
如@Autowire @Column 之类的，使用idea自带API完成命名风格的转换。所有触发词可自行到设置中修改。
- ORM列补全
```
//触发词cl
@Column(name = "`$COLUMN1$`")
private $TYPE$ $COLUMN$;
```
- 声明logger
```
//触发词lg
private static final Logger LOGGER = LoggerFactory.getLogger($CLASS$.class);
```
- 复制属性
```
//触发词vc
this.$FIELDS$ = v.get$GETTER$(); $END$
```
- 声明注入字段
```
//触发词wf
@Autowired
private $type$ $field$;
```
- 静态String
```
//触发词psfs
private static final String $field$ = "$value$";
```
- 默认ORM列
```
//触发词dcl
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "id")
private Integer id;

$END$

@Column(name = "created_at")
private Timestamp createdAt;

@Column(name = "updated_at", insertable = false, updatable = false)
private Timestamp updatedAt;

@PrePersist
public void prePersist() {
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    createdAt = timestamp;
    updatedAt = timestamp;
}

@PreUpdate
public void preUpdate() {
    updatedAt = new Timestamp(System.currentTimeMillis());
}

```
- HTTP请求
```
//触发词postjson
String url = $URL$;

Map<String, String> payload = new HashMap<>(2);
$PAYLOAD$
String responseJson = okHttpService.postJsonBody(url, gson.toJson(payload));
if (StringUtils.isEmpty(responseJson)) {
    LOGGER.error("$OP$ failed, response is empty");
    return null;
}
$RESPONSE$ respone = null;
try {
    respone = gson.fromJson(responseJson, $RESPONSE$.class);
} catch (Exception e) {
    LOGGER.error("$OP$  failed, response format error1, data = [{}]", responseJson);
    return null;
}
if (respone == null) {
    LOGGER.error("$OP$ failed, response format error2, data = [{}]", responseJson);
    return null;
}
String code = respone.getCode();
if (code == null) {
    LOGGER.error("$OP$ failed, response format error3, code missing, data = [{}]", responseJson);
    return null;
}
```

### 2.2.动态生成
- 日志

打印日志记录当前方法参数和局部变量，如果是Controller层则自动记录uid
```
//触发词lgm
LOGGER.info("blablabla, a = [{}], b = [{}]", a, b)
```
- 数据复制

根据上下文获取第一个参数的所有属性，复制到当前上下文中或指定的对象中，代替动态的反射copy避免运行时异常。
```
//触发词vca
public SomeConstructor(SomeOtherClass v) {
    // ------------- generated by skykoma begin -------------
    this.field1 = v.getField1();
    this.field2 = v.getField2();
    this.field3 = v.getField3();
    this.field4 = v.getField4();
    this.field5 = v.getField5();
    // ------------- generated by skykoma end -------------
}

public void someMethod(SomeOtherClass v){
    // ------------- generated by skykoma begin -------------
    ThisClass thisClass = this;
    thisClass.setField1(v.getField1());
    thisClass.setField2(v.getField2());
    thisClass.setField3(v.getField3());
    thisClass.setField4(v.getField4());
    thisClass.setField5(v.getField5());
    // ------------- generated by skykoma end -------------
}
```
- SQL

寻找上下文中的ORM注解信息，根据字段类型生成默认的建表语句。
```
//触发词sqlc
// create table `table_name` (
//   `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
//   `big_int` bigint(20) NOT NULL,
//   `some_string` varchar(255) NOT NULL DEFAULT '',
//   `some_int` int(11) NOT NULL,
//   `some_boolean` tinyint(1) NOT NULL,
//   `created_at` timestamp NOT NULL DEFAULT '2000-01-01 00:00:00',
//   `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
//   PRIMARY KEY (`id`)
// ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
```
//TODO
Service：增强自带的创建类过程，根据类名猜测正确的包名，一键生成带注解的Service类和接口并实现对应接口满足主流项目的风格要求，减少人工操作。

## 3.获取当前项目语义并上报
#### 3.1.项目信息
根据当前项目自动识别文件树，并追踪文件变化
- 文件树
- 源码、测试源码、资源、测试资源识别
```json
{
    "scanId": "340bc77b232a4fa599b819743725e160",
    "projectInfoDto": {
        "key": "realworld-mdd",
        "name": "realworld-mdd",
        "vcsEntityDto": {
            "vcsType": "git",
            "name": "realworld-mdd",
            "path": "D:\\code\\realworld-mdd"
        },
        "rootFolder": {
            "name": "realworld-mdd",
            "type": "folder",
            "relativePath": "",
            "absolutePath": "D:\\code\\realworld-mdd",
            "subFiles": [
                //omit mid dirs
                {
                    "name": "config",
                    "type": "folder",
                    "relativePath": "src\\main\\java\\cn\\hylstudio\\mdse\\demo\\realworld\\config",
                    "absolutePath": "D:\\code\\realworld-mdd\\src\\main\\java\\cn\\hylstudio\\mdse\\demo\\realworld\\config",
                    "subFiles": [
                        {
                            "name": "interceptor",
                            "type": "folder",
                            "relativePath": "src\\main\\java\\cn\\hylstudio\\mdse\\demo\\realworld\\config\\interceptor",
                            "absolutePath": "D:\\code\\realworld-mdd\\src\\main\\java\\cn\\hylstudio\\mdse\\demo\\realworld\\config\\interceptor",
                            "subFiles": [
                                {
                                    "name": "AuthInterceptor.java",
                                    "type": "file",
                                    "relativePath": "src\\main\\java\\cn\\hylstudio\\mdse\\demo\\realworld\\config\\interceptor\\AuthInterceptor.java",
                                    "absolutePath": "D:\\code\\realworld-mdd\\src\\main\\java\\cn\\hylstudio\\mdse\\demo\\realworld\\config\\interceptor\\AuthInterceptor.java",
                                    "psiFileJson": "jsonContent .......",
                                    "subFiles": []
                                },
                                {
                                    "name": "PublicInterceptor.java",
                                    "type": "file",
                                    "relativePath": "src\\main\\java\\cn\\hylstudio\\mdse\\demo\\realworld\\config\\interceptor\\PublicInterceptor.java",
                                    "absolutePath": "D:\\code\\realworld-mdd\\src\\main\\java\\cn\\hylstudio\\mdse\\demo\\realworld\\config\\interceptor\\PublicInterceptor.java",
                                    "psiFileJson": "jsonContent .......",
                                    "subFiles": []
                                }
                            ]
                        },
                        {
                            "name": "WebConfig.java",
                            "type": "file",
                            "relativePath": "src\\main\\java\\cn\\hylstudio\\mdse\\demo\\realworld\\config\\WebConfig.java",
                            "absolutePath": "D:\\code\\realworld-mdd\\src\\main\\java\\cn\\hylstudio\\mdse\\demo\\realworld\\config\\WebConfig.java",
                            "psiFileJson": "jsonContent .......",
                            "subFiles": []
                        }
                    ]
                }
            ]
        },
        "modules": [
            {
                "name": "realworld",
                "roots": [
                    {
                        "type": "src",
                        "folders": [
                            {
                                "name": "java",
                                "type": "folder",
                                "relativePath": "src-gen\\main\\java",
                                "absolutePath": "D:\\code\\realworld-mdd\\src-gen\\main\\java",
                                "subFiles": []
                            },
                            {
                                "name": "java",
                                "type": "folder",
                                "relativePath": "src\\main\\java",
                                "absolutePath": "D:\\code\\realworld-mdd\\src\\main\\java",
                                "subFiles": []
                            }
                        ]
                    },
                    {
                        "type": "testSrc",
                        "folders": [
                            {
                                "name": "java",
                                "type": "folder",
                                "relativePath": "src\\test\\java",
                                "absolutePath": "D:\\code\\realworld-mdd\\src\\test\\java",
                                "subFiles": []
                            }
                        ]
                    },
                    {
                        "type": "resources",
                        "folders": [
                            {
                                "name": "resources",
                                "type": "folder",
                                "relativePath": "src\\main\\resources",
                                "absolutePath": "D:\\code\\realworld-mdd\\src\\main\\resources",
                                "subFiles": []
                            }
                        ]
                    },
                    {
                        "type": "testResources",
                        "folders": [ //omit
                        ]
                    }
                ]
            }
        ],
        "scanId": "340bc77b232a4fa599b819743725e160",
        "lastScanTs": 1680358223404
    }
}
```

### 3.2.语法信息
根据PsiFile获取PsiElement的树状结构，如：
```json
{
    "originText": "package cn.hylstudio.mdse.demo.realworld.controller.user;",
    "startOffset": 0,
    "endOffset": 57,
    "className": "com.intellij.psi.impl.source.tree.java.PsiPackageStatementImpl",
    "childElements": [
        {
            "originText": "package",
            "startOffset": 0,
            "endOffset": 7,
            "className": "com.intellij.psi.impl.source.tree.java.PsiKeywordImpl",
            "childElements": []
        },
        {
            "originText": " ",
            "startOffset": 7,
            "endOffset": 8,
            "className": "com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl",
            "childElements": []
        },
        {
            "originText": "cn.hylstudio.mdse.demo.realworld.controller.user",
            "startOffset": 8,
            "endOffset": 56,
            "className": "com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl",
            "childElements": [ omit ]
        },
        {
            "originText": ";",
            "startOffset": 56,
            "endOffset": 57,
            "className": "com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl",
            "childElements": []
        }
    ]
}
```
### 3.3.语义信息
根据PsiElement不同的节点类型，会附加不同的语义信息。包括：
- 类之间的继承关系
- 接口之间继承关系
- 类和接口的实现关系
```json
{
    "originText": "omit",
    "startOffset": 859,
    "endOffset": 1564,
    "className": "com.intellij.psi.impl.source.PsiClassImpl",
    "childElements": [omit],
    "qualifiedName": "cn.hylstudio.mdse.demo.realworld.controller.user.UserController",
    "isInterface": false,
    "superTypeCanonicalTexts": [
        "cn.hylstudio.mdse.demo.realworld.controller.BaseController"
    ],
    "implementsList": [],
    "implementsCanonicalTextsList": [],
    "implementsSuperTypeCanonicalTextsList": [],
    "superClass": {
        "originText": "",
        "startOffset": 0,
        "endOffset": 0,
        "className": "com.intellij.psi.impl.source.PsiClassImpl",
        "childElements": [],
        "qualifiedName": "cn.hylstudio.mdse.demo.realworld.controller.BaseController",
        "isInterface": false,
        "superTypeCanonicalTexts": [
            "java.lang.Object"
        ],
        "implementsList": [],
        "implementsCanonicalTextsList": [],
        "implementsSuperTypeCanonicalTextsList": [],
        "superClass": {
            "originText": "",
            "startOffset": 0,
            "endOffset": 0,
            "className": "com.intellij.psi.impl.compiled.ClsClassImpl",
            "childElements": [],
            "qualifiedName": "java.lang.Object",
            "isInterface": false,
            "superTypeCanonicalTexts": [],
            "implementsList": [],
            "implementsCanonicalTextsList": [],
            "implementsSuperTypeCanonicalTextsList": [],
            "superClass": {}
        }
    }
}
```

```json
{
    "originText": "public interface RealWorldUserRepo extends JpaRepository<RealWorldUser, Integer> {\n    RealWorldUser findByEmailAndPassword(String loginEmail, String passwordHash);\n}",
    "startOffset": 185,
    "endOffset": 351,
    "className": "com.intellij.psi.impl.source.PsiClassImpl",
    "childElements": [omit        ],
    "qualifiedName": "cn.hylstudio.mdse.demo.realworld.repo.mysql.RealWorldUserRepo",
    "isInterface": true,
    "superTypeCanonicalTexts": [
        "org.springframework.data.jpa.repository.JpaRepository<cn.hylstudio.mdse.demo.realworld.entity.mysql.RealWorldUser,java.lang.Integer>"
    ],
    "extendsClassList": [
        {
            "originText": "",
            "startOffset": 0,
            "endOffset": 0,
            "className": "com.intellij.psi.impl.compiled.ClsClassImpl",
            "childElements": [],
            "qualifiedName": "org.springframework.data.jpa.repository.JpaRepository",
            "isInterface": true,
            "superTypeCanonicalTexts": [
                "org.springframework.data.repository.PagingAndSortingRepository<T,ID>",
                "org.springframework.data.repository.query.QueryByExampleExecutor<T>"
            ],
            "extendsClassList": [
                {
                    "originText": "",
                    "startOffset": 0,
                    "endOffset": 0,
                    "className": "com.intellij.psi.impl.compiled.ClsClassImpl",
                    "childElements": [],
                    "qualifiedName": "org.springframework.data.repository.PagingAndSortingRepository",
                    "isInterface": true,
                    "superTypeCanonicalTexts": [
                        "org.springframework.data.repository.CrudRepository<T,ID>"
                    ],
                    "extendsClassList": [
                        {
                            "originText": "",
                            "startOffset": 0,
                            "endOffset": 0,
                            "className": "com.intellij.psi.impl.compiled.ClsClassImpl",
                            "childElements": [],
                            "qualifiedName": "org.springframework.data.repository.CrudRepository",
                            "isInterface": true,
                            "superTypeCanonicalTexts": [
                                "org.springframework.data.repository.Repository<T,ID>"
                            ],
                            "extendsClassList": [
                                {
                                    "originText": "",
                                    "startOffset": 0,
                                    "endOffset": 0,
                                    "className": "com.intellij.psi.impl.compiled.ClsClassImpl",
                                    "childElements": [],
                                    "qualifiedName": "org.springframework.data.repository.Repository",
                                    "isInterface": true,
                                    "superTypeCanonicalTexts": [
                                        "java.lang.Object"
                                    ],
                                    "extendsClassList": [],
                                    "extendsCanonicalTextsList": [],
                                    "extendsSuperTypeCanonicalTextsList": []
                                }
                            ],
                            "extendsCanonicalTextsList": [
                                "org.springframework.data.repository.Repository<T,ID>"
                            ],
                            "extendsSuperTypeCanonicalTextsList": [
                                "java.lang.Object"
                            ]
                        }
                    ],
                    "extendsCanonicalTextsList": [
                        "org.springframework.data.repository.CrudRepository<T,ID>"
                    ],
                    "extendsSuperTypeCanonicalTextsList": [
                        "org.springframework.data.repository.Repository<T,ID>"
                    ]
                },
                {
                    "originText": "",
                    "startOffset": 0,
                    "endOffset": 0,
                    "className": "com.intellij.psi.impl.compiled.ClsClassImpl",
                    "childElements": [],
                    "qualifiedName": "org.springframework.data.repository.query.QueryByExampleExecutor",
                    "isInterface": true,
                    "superTypeCanonicalTexts": [
                        "java.lang.Object"
                    ],
                    "extendsClassList": [],
                    "extendsCanonicalTextsList": [],
                    "extendsSuperTypeCanonicalTextsList": []
                }
            ],
            "extendsCanonicalTextsList": [
                "org.springframework.data.repository.PagingAndSortingRepository<T,ID>",
                "org.springframework.data.repository.query.QueryByExampleExecutor<T>"
            ],
            "extendsSuperTypeCanonicalTextsList": [
                "org.springframework.data.repository.CrudRepository<T,ID>",
                "java.lang.Object"
            ]
        }
    ],
    "extendsCanonicalTextsList": [
        "org.springframework.data.jpa.repository.JpaRepository<cn.hylstudio.mdse.demo.realworld.entity.mysql.RealWorldUser,java.lang.Integer>"
    ],
    "extendsSuperTypeCanonicalTextsList": [
        "org.springframework.data.repository.PagingAndSortingRepository<cn.hylstudio.mdse.demo.realworld.entity.mysql.RealWorldUser,java.lang.Integer>",
        "org.springframework.data.repository.query.QueryByExampleExecutor<cn.hylstudio.mdse.demo.realworld.entity.mysql.RealWorldUser>"
    ]
}
```
## 开发
### 编译
需要使用jdk21运行gradlew，目标 IDE 平台为 IntelliJ IDEA 2026.1（`sinceBuild=242`）。

`./gradlew buildPlugin -PprojVersion=VERSION`

构建过程中 `prepareSandbox` 会自动剔除与 IDEA 平台冲突的 kotlin-stdlib / kotlin-reflect / kotlinx-* jar，并把 `product-info.json` 复制到 sandbox 根目录。
