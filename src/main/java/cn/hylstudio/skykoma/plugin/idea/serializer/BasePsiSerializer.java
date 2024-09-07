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
    protected static void addError(JsonObject jsonObject, String msg) {
        if (jsonObject.has("hasErr")) {
            jsonObject.remove("hasErr");
        }
        if (jsonObject.has("error")) {
            jsonObject.remove("error");
        }
        jsonObject.addProperty("hasErr", true);
        jsonObject.addProperty("error", msg);
    }

    protected static boolean fillBasicInfo(PsiFile currentFile, PsiElement psiElement, JsonObject jsonObject, int elemDepth, int propDepth) {
        Class<? extends PsiElement> clazz = psiElement.getClass();
        jsonObject.addProperty("className", clazz.getName());
        PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile == null) {
            addError(jsonObject, "containingFileName null");
            return true;
        }
        String name = containingFile.getName();
        jsonObject.addProperty("containingFileName", name);
        if (!currentFile.equals(containingFile)) {
            addError(jsonObject, "out of file");
            return true;
        }
        String originText = psiElement.getText();
        jsonObject.addProperty("originText", originText);
        int lineNumber = PsiUtils.getLineNumber(psiElement);
        jsonObject.addProperty("lineNum", lineNumber);

        TextRange textRange = psiElement.getTextRange();
        int startOffset = textRange.getStartOffset();
        int endOffset = textRange.getEndOffset();
        jsonObject.addProperty("startOffset", startOffset);
        jsonObject.addProperty("endOffset", endOffset);
        if (elemDepth > 1) {//避免结果膨胀，第二层开始不fill重复的信息
            return false;
        }
        Project project = containingFile.getProject();
        String basePath = project.getBasePath();
        if (basePath == null) {
            addError(jsonObject, "basePath null");
            return true;
        }
        //对当前的文件获取源码
        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null) {
            addError(jsonObject, "virtualFile null");
            return true;
        }
        String filePath = virtualFile.getPath();
        jsonObject.addProperty("absolutePath", filePath);
        boolean inProject = filePath.startsWith(basePath);
        jsonObject.addProperty("inProject", inProject);
        String relativePath = "";
        if (inProject) {
            relativePath = filePath.substring(basePath.length());
            if (relativePath.startsWith("\\") || relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
        }
        jsonObject.addProperty("relativePath", relativePath);
        return false;
    }
}

