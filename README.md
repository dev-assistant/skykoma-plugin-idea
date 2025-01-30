# Skykoma Plugin For IDEA
尝试尽可能减少人工对IDE的重复操作
- 1.模板代码自动生成
- 2.获取当前项目语义并上报
- 3.支持将idea作为jupyter-kotlin-kernel注册
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
```bash
# 安装jupyterlab
# virtualenv skykoma
conda create -n skykoma python=3.12 -y
SKYKOMA_PYTHON_HOME=$CONDA_HOME/envs/skykoma
SKYKOMA_PYTHON_BIN=$SKYKOMA_PYTHON_HOME/bin
$SKYKOMA_PYTHON_BIN/python -m pip install jupyterlab kotlin-jupyter-kernel jupyterlab-lsp git+https://github.com/956237586/jupyter_client.git@v8.4.3
# 清华镜像加速安装jupyterlab选项
-i https://pypi.tuna.tsinghua.edu.cn/simple
```
#### 3.2.修改run_kernel.py
路径是`site-packages\run_kotlin_kernel\run_kernel.py`，修改部分为`#for skykoma-agent-idea begin`到`#for skykoma-agent-idea end`
```
RUN_KOTLIN_KERNEL_DIR=$SKYKOMA_PYTHON_HOME/lib/python3.8/site-packages/run_kotlin_kernel
mv $RUN_KOTLIN_KERNEL_DIR/run_kernel.py $RUN_KOTLIN_KERNEL_DIR/run_kernel.py.bak
wget -O $RUN_KOTLIN_KERNEL_DIR/run_kernel.py https://raw.githubusercontent.com/956237586/kotlin-jupyter/ideav0.0.1/distrib/run_kotlin_kernel/run_kernel.py
```
#### 3.3.启动jupyterlab
```
#default cmd
$SKYKOMA_PYTHON_BIN/jupyter lab
#set ip
$SKYKOMA_PYTHON_BIN/jupyter lab --ip=0.0.0.0 
#allow root
$SKYKOMA_PYTHON_BIN/jupyter lab --ip=0.0.0.0 --allow-root 
```
## 编译
需要使用jdk17运行gradlew

`./gradlew buildPlugin -PprojVersion=VERSION`