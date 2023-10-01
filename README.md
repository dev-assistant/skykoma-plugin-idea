# Skykoma Plugin For IDEA
尝试尽可能减少人工对IDE的重复操作
- 1.模板代码自动生成
- 2.获取当前项目语义并上报

## 功能说明
### 1.模板代码自动生成
模板代码生成主要依赖于LiveTemplate实现，分为静态生成和动态生成，静态生成是上下文无关的，动态生成可感知代码上下文。

#### 1.1.静态生成
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

#### 动态生成
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

### 2.获取当前项目语义并上报
##### 2.1.项目信息
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

##### 2.2.语法信息
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

##### 2.3.语义信息
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
### 3.作为jupyter kernel注册
#### 3.1.安装jupyter kotlin kernel
```
# 安装jupyterlab
pip install jupyterlab kotlin-jupyter-kernel
# 清华镜像加速安装jupyterlab
pip install -i https://pypi.tuna.tsinghua.edu.cn/simple jupyterlab kotlin-jupyter-kernel
```
#### 3.2.修改run_kernel.py
路径是`site-packages\run_kotlin_kernel\run_kernel.py`，修改部分为`#for skykoma-agent-idea begin`到`#for skykoma-agent-idea end`
```python
import json
import os
import shlex
import subprocess
import sys
from kotlin_kernel import env_names
from kotlin_kernel import port_generator


def run_kernel(*args):
    try:
        run_kernel_impl(*args)
    except KeyboardInterrupt:
        print('Kernel interrupted')
        try:
            sys.exit(130)
        except SystemExit:
            os._exit(130)


def module_install_path():
    abspath = os.path.abspath(__file__)
    current_dir = os.path.dirname(abspath)
    return str(current_dir)


def run_kernel_impl(connection_file, jar_args_file=None, executables_dir=None):
    abspath = os.path.abspath(__file__)
    current_dir = os.path.dirname(abspath)
    if jar_args_file is None:
        jar_args_file = os.path.join(current_dir, 'config', 'jar_args.json')
    if executables_dir is None:
        executables_dir = current_dir
    jars_dir = os.path.join(executables_dir, 'jars')
    with open(jar_args_file, 'r') as fd:
        jar_args_json = json.load(fd)
        debug_port = jar_args_json['debuggerPort']
        cp = jar_args_json['classPath']
        main_jar = jar_args_json['mainJar']
        debug_list = []
        if debug_port is not None and debug_port != '':
            if debug_port == 'generate':
                debug_port = port_generator.get_port_not_in_use(port_generator
                    .DEFAULT_DEBUG_PORT)
            debug_list.append(
                '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address={}'
                .format(debug_port))
        else:
            debug_port = None
        class_path_arg = os.pathsep.join([os.path.join(jars_dir, jar_name) for
            jar_name in cp])
        main_jar_path = os.path.join(jars_dir, main_jar)
        java_exec = os.getenv(env_names.KERNEL_JAVA_EXECUTABLE)
        java_home = os.getenv(env_names.KERNEL_JAVA_HOME) or os.getenv(
            env_names.JAVA_HOME)
        if java_exec is not None:
            java = java_exec
        elif java_home is not None:
            java = os.path.join(java_home, 'bin', 'java')
        else:
            java = 'java'
        jvm_arg_str = os.getenv(env_names.KERNEL_JAVA_OPTS) or os.getenv(
            env_names.JAVA_OPTS) or ''
        extra_args = os.getenv(env_names.KERNEL_EXTRA_JAVA_OPTS)
        if extra_args is not None:
            jvm_arg_str += ' ' + extra_args
        kernel_args = os.getenv(env_names.KERNEL_INTERNAL_ADDED_JAVA_OPTS)
        if kernel_args is not None:
            jvm_arg_str += ' ' + kernel_args
        jvm_args = shlex.split(jvm_arg_str)
        jar_args = [main_jar_path, '-classpath=' + class_path_arg,
            connection_file, '-home=' + executables_dir]
        if debug_port is not None:
            jar_args.append('-debugPort=' + str(debug_port))
        #for skykoma-agent-idea begin
        skykoma_agent_idea = 'idea' in os.getenv('SKYKOMA_AGENT_TYPE', '')
        if skykoma_agent_idea:
            import requests
            import time
            skykoma_agent_server_api = os.getenv('SKYKOMA_AGENT_SERVER_API')
            payload = json.dumps(jar_args[1:])
            print('launch skykoma agent idea jupyter repl server, api: {}, args: {}'.format(skykoma_agent_server_api, payload))
            headers = {"Content-Type": "application/json"}
            response = requests.post(skykoma_agent_server_api, data=payload, headers=headers)
            print(response.status_code)
            print(response.json())
            while True:
                time.sleep(10000)  #avoid current process exit
        else:
            print([java] + jvm_args + ['-jar'] + debug_list + jar_args)  #for debug only
            subprocess.call([java] + jvm_args + ['-jar'] + debug_list + jar_args)
        # subprocess.call([java] + jvm_args + ['-jar'] + debug_list + jar_args)
        #for skykoma-agent-idea end


if __name__ == '__main__':
    run_kernel(*sys.argv[1:])
```
#### 3.3.启动jupyterlab
```
jupyter lab
```
