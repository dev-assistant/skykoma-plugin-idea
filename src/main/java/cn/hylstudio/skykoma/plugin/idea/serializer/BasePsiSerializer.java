package cn.hylstudio.skykoma.plugin.idea.serializer;

import cn.hylstudio.skykoma.plugin.idea.util.PsiUtils;
import com.google.gson.JsonObject;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class BasePsiSerializer {

    protected static void fillBasicInfo(PsiFile currentFile, PsiElement psiElement, JsonObject jsonObject) {
        PsiFile containingFile = psiElement.getContainingFile();
        Class<? extends PsiElement> aClass = psiElement.getClass();
        jsonObject.addProperty("className", aClass.getName());
        //对当前的文件获取源码
        if (currentFile.equals(containingFile)) {
            String originText = psiElement.getText();
            TextRange textRange = psiElement.getTextRange();
            int startOffset = textRange.getStartOffset();
            int endOffset = textRange.getEndOffset();
            jsonObject.addProperty("originText", originText);
            jsonObject.addProperty("startOffset", startOffset);
            jsonObject.addProperty("endOffset", endOffset);
            int lineNumber = PsiUtils.getLineNumber(psiElement);
            jsonObject.addProperty("lineNum", lineNumber);
        }
    }
}
