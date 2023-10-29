package cn.hylstudio.skykoma.plugin.idea.util

import com.intellij.lang.jvm.annotation.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*

val PsiElement.lineNumber
    get():Int {
        val v: PsiElement = this
        val containingFile: PsiFile = v.containingFile
        val fileViewProvider: FileViewProvider = containingFile.viewProvider
        val document: Document = fileViewProvider.document
        val textOffset: Int = v.textOffset
        if (textOffset < 0) {
            return 1
        }
        val lineNumber: Int = document.getLineNumber(textOffset)
        return lineNumber + 1
    }

fun PsiElement.requestFocus() {
    val v: PsiElement = this
    val lineNum: Int = v.lineNumber
    val project: Project = v.project
    val psiFile: PsiFile = v.containingFile
    val virtualFile: VirtualFile = psiFile.virtualFile
    val openFileDescriptor: OpenFileDescriptor = OpenFileDescriptor(project, virtualFile, lineNum - 1, 0)
    val fileEditorManager: FileEditorManager = project.fileEditorManager
    invokeLater { fileEditorManager.openTextEditor(openFileDescriptor, true); }
}

fun PsiClass.findAnnotation(fullyQualifiedName: String): PsiAnnotation? {
    return this.modifierList?.findAnnotation(fullyQualifiedName)
}

val PsiClass.psiAnnotations: Array<PsiAnnotation> get() = this.modifierList?.annotations ?: arrayOf()
val PsiClass.isController: Boolean
    get() {
        val psiClass: PsiClass = this
        return runReadAction<Boolean> {
            val annotationRestController: PsiAnnotation? = psiClass.findAnnotation("org.springframework.web.bind.annotation.RestController")
            val annotationController: PsiAnnotation? = psiClass.findAnnotation("org.springframework.stereotype.Controller")
            annotationRestController != null || annotationController != null
        }
    }

fun JvmAnnotationAttributeValue.parseValue(): List<String> {
    when (this) {
        is JvmAnnotationConstantValue -> {  // For PsiAnnotationConstantValue
            return listOf("const", this.constantValue as String)
        }

        is JvmAnnotationEnumFieldValue -> {  // For PsiAnnotationEnumFieldValue
            return listOf("enum", "${this.containingClassName}.${this.fieldName}")
        }

        is JvmAnnotationArrayValue -> {  // For PsiAnnotationArrayValue
            return this.values.map { it.parseValue() }.flatten()
        }

        is JvmAnnotationClassValue -> {  // For PsiAnnotationClassValue
            return listOf("class", this.qualifiedName ?: "")
        }
        // is JvmNestedAnnotationValue -> {  // For PsiNestedAnnotationValue
        //     // println(this.value)
        //     return listOf("annotation", "")//TODO
        // }
        else -> {
            // handle unexpected type
            return listOf("unknown", "")
        }
    }


}