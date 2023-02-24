# Skykoma Plugin For IDEA
尝试尽可能少的减少人工对IDE的重复操作

## skykoma-plugin-idea
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
日志：寻找上下文中可用的LOGGER，若LOGGER不存在则尝试先生成LOGGER字段，并根据当前上下文插入LOGGER语句。

SQL：寻找上下文中的ORM注解信息，根据字段类型生成默认的建表语句。

数据复制：根据上下文获取指定类的所有属性，复制到当前上下文中或指定的对象中，代替动态的反射copy避免运行时异常。

Service：增强自带的创建类过程，根据类名猜测正确的包名，一键生成带注解的Service类和接口并实现对应接口满足主流项目的风格要求，减少人工操作。