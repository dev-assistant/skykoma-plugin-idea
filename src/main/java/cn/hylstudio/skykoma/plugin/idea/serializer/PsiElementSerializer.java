package cn.hylstudio.skykoma.plugin.idea.serializer;

import cn.hylstudio.skykoma.plugin.idea.util.PsiUtils;
import com.google.gson.*;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.util.List;

public class PsiElementSerializer extends BasePsiSerializer implements JsonSerializer<PsiElement> {
    private static final Logger LOGGER = Logger.getInstance(PsiElementSerializer.class);

    @Override
    public JsonElement serialize(PsiElement psiElement, Type type, JsonSerializationContext jsonSerializationContext) {
        PsiFile containingFile = psiElement.getContainingFile();
        return serializeRecursively(containingFile, psiElement, jsonSerializationContext);
    }

    private JsonObject serializeRecursively(PsiFile currentFile, PsiElement psiElement, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonObject = new JsonObject();
        //填充基本信息
        fillBasicInfo(currentFile, psiElement, jsonObject);
        //对当前的文件序列化子节点
        PsiFile containingFile = psiElement.getContainingFile();
        JsonArray childElementsJsonArray = new JsonArray();
        if (currentFile.equals(containingFile)) {
            PsiElement[] childElements = psiElement.getChildren();
            for (PsiElement childElement : childElements) {
                try {
                    childElementsJsonArray.add(serializeRecursively(currentFile, childElement, jsonSerializationContext));
                } catch (Exception e) {
                    e.printStackTrace();
                    LOGGER.error(String.format("psiElement serialize error, e = [%s]", e.getMessage()), e);
                }
            }
        }
        jsonObject.add("childElements", childElementsJsonArray);
        //序列化特定的节点
        if (psiElement instanceof PsiClass) {
            jsonObject.addProperty("psiType", "Class");
            processPsiClass(currentFile, (PsiClass) psiElement, jsonSerializationContext, jsonObject);
        } else if (psiElement instanceof PsiAnnotation) {
            jsonObject.addProperty("psiType", "Annotation");
            processPsiAnnotation(currentFile, (PsiAnnotation) psiElement, jsonSerializationContext, jsonObject);
        }else{
            jsonObject.addProperty("psiType", "unknown");
        }
        return jsonObject;

    }
    private void processPsiAnnotation(PsiFile currentFile, PsiAnnotation annotation, JsonSerializationContext jsonSerializationContext, JsonObject jsonObject) {
        String qualifiedName = annotation.getQualifiedName();
        jsonObject.addProperty("qualifiedName", qualifiedName);
        List<JvmAnnotationAttribute> jvmAnnotationAttributes = annotation.getAttributes();
        //解析注解的属性
        JsonArray attributesArr = new JsonArray();
        for (JvmAnnotationAttribute attr : jvmAnnotationAttributes) {
            String attributeName = attr.getAttributeName();
            PsiAnnotationMemberValue valueProperty = annotation.findAttributeValue(attributeName);
            List<String> tableNameValues = PsiUtils.parseAnnotationValue(valueProperty);
            JsonArray valuesArr = new JsonArray();
            for (String value : tableNameValues) {
                valuesArr.add(value);
            }
            JsonObject attrObj = new JsonObject();
            attrObj.addProperty("name", attributeName);
            attrObj.add("values", valuesArr);
            attributesArr.add(attrObj);
        }
        jsonObject.add("attributes", attributesArr);
        //尝试解析注解的类
        PsiClass psiClass = annotation.resolveAnnotationType();
        JsonObject annotationClassObj = new JsonObject();
        if (psiClass != null) {
            annotationClassObj = serializeRecursively(currentFile, psiClass, jsonSerializationContext);
        }
        jsonObject.add("annotationClass", annotationClassObj);
    }

