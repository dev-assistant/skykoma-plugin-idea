package cn.hylstudio.skykoma.plugin.idea.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile


val VirtualFile.project: Project? get() = ProjectLocator.getInstance().guessProjectForFile(this)
val VirtualFile.toPsiFile: PsiFile? get() = this.project?.psiManager?.findFile(this)

fun VirtualFile.openFileInEditor(line: Int? = 0, column: Int? = 0, offset: Int? = 0, project: Project? = this.project) {
    val openLine = if (line != null && line > 0) line - 1 else 0
    val openColumn = if (column != null && column > 0) column - 1 else 0
    val openProject = project!!
    val fileEditorManager = FileEditorManager.getInstance(openProject)
    val openFileDescriptor = if (offset != null) {
        OpenFileDescriptor(openProject, this, offset)
    } else {
        OpenFileDescriptor(openProject, this, openLine, openColumn)
    }
    val editor: Editor? = fileEditorManager.openTextEditor(openFileDescriptor, true)
    println("${this.name} opened in $editor")
}