package cn.hylstudio.skykoma.plugin.idea;

@AnnotationTest(
        value = "constValue",
        values = {"constValue"},
        enumValue = TestEnum.a,
        enumValues = {TestEnum.a},
        annotationValue = @Annotation2(value = "123")
)
public class Test {
    public void test() {
        String a = "!123";
    }

}

@interface Annotation2 {
    String value();
}
@interface AnnotationTest {
    String value();
    String[] values();
    TestEnum enumValue();
    TestEnum[] enumValues();
    Annotation2 annotationValue();
//    Object[] selfType();
}
enum TestEnum {
    a,b,c
}