    private void processPsiClass(PsiFile currentFile, PsiClass psiClass, JsonSerializationContext jsonSerializationContext, JsonObject jsonObject) {
        String qualifiedName = psiClass.getQualifiedName();
        if (StringUtils.isEmpty(qualifiedName)) {
            jsonObject.addProperty("qualifiedName", "unknown");
            return;
        }
        jsonObject.addProperty("qualifiedName", qualifiedName);
        boolean isInterface = psiClass.isInterface();
        PsiElementFactory factory = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory();
        PsiType psiType = factory.createType(psiClass);
        jsonObject.addProperty("canonicalText", psiType.getCanonicalText());
        jsonObject.addProperty("isInterface", isInterface);
        PsiClassType[] superTypes = psiClass.getSuperTypes();
        JsonArray superTypeCanonicalTextsArr = new JsonArray();
        for (PsiClassType superType : superTypes) {
            String canonicalText = superType.getCanonicalText();
            superTypeCanonicalTextsArr.add(canonicalText);
        }
        jsonObject.add("superTypeCanonicalTexts", superTypeCanonicalTextsArr);
        if (isInterface) {
            PsiClassType[] extendsListTypes = psiClass.getExtendsListTypes();
            // 获取继承的接口类型信息
            JsonArray extendsClassListArr = new JsonArray();
            JsonArray extendsCanonicalTextsListArr = new JsonArray();
            JsonArray extendsSuperTypeCanonicalTextsListArr = new JsonArray();
            extractInterfaceInfo(currentFile, jsonSerializationContext,
                    extendsClassListArr,
                    extendsCanonicalTextsListArr,
                    extendsSuperTypeCanonicalTextsListArr,
                    extendsListTypes);
            jsonObject.add("extendsClassList", extendsClassListArr);
            jsonObject.add("extendsCanonicalTextsList", extendsCanonicalTextsListArr);
            jsonObject.add("extendsSuperTypeCanonicalTextsList", extendsSuperTypeCanonicalTextsListArr);
        } else {
            // 获取父类
            JsonObject superClassObj = new JsonObject();
            PsiClass superClass = psiClass.getSuperClass();
            if (superClass != null) {
                superClassObj = serializeRecursively(currentFile, superClass, jsonSerializationContext);
            }
            jsonObject.add("superClass", superClassObj);
            // 获取实现的接口类型
            PsiClassType[] implementedInterfaces = psiClass.getImplementsListTypes();
            JsonArray implementsListArr = new JsonArray();
            JsonArray implementsCanonicalTextsListArr = new JsonArray();
            JsonArray implementsSuperTypeCanonicalTextsListArr = new JsonArray();
            extractInterfaceInfo(currentFile, jsonSerializationContext,
                    implementsListArr,
                    implementsCanonicalTextsListArr,
                    implementsSuperTypeCanonicalTextsListArr,
                    implementedInterfaces);
            jsonObject.add("implementsList", implementsListArr);
            jsonObject.add("implementsCanonicalTextsList", implementsCanonicalTextsListArr);
            jsonObject.add("implementsSuperTypeCanonicalTextsList", implementsSuperTypeCanonicalTextsListArr);
        }
    }

    private void extractInterfaceInfo(PsiFile currentFile,
                                      JsonSerializationContext jsonSerializationContext,
                                      JsonArray typeListArr,
                                      JsonArray typeCanonicalTextsListArr,
                                      JsonArray superTypeCanonicalTextsListArr,
                                      PsiClassType[] types) {
        for (PsiClassType type : types) {
            String canonicalText = type.getCanonicalText();
            typeCanonicalTextsListArr.add(canonicalText);
            PsiType[] superTypes = type.getSuperTypes();
            for (PsiType superType : superTypes) {
                String superTypeCanonicalText = superType.getCanonicalText();
                superTypeCanonicalTextsListArr.add(superTypeCanonicalText);
            }
            PsiClass interfaceClass = type.resolve();
            if (interfaceClass != null) {
                JsonObject classJsonObj = serializeRecursively(currentFile, interfaceClass, jsonSerializationContext);
                typeListArr.add(classJsonObj);
            }
        }
    }
}