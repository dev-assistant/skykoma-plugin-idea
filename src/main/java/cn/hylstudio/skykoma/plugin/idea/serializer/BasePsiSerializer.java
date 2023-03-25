package cn.hylstudio.skykoma.plugin.idea.serializer;

import com.google.gson.JsonObject;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

public class BasePsiSerializer {

    protected static void fillBasicInfo(PsiElement psiElement, JsonObject jsonObject) {
        String originText = psiElement.getText();
        TextRange textRange = psiElement.getTextRange();
        int startOffset = textRange.getStartOffset();
        int endOffset = textRange.getEndOffset();
        jsonObject.addProperty("className", psiElement.getClass().getName());
        jsonObject.addProperty("originText", originText);
        jsonObject.addProperty("startOffset", startOffset);
        jsonObject.addProperty("endOffset", endOffset);
    }
}
