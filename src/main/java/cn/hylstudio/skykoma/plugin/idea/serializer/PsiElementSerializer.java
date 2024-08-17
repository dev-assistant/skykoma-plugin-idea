package cn.hylstudio.skykoma.plugin.idea.serializer;

import cn.hylstudio.skykoma.plugin.idea.util.PsiUtils;
import com.google.gson.*;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class PsiElementSerializer extends BasePsiSerializer implements JsonSerializer<PsiElement> {
    private static final Logger LOGGER = Logger.getInstance(PsiElementSerializer.class);

    @Override
    public JsonElement serialize(PsiElement psiElement, Type type, JsonSerializationContext jsonSerializationContext) {
        PsiFile containingFile = psiElement.getContainingFile();
        Set<Object> emptyVisited = new HashSet<>();
        return serializeRecursively(containingFile, psiElement, jsonSerializationContext,1,0, emptyVisited);
    }

    private JsonObject serializeRecursively(PsiFile currentFile, PsiElement psiElement,
                                            JsonSerializationContext jsonSerializationContext,
                                            int elemDepth, int propDepth,
                                            Set<Object> visited) {
        JsonObject jsonObject = new JsonObject();
        if (psiElement.getContainingFile() != currentFile) {
            jsonObject.addProperty("value", "out of file");
            return jsonObject;
        }
        //填充基本信息
        fillBasicInfo(currentFile, psiElement, jsonObject);
        //对当前的文件序列化子节点
        PsiFile containingFile = psiElement.getContainingFile();
        Class<? extends PsiElement> clazz = psiElement.getClass();
        jsonObject.addProperty("className", clazz.getName());
        JsonObject props = getPsiElementProps(clazz, psiElement, jsonSerializationContext, elemDepth, propDepth+1, visited);
        jsonObject.add("props", props);
        JsonArray childElementsJsonArray = new JsonArray();
        if (currentFile.equals(containingFile)) {
            PsiElement[] childElements = psiElement.getChildren();
            for (PsiElement childElement : childElements) {
                try {
                    childElementsJsonArray.add(serializeRecursively(currentFile, childElement, jsonSerializationContext, elemDepth + 1, propDepth, visited));
                } catch (Exception e) {
                    e.printStackTrace();
                    LOGGER.error(String.format("psiElement serialize error, e = [%s]", e.getMessage()), e);
                }
            }
        }
        jsonObject.add("childElements", childElementsJsonArray);
        //序列化特定的节点
//        if (psiElement instanceof PsiClass) {
//            jsonObject.addProperty("psiType", "Class");
//            processPsiClass(currentFile, (PsiClass) psiElement, jsonSerializationContext, jsonObject);
//        } else if (psiElement instanceof PsiAnnotation) {
//            jsonObject.addProperty("psiType", "Annotation");
//            processPsiAnnotation(currentFile, (PsiAnnotation) psiElement, jsonSerializationContext, jsonObject);
//        } else if (psiElement instanceof PsiField) {
//            jsonObject.addProperty("psiType", "Field");
//            processPsiField(currentFile, (PsiField) psiElement, jsonSerializationContext, jsonObject);
//        } else if (psiElement instanceof PsiIdentifier) {
//            jsonObject.addProperty("psiType", "Identifier");
//            processPsiIdentifier(currentFile, (PsiIdentifier) psiElement, jsonSerializationContext, jsonObject);
//        } else if (psiElement instanceof PsiExpression) {
//            jsonObject.addProperty("psiType", "Expression");
//            processPsiExpression(currentFile, (PsiExpression) psiElement, jsonSerializationContext, jsonObject);
//        } else {
//            jsonObject.addProperty("psiType", "unknown");
//        }
        return jsonObject;
    }

    private JsonObject getPsiElementProps(Class<?> clazz, PsiElement obj, JsonSerializationContext jsonSerializationContext,
                                          int elemDepth, int propDepth, Set<Object> visited) {
        JsonObject props = new JsonObject();
//        Method[] methods = clazz.getMethods();
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            Method accessibleMethod = MethodUtils.getAccessibleMethod(method);
            if (accessibleMethod == null) {
//                props.addProperty(methodName, "props error: ignore not accessible");
                continue;
            }
            int parameterCount = method.getParameterCount();
            if (parameterCount > 0) {
                props.addProperty(methodName, "props error: ignore has params");
                continue;
            }
            try {
                Object result = method.invoke(obj);
                JsonElement jsonElement = convertToJsonElement(obj.getContainingFile(), result, jsonSerializationContext, elemDepth, propDepth, visited);
                props.add(methodName, jsonElement);
            } catch (Throwable e) {
                props.addProperty(methodName, "props error:" + e.getMessage());
                LOGGER.error(String.format("gen props error, obj = [%s], e = [%s]", obj, e.getMessage()));
            }
        }
        return props;
    }

    private JsonElement convertToJsonElement(PsiFile containingFile, Object obj, JsonSerializationContext jsonSerializationContext,
                                             int elementDepth, int propDepth, Set<Object> visited) {
        if (visited.contains(obj)) {
            return new JsonPrimitive("omit by visited");
        }
        visited.add(obj);
        if (propDepth > 10) {
            return new JsonPrimitive("omit by propDepth");
        }
        if (obj == null) {
            return JsonNull.INSTANCE;
        } else if (obj.getClass().isArray()) {
            JsonArray jsonArray = new JsonArray();
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                Object o = Array.get(obj, i);
                jsonArray.add(convertToJsonElement(containingFile, o, jsonSerializationContext, elementDepth, propDepth + 1, visited));
            }
            return jsonArray;
        } else if (obj instanceof Collection) {
            JsonArray jsonArray = new JsonArray();
            for (Object item : (Collection<?>) obj) {
                jsonArray.add(convertToJsonElement(containingFile, item, jsonSerializationContext, elementDepth, propDepth + 1, visited));
            }
            return jsonArray;
        } else if (obj instanceof Number || obj instanceof Boolean || obj instanceof Character) {
            return new JsonPrimitive(String.valueOf(obj));
        } else if (obj instanceof String) {
            return new JsonPrimitive((String) obj);
        } else if (obj instanceof PsiElement) {
            return serializeRecursively(containingFile, (PsiElement) obj, jsonSerializationContext, elementDepth + 1, propDepth + 1, visited);
//            JsonObject jsonObject = new JsonObject();
//            jsonObject.addProperty("className", clazz.getName());
//            JsonObject props = getPsiElementProps(clazz, obj, jsonSerializationContext);
//            jsonObject.add("props", props);
//            return jsonObject;
        } else {
            return new JsonPrimitive("unknown element:" + String.valueOf(obj));
        }
    }

