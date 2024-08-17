package cn.hylstudio.skykoma.plugin.idea.serializer;

import cn.hylstudio.skykoma.plugin.idea.util.PsiUtils;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.io.File;

public class BasePsiSerializer {

    protected static void fillBasicInfo(PsiFile currentFile, PsiElement psiElement, JsonObject jsonObject, int elemDepth, int propDepth) {
        Class<? extends PsiElement> clazz = psiElement.getClass();
        jsonObject.addProperty("className", clazz.getName());
        PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile == null) {
            jsonObject.add("containingFile", null);
            return;
        }
        String name = containingFile.getName();
        jsonObject.addProperty("containingFileName", name);
        if (currentFile.equals(containingFile)) {
            String originText = psiElement.getText();
            jsonObject.addProperty("originText", originText);
        } else {
            jsonObject.addProperty("originText", "");
        }
        int lineNumber = PsiUtils.getLineNumber(psiElement);
        jsonObject.addProperty("lineNum", lineNumber);

        TextRange textRange = psiElement.getTextRange();
        int startOffset = textRange.getStartOffset();
        int endOffset = textRange.getEndOffset();
        jsonObject.addProperty("startOffset", startOffset);
        jsonObject.addProperty("endOffset", endOffset);
        if (elemDepth > 1) {//避免结果膨胀，第二层开始不fill重复的信息
            return;
        }
        Project project = containingFile.getProject();
        String basePath = project.getBasePath();
        if (basePath == null) {
            jsonObject.addProperty("error", "basePath unknown");
            return;
        }
        //对当前的文件获取源码
        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null) {
            jsonObject.addProperty("error", "virtualFile unknown");
            return;
        }
        String filePath = virtualFile.getPath();
        String relativePath = filePath;
        boolean inProject = filePath.startsWith(basePath);
        jsonObject.addProperty("inProject", inProject);
        if (inProject) {
            relativePath = filePath.substring(basePath.length());
            if (relativePath.startsWith("\\") || relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
        }
        jsonObject.addProperty("relativePath", relativePath);
    }
}

