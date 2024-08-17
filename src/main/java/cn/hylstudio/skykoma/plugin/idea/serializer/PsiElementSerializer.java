package cn.hylstudio.skykoma.plugin.idea.serializer;

import cn.hylstudio.skykoma.plugin.idea.util.PsiUtils;
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

    private JsonObject serializeRecursively(PsiFile currentFile, PsiElement psiElement,
                                            JsonSerializationContext jsonSerializationContext,
                                            int elemDepth, int propDepth,
                                            Set<Object> visited) {
        JsonObject jsonObject = new JsonObject();
        if (visited.contains(psiElement)) {
//            jsonObject.addProperty("omit", true);
//            jsonObject.addProperty("omitReason", "cycle detected");
            return jsonObject;
        }
        visited.add(psiElement);
        jsonObject.addProperty("elemDepth", elemDepth);
        jsonObject.addProperty("propDepth", propDepth);
        int lineNumber = PsiUtils.getLineNumber(psiElement);
        String currentFileName = currentFile.getName();
        LOGGER.info(String.format("processing filePath = [%s], lineNum = [%s], elemDepth = [%s], propDepth = [%s]",
                currentFileName, lineNumber, elemDepth, propDepth));
        fillBasicInfo(currentFile, psiElement, jsonObject, elemDepth, propDepth);
        if (!currentFile.equals(psiElement.getContainingFile())) {
//            jsonObject.addProperty("omit", true);
//            jsonObject.addProperty("omit", "out of file");
            return jsonObject;
        }
//        jsonObject.addProperty("omit", false);
        // Serialize properties
        JsonObject props = getPsiElementProps(psiElement, jsonSerializationContext, elemDepth, propDepth, visited);
        jsonObject.add("props", props);
        if (propDepth > 1) {//避免结果膨胀，第二层开始不向下递归
            return jsonObject;
        }
        // Serialize child elements
        JsonArray childElementsJsonArray = new JsonArray();
        PsiElement[] childElements = psiElement.getChildren();
        for (PsiElement childElement : childElements) {
            try {
                childElementsJsonArray.add(serializeRecursively(currentFile, childElement, jsonSerializationContext, elemDepth + 1, propDepth, new HashSet<>(visited)));
            } catch (Exception e) {
                LOGGER.error(String.format("psiElement serialize error, e = [%s]", e.getMessage()), e);
            }
        }
        jsonObject.add("childElements", childElementsJsonArray);
        return jsonObject;
    }

    private JsonObject getPsiElementProps(PsiElement psiElement, JsonSerializationContext jsonSerializationContext,
                                          int elemDepth, int propDepth, Set<Object> visited) {
        JsonObject props = new JsonObject();
        if (propDepth > 2) {
//            props.addProperty("omit", true);
//            props.addProperty("omitReason", "maximum depth reached");
            return props;
        }
//        props.addProperty("propDepth", propDepth);
        Class<? extends PsiElement> clazz = psiElement.getClass();
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            Method accessibleMethod = MethodUtils.getAccessibleMethod(method);
            if (accessibleMethod == null || method.getParameterCount() > 0) {
                continue;
            }
            // 检查递归深度
            try {
                Object result = method.invoke(psiElement);
                Set<Object> visitedForProp = new HashSet<>(visited);
                JsonElement jsonElement = convertToJsonElement(psiElement.getContainingFile(), result, jsonSerializationContext, elemDepth, propDepth, visitedForProp);
                props.add(methodName, jsonElement);
            } catch (Throwable e) {
//                props.addProperty(methodName, "props error:" + e.getMessage());
                props.add(methodName, null);
                LOGGER.error(String.format("gen props error, psiElement = [%s], e = [%s]", psiElement, e.getMessage()));
            }
        }
        return props;
    }

    private JsonElement convertToJsonElement(PsiFile containingFile, Object obj, JsonSerializationContext jsonSerializationContext,
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