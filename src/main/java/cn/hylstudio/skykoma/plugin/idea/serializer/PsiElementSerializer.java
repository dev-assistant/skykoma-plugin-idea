package cn.hylstudio.skykoma.plugin.idea.serializer;

import cn.hylstudio.skykoma.plugin.idea.util.ProjectUtils;
import cn.hylstudio.skykoma.plugin.idea.util.PsiUtils;
import com.google.gson.*;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;

import java.lang.reflect.Type;
import java.util.List;

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
        if (psiElement instanceof PsiClassImpl) {
            PsiClassImpl psiClass = (PsiClassImpl) psiElement;
            String qualifiedName = psiClass.getQualifiedName();
            jsonObject.addProperty("qualifiedName", qualifiedName);
        } else if (psiElement instanceof PsiAnnotationImpl) {
            PsiAnnotationImpl annotation = (PsiAnnotationImpl) psiElement;
            String qualifiedName = annotation.getQualifiedName();
            List<JvmAnnotationAttribute> jvmAnnotationAttributes = annotation.getAttributes();
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
            jsonObject.addProperty("qualifiedName", qualifiedName);
        }
        return jsonObject;

    }
}