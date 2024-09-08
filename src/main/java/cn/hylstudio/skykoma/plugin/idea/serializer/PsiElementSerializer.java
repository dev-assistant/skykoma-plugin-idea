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
        JsonObject result = serializeRecursively(containingFile, psiElement, jsonSerializationContext, 1, 1, visited);
        int lineNumber = PsiUtils.getLineNumber(psiElement);
        String currentFileName = containingFile.getName();
        LOGGER.info(String.format("processing filePath = [%s], lineNum = [%s]", currentFileName, lineNumber));
        return result;
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
//        int lineNumber = PsiUtils.getLineNumber(psiElement);
//        String currentFileName = currentFile.getName();
        boolean needBreak = fillBasicInfo(currentFile, psiElement, jsonObject, elemDepth, propDepth);
//        LOGGER.info(String.format("processing filePath = [%s], lineNum = [%s], elemDepth = [%s], propDepth = [%s], needBreak = [%s]",
//                currentFileName, lineNumber, elemDepth, propDepth, needBreak));
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
            "com.intellij.psi.impl.source.PsiParameterImpl|normalizeDeclaration",
            "com.intellij.psi.impl.source.PsiTypeElementImpl|getInnermostComponentReferenceElement",
            "de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder|copy",
            "de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder|getChildren",
            "de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder|getBody",
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
            String methodId = String.format("%s|%s", clazzName, methodName);
            boolean excluded = excludeMethods.contains(methodId);
            if (excluded) {
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
                String errMsg = e.getMessage();
                String errMsgForJson = String.format("gen props error, psiElement = [%s], methodId = [%s], e = [%s]", psiElement, methodId, errMsg);
                props.addProperty(methodName, errMsgForJson);
                GsonUtils.collectInvokeError(clazzName, methodName, errMsg);
                LOGGER.error(errMsgForJson);
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