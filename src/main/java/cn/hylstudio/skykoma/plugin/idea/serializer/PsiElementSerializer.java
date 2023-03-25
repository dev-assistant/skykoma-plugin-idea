package cn.hylstudio.skykoma.plugin.idea.serializer;

import com.google.gson.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;

import java.lang.reflect.Type;

public class PsiElementSerializer extends BasePsiSerializer implements JsonSerializer<PsiElement> {
    private static final Logger LOGGER = Logger.getInstance(PsiElementSerializer.class);

    @Override
    public JsonElement serialize(PsiElement psiElement, Type type, JsonSerializationContext jsonSerializationContext) {
        return serializeRecursively(psiElement, jsonSerializationContext);
    }

    private JsonObject serializeRecursively(PsiElement psiElement, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonObject = new JsonObject();
        fillBasicInfo(psiElement, jsonObject);
        PsiElement[] childElements = psiElement.getChildren();
        JsonArray childElementsJsonArray = new JsonArray();
        for (PsiElement childElement : childElements) {
            try {
                childElementsJsonArray.add(serializeRecursively(childElement, jsonSerializationContext));
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error(String.format("psiElement serialize error, e = [%s]", e.getMessage()), e);
            }
        }
        jsonObject.add("childElements", childElementsJsonArray);
        return jsonObject;

    }
}