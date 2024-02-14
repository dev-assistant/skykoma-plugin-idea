package cn.hylstudio.skykoma.plugin.idea.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

val VirtualFile.project: Project? get() = ProjectLocator.getInstance().guessProjectForFile(this)
val VirtualFile.toPsiFile: PsiFile? get() = this.project?.psiManager?.findFile(this)

