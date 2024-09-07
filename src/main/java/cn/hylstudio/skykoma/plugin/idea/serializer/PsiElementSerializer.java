package cn.hylstudio.skykoma.plugin.idea.serializer;

import cn.hylstudio.skykoma.plugin.idea.util.GsonUtils;
import cn.hylstudio.skykoma.plugin.idea.util.PsiUtils;
import com.google.common.collect.Sets;
import com.google.gson.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jetbrains.deft.Obj;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class PsiElementSerializer extends BasePsiSerializer implements JsonSerializer<PsiElement> {
    private static final Logger LOGGER = Logger.getInstance(PsiElementSerializer.class);

    @Override
    public JsonElement serialize(PsiElement psiElement, Type type, JsonSerializationContext jsonSerializationContext) {
        PsiFile containingFile = psiElement.getContainingFile();
        Set<Object> visited = new HashSet<>();
        return serializeRecursively(containingFile, psiElement, jsonSerializationContext, 1, 1, visited);
    }

    private static JsonObject serializeRecursively(PsiFile currentFile, PsiElement psiElement,
                                            JsonSerializationContext jsonSerializationContext,
                                            int elemDepth, int propDepth,
                                            Set<Object> visited) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("gsonType", "PsiElement");
        jsonObject.addProperty("elemDepth", elemDepth);
        jsonObject.addProperty("propDepth", propDepth);
        if (visited.contains(psiElement)) {
            addError(jsonObject,"cycle detected");
            return jsonObject;
        }
        visited.add(psiElement);
        int lineNumber = PsiUtils.getLineNumber(psiElement);
        String currentFileName = currentFile.getName();
        boolean needBreak = fillBasicInfo(currentFile, psiElement, jsonObject, elemDepth, propDepth);
        LOGGER.info(String.format("processing filePath = [%s], lineNum = [%s], elemDepth = [%s], propDepth = [%s], needBreak = [%s]",
                currentFileName, lineNumber, elemDepth, propDepth, needBreak));
        boolean hasProps = !needBreak;
        jsonObject.addProperty("hasProps", hasProps);
        if (needBreak) {
            return jsonObject;
        }
        // Serialize properties
        JsonObject props = getPsiElementProps(psiElement, jsonSerializationContext, elemDepth, propDepth, visited);
        jsonObject.add("props", props);
        JsonArray childElementsJsonArray = new JsonArray();
        jsonObject.add("childElements", childElementsJsonArray);
        if (propDepth > 1) {//避免结果膨胀，第二层开始不向下递归
            return jsonObject;
        }
        // Serialize child elements
        PsiElement[] childElements = psiElement.getChildren();
        for (PsiElement childElement : childElements) {
            try {
                childElementsJsonArray.add(serializeRecursively(currentFile, childElement, jsonSerializationContext, elemDepth + 1, propDepth, new HashSet<>(visited)));
            } catch (Exception e) {
                LOGGER.error(String.format("psiElement serialize error, e = [%s]", e.getMessage()), e);
            }
        }
        return jsonObject;
    }
    static HashSet<String> excludeMethods = Sets.newHashSet(
            "com.intellij.psi.impl.source.PsiClassImpl|getAllFields",
            "com.intellij.psi.impl.source.PsiClassImpl|getAllInnerClasses",
            "com.intellij.psi.impl.source.PsiClassImpl|getAllMethods",
            "com.intellij.psi.impl.source.PsiClassImpl|getAllMethodsAndTheirSubstitutors",
            "com.intellij.psi.impl.source.PsiClassImpl|getInterfaces",
            "com.intellij.psi.impl.source.PsiClassImpl|getSuperClass",
            "com.intellij.psi.impl.source.PsiClassImpl|getSupers",
            "com.intellij.psi.impl.source.PsiClassImpl|getSuperTypes",
            "com.intellij.psi.impl.source.PsiClassImpl|getVisibleSignatures",
            "com.intellij.psi.impl.source.PsiFieldImpl|computeConstantValue",
            "com.intellij.psi.impl.source.PsiImportStaticReferenceElementImpl|resolve",
            "com.intellij.psi.impl.source.PsiImportStaticStatementImpl|resolveTargetClass",
            "com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl|getCanonicalText",
            "com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl|getQualifiedName",
            "com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl|getVariants",
            "com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl|resolve",
            "com.intellij.psi.impl.source.PsiMethodImpl|findDeepestSuperMethod",
            "com.intellij.psi.impl.source.PsiMethodImpl|findDeepestSuperMethods",
            "com.intellij.psi.impl.source.PsiMethodImpl|findSuperMethods",
            "com.intellij.psi.impl.source.PsiMethodImpl|getHierarchicalMethodSignature",
            "com.intellij.psi.impl.source.PsiModifierListImpl|getApplicableAnnotations",
            "com.intellij.psi.impl.source.PsiParameterImpl|getType",
            "com.intellij.psi.impl.source.PsiParameterImpl|normalizeDeclaration",
            "com.intellij.psi.impl.source.PsiTypeElementImpl|getAnnotations",
            "com.intellij.psi.impl.source.PsiTypeElementImpl|getApplicableAnnotations",
            "com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl|getQualifiedName",
            "com.intellij.psi.impl.source.tree.java.PsiAssignmentExpressionImpl|getType",
            "com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl|getType",
            "com.intellij.psi.impl.source.tree.java.PsiCatchSectionImpl|getPreciseCatchTypes",
            "com.intellij.psi.impl.source.tree.java.PsiClassObjectAccessExpressionImpl|getType",
            "com.intellij.psi.impl.source.tree.java.PsiExpressionListImpl|getExpressionTypes",
            "com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl|getTokenType",
            "com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl|toString",
            "com.intellij.psi.impl.source.tree.java.PsiLambdaExpressionImpl|getFunctionalInterfaceType",
            "com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl|getType",
            "com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl|resolveMethod",
            "com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl|resolveMethodGenerics",
            "com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl|getFunctionalInterfaceType",
            "com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl|getPotentiallyApplicableMember",
            "com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl|isExact",
            "com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl|resolve",
            "com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl|resolveConstructor",
            "com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl|resolveMethod",
            "com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl|resolveMethodGenerics",
            "com.intellij.psi.impl.source.tree.java.PsiPrefixExpressionImpl|getType",
            "com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl|getCanonicalText",
            "com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl|getClassNameText",
            "com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl|getQualifiedName",
            "com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl|getQualifierExpression",
            "com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl|getType",
            "com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl|resolve",
            "com.intellij.psi.impl.source.tree.java.PsiTypeCastExpressionImpl|getType",
            "com.intellij.psi.impl.source.tree.java.PsiTypeParameterImpl|getAllMethodsAndTheirSubstitutors",
            "com.intellij.psi.impl.source.tree.java.PsiTypeParameterImpl|getImplementsList",
            "com.intellij.psi.impl.source.tree.java.PsiTypeParameterImpl|getSuperClass",
            "com.intellij.psi.impl.source.tree.java.PsiTypeParameterImpl|getSupers",
            "com.intellij.psi.impl.source.tree.java.PsiTypeParameterImpl|getSuperTypes",
            "com.intellij.psi.impl.source.tree.java.PsiTypeParameterImpl|getVisibleSignatures",
            "de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder|copy",
            "de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder|getChildren",
            "de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder|getModifierList",
            "de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder|getNameIdentifier",
            "de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder|getParameterList"
            );

    public static JsonObject getPsiElementProps(PsiElement psiElement, JsonSerializationContext jsonSerializationContext,
                                          int elemDepth, int propDepth, Set<Object> visited) {
        JsonObject props = new JsonObject();
        props.addProperty("propDepth", propDepth);
        if (propDepth > 2) {
            addError(props,"propDepth > 2");
            return props;
        }
        Class<? extends PsiElement> clazz = psiElement.getClass();
        Method[] methods = clazz.getDeclaredMethods();
        String clazzName = clazz.getName();
        for (Method method : methods) {
            String methodName = method.getName();
            Method accessibleMethod = MethodUtils.getAccessibleMethod(method);
            if (accessibleMethod == null || method.getParameterCount() > 0) {
                continue;
            }
            if (excludeMethods.contains(String.format("%s|%s", clazzName, methodName))) {
                props.addProperty(methodName, "excluded");
                continue;
            }
            // 检查递归深度
            try {
                Object result = method.invoke(psiElement);
                Set<Object> visitedForProp = new HashSet<>(visited);
//                JsonObject jsonObject = new JsonObject();
                JsonElement resultValueJsonElement = convertToJsonElement(psiElement.getContainingFile(), result, jsonSerializationContext, elemDepth, propDepth, visitedForProp);
//                jsonObject.addProperty("type", result.getClass().getName());
//                jsonObject.add("val", resultValueJsonElement);
                props.add(methodName, resultValueJsonElement);
            } catch (Throwable e) {
//                props.addProperty(methodName, "props error:" + e.getMessage());
                props.add(methodName, null);
                GsonUtils.collectInvokeError(clazzName, methodName, e.getMessage());
                LOGGER.error(String.format("gen props error, psiElement = [%s], methodName = [%s], e = [%s]", psiElement, methodName, e.getMessage()));
            }
        }
        return props;
    }

    private static JsonElement convertToJsonElement(PsiFile containingFile, Object obj, JsonSerializationContext jsonSerializationContext,
                                             int elementDepth, int propDepth, Set<Object> visited) {
        if (obj == null) {
            return JsonNull.INSTANCE;
        } else if (obj.getClass().isArray()) {
            JsonArray jsonArray = new JsonArray();
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                Object o = Array.get(obj, i);
                jsonArray.add(convertToJsonElement(containingFile, o, jsonSerializationContext, elementDepth, propDepth, visited));
            }
            return jsonArray;
        } else if (obj instanceof Collection) {
            JsonArray jsonArray = new JsonArray();
            for (Object item : (Collection<?>) obj) {
                jsonArray.add(convertToJsonElement(containingFile, item, jsonSerializationContext, elementDepth, propDepth, visited));
            }
            return jsonArray;
        } else if (obj instanceof Number || obj instanceof Boolean || obj instanceof Character || obj instanceof String) {
            return new JsonPrimitive(String.valueOf(obj));
        } else if (obj instanceof PsiElement) {
            return serializeRecursively(containingFile, (PsiElement) obj, jsonSerializationContext, 1, propDepth + 1, visited);
        } else {
            String name = obj.getClass().getName();
            return new JsonPrimitive("unknown type|" + name + ":" + obj);
        }
    }
}