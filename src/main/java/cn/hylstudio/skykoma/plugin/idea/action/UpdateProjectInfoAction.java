// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package cn.hylstudio.skykoma.plugin.idea.action;

import cn.hylstudio.skykoma.plugin.idea.service.IProjectInfoService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class UpdateProjectInfoAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Project project = anActionEvent.getProject();
        assert project != null;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "UploadProjectInfo Task") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false); // 设置进度条为确定进度条
                indicator.setFraction(0.0); // 设置初始进度为 0.0
                // 执行一些耗时操作
//                for (int i = 0; i < 100; i++) {
//                    if (indicator.isCanceled()) {
//                        return; // 用户取消操作
//                    }
//                    indicator.setFraction((double) i / 100); // 更新进度条
//                }

                Application application = ApplicationManager.getApplication();
                application.runReadAction(() -> {
                    IProjectInfoService projectService = project.getService(IProjectInfoService.class);
                    projectService.updateProjectInfo(true);
                });
                indicator.setFraction(1.0);
                // 耗时操作完成
            }
        });
//        Editor editor = anActionEvent.getData(CommonDataKeys.EDITOR);
//        PsiFile psiFile = anActionEvent.getData(CommonDataKeys.PSI_FILE);
//        if (editor == null || psiFile == null) {
//            return;
//        }
//        int offset = editor.getCaretModel().getOffset();
//
//        final StringBuilder infoBuilder = new StringBuilder();
//        PsiElement element = psiFile.findElementAt(offset);
//        infoBuilder.append("Element at caret: ").append(element).append("\n");
//        if (element != null) {
//            PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
//            infoBuilder
//                    .append("Containing method: ")
//                    .append(containingMethod != null ? containingMethod.getName() : "none")
//                    .append("\n");
//            if (containingMethod != null) {
//                PsiClass containingClass = containingMethod.getContainingClass();
//                infoBuilder
//                        .append("Containing class: ")
//                        .append(containingClass != null ? containingClass.getName() : "none")
//                        .append("\n");
//
//                infoBuilder.append("Local variables:\n");
//                containingMethod.accept(new JavaRecursiveElementVisitor() {
//                    @Override
//                    public void visitLocalVariable(PsiLocalVariable variable) {
//                        super.visitLocalVariable(variable);
//                        infoBuilder.append(variable.getName()).append("\n");
//                    }
//                });
//            }
//        }
//        Messages.showMessageDialog(anActionEvent.getProject(), infoBuilder.toString(), "PSI Info", null);
    }

    @Override
    public void update(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabled(editor != null && psiFile != null);
    }

}