//
//    private void processPsiClass(PsiFile currentFile, PsiClass psiClass, JsonSerializationContext jsonSerializationContext, JsonObject jsonObject) {
//        String qualifiedName = psiClass.getQualifiedName();
//        if (StringUtils.isEmpty(qualifiedName)) {
//            jsonObject.addProperty("qualifiedName", "unknown");
//            return;
//        }
//        jsonObject.addProperty("qualifiedName", qualifiedName);
//        boolean isInterface = psiClass.isInterface();
//        PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
//        String canonicalText = qualifiedName;
//        if (typeParameters.length != 0) {
//            String genericType = Arrays.stream(typeParameters).map(PsiElement::getText).collect(Collectors.joining(",", "<", ">"));
//            canonicalText = qualifiedName + genericType;
//        }
//        jsonObject.addProperty("canonicalText", canonicalText);
//        jsonObject.addProperty("isInterface", isInterface);
//        JsonArray superTypeClassListArr = new JsonArray();
//        JsonArray superTypeCanonicalTextsArr = new JsonArray();
//        JsonArray superTypeSuperTypeCanonicalTextsArr = new JsonArray();
//        PsiClassType[] superTypes = psiClass.getSuperTypes();
//        extractTypesInfo(currentFile, jsonSerializationContext,
//                superTypeClassListArr,
//                superTypeCanonicalTextsArr,
//                superTypeSuperTypeCanonicalTextsArr,
//                superTypes);
//        jsonObject.add("superTypeClassList", superTypeClassListArr);
//        jsonObject.add("superTypeCanonicalTextsList", superTypeCanonicalTextsArr);
//        jsonObject.add("superTypeSuperTypeCanonicalTextsList", superTypeSuperTypeCanonicalTextsArr);
//        if (isInterface) {
//            PsiClassType[] extendsListTypes = psiClass.getExtendsListTypes();
//            // 获取继承的接口类型信息
//            JsonArray extendsClassListArr = new JsonArray();
//            JsonArray extendsCanonicalTextsListArr = new JsonArray();
//            JsonArray extendsSuperTypeCanonicalTextsListArr = new JsonArray();
//            extractTypesInfo(currentFile, jsonSerializationContext,
//                    extendsClassListArr,
//                    extendsCanonicalTextsListArr,
//                    extendsSuperTypeCanonicalTextsListArr,
//                    extendsListTypes);
//            jsonObject.add("extendsClassList", extendsClassListArr);
//            jsonObject.add("extendsCanonicalTextsList", extendsCanonicalTextsListArr);
//            jsonObject.add("extendsSuperTypeCanonicalTextsList", extendsSuperTypeCanonicalTextsListArr);
//        } else {
//            // 获取父类
//            JsonObject superClassObj = new JsonObject();
//            PsiClass superClass = psiClass.getSuperClass();
//            if (superClass != null) {
//                superClassObj = serializeRecursively(currentFile, superClass, jsonSerializationContext);
//            }
//            jsonObject.add("superClass", superClassObj);
//            // 获取实现的接口类型
//            PsiClassType[] implementedInterfaces = psiClass.getImplementsListTypes();
//            JsonArray implementsListArr = new JsonArray();
//            JsonArray implementsCanonicalTextsListArr = new JsonArray();
//            JsonArray implementsSuperTypeCanonicalTextsListArr = new JsonArray();
//            extractTypesInfo(currentFile, jsonSerializationContext,
//                    implementsListArr,
//                    implementsCanonicalTextsListArr,
//                    implementsSuperTypeCanonicalTextsListArr,
//                    implementedInterfaces);
//            jsonObject.add("implementsList", implementsListArr);
//            jsonObject.add("implementsCanonicalTextsList", implementsCanonicalTextsListArr);
//            jsonObject.add("implementsSuperTypeCanonicalTextsList", implementsSuperTypeCanonicalTextsListArr);
//        }
//    }
//
//    private void processPsiAnnotation(PsiFile currentFile, PsiAnnotation annotation, JsonSerializationContext jsonSerializationContext, JsonObject jsonObject) {
//        String qualifiedName = annotation.getQualifiedName();
//        jsonObject.addProperty("qualifiedName", qualifiedName);
//        List<JvmAnnotationAttribute> jvmAnnotationAttributes = annotation.getAttributes();
//        //解析注解的属性
//        JsonArray attributesArr = new JsonArray();
//        for (JvmAnnotationAttribute attr : jvmAnnotationAttributes) {
//            String attributeName = attr.getAttributeName();
//            PsiAnnotationMemberValue valueProperty = annotation.findAttributeValue(attributeName);
//            List<String> tableNameValues = PsiUtils.parseAnnotationValue(valueProperty);
//            JsonArray valuesArr = new JsonArray();
//            for (String value : tableNameValues) {
//                valuesArr.add(value);
//            }
//            JsonObject attrObj = new JsonObject();
//            attrObj.addProperty("name", attributeName);
//            attrObj.add("values", valuesArr);
//            attributesArr.add(attrObj);
//        }
//        jsonObject.add("attributes", attributesArr);
//        //尝试解析注解的类
//        PsiClass psiClass = annotation.resolveAnnotationType();
//        JsonObject annotationClassObj = new JsonObject();
//        if (psiClass != null) {
//            annotationClassObj = serializeRecursively(currentFile, psiClass, jsonSerializationContext);
//        }
//        jsonObject.add("annotationClass", annotationClassObj);
//    }
//
//    private void processPsiField(PsiFile currentFile, PsiField psiField, JsonSerializationContext jsonSerializationContext, JsonObject jsonObject) {
//        processPsiVariable(currentFile, psiField, jsonSerializationContext, jsonObject);
//    }
//
//    private void processPsiVariable(PsiFile currentFile, PsiVariable psiVariable, JsonSerializationContext jsonSerializationContext, JsonObject jsonObject) {
//        //type info
//        PsiType type = psiVariable.getType();
//        String canonicalText = type.getCanonicalText();
//        jsonObject.addProperty("canonicalText", canonicalText);
//        JsonObject classInfo = new JsonObject();
//        PsiClass fieldClassType = PsiTypesUtil.getPsiClass(type);
//        boolean isClass = fieldClassType != null;
//        jsonObject.addProperty("isClass", isClass);
//        if (isClass) {
//            classInfo = serializeRecursively(currentFile, fieldClassType, jsonSerializationContext);
//        }
//        jsonObject.add("classInfo", classInfo);
//        //identifier info
//        PsiIdentifier nameIdentifier = psiVariable.getNameIdentifier();
//        JsonObject nameIdentifierObj = serializeRecursively(currentFile, nameIdentifier, jsonSerializationContext);
//        jsonObject.add("nameIdentifier", nameIdentifierObj);
//        jsonObject.addProperty("variableName", psiVariable.getName());
//        //initializer info
//        boolean hasInitializer = psiVariable.hasInitializer();
//        jsonObject.addProperty("hasInitializer", hasInitializer);
//        JsonObject initializerObj = new JsonObject();
//        if (hasInitializer) {
//            PsiExpression initializer = psiVariable.getInitializer();
//            initializerObj = serializeRecursively(currentFile, initializer, jsonSerializationContext);
//        }
//        jsonObject.add("initializer", initializerObj);
//    }
//
//    private void processPsiIdentifier(PsiFile currentFile, PsiIdentifier psiIdentifier, JsonSerializationContext jsonSerializationContext, JsonObject jsonObject) {
//        PsiElement context = psiIdentifier.getContext();
//        boolean hasContext = context != null;
//        jsonObject.addProperty("hasContext", hasContext);
//        int contextHash = 0;
//        if (hasContext) {
//            contextHash = context.hashCode();
//        }
//        jsonObject.addProperty("contextHash", contextHash);
//        int identifierHash = psiIdentifier.hashCode();
//        jsonObject.addProperty("identifierHash", identifierHash);
//    }
//
//    private void processPsiExpression(PsiFile currentFile, PsiExpression psiElement, JsonSerializationContext jsonSerializationContext, JsonObject jsonObject) {
//
//    }
//
//    private void extractTypesInfo(PsiFile currentFile,
//                                  JsonSerializationContext jsonSerializationContext,
//                                  JsonArray typeListArr,
//                                  JsonArray typeCanonicalTextsListArr,
//                                  JsonArray superTypeCanonicalTextsListArr,
//                                  PsiClassType[] types) {
//        for (PsiClassType type : types) {
//            String canonicalText = type.getCanonicalText();
//            typeCanonicalTextsListArr.add(canonicalText);
//            PsiType[] superTypes = type.getSuperTypes();
//            for (PsiType superType : superTypes) {
//                String superTypeCanonicalText = superType.getCanonicalText();
//                superTypeCanonicalTextsListArr.add(superTypeCanonicalText);
//            }
//            PsiClass interfaceClass = type.resolve();
//            if (interfaceClass != null) {
//                JsonObject classJsonObj = serializeRecursively(currentFile, interfaceClass, jsonSerializationContext);
//                typeListArr.add(classJsonObj);
//            }
//        }
//    }
}