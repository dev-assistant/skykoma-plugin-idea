package cn.hylstudio.skykoma.plugin.idea.util

import com.intellij.ide.DataManager
import com.intellij.lang.jvm.annotation.*
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.utils.keysToMap

fun getDataContext(): DataContext? {
    val dataManager = DataManager.getInstance()
    return dataManager.dataContextFromFocusAsync.blockingGet(10)
}

val DataContext.currentPsiFile: PsiFile? get() = CommonDataKeys.PSI_FILE.getData(this)
val DataContext.currentPsiElement: PsiElement? get() = CommonDataKeys.PSI_ELEMENT.getData(this)
val DataContext.caret: Caret? get() = CommonDataKeys.CARET.getData(this)
val PsiFile.psiClasses: List<PsiClass>
    get() {
        return mutableListOf<PsiClass>().also {
            this.accept(object : JavaRecursiveElementVisitor() {
                override fun visitClass(aClass: PsiClass) {
                    super.visitClass(aClass)
                    it.add(aClass)
                }
            })
        }
    }
val PsiFile.psiClass: PsiClass? get() = this.psiClasses.firstOrNull()
val PsiFile.currentPsiElement: PsiElement?
    get() {
        val psiElement: PsiElement? = getDataContext()?.currentPsiElement
        return if (psiElement?.containingFile == this) psiElement else null
    }
val PsiElement.containingMethod: PsiMethod? get() = PsiTreeUtil.getParentOfType(this, PsiMethod::class.java)
val PsiElement.containingClass: PsiClass? get() = this.containingMethod?.containingClass
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
val PsiElement.columnNumber
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
        return textOffset - document.getLineStartOffset(lineNumber)
    }

fun PsiElement.requestFocus() {
    val v: PsiElement = this
//    val lineNum: Int = v.lineNumber
//    val columnNum: Int = v.columnNumber
    val project: Project = v.project
    val psiFile: PsiFile = v.containingFile
    val virtualFile: VirtualFile = psiFile.virtualFile
    val offset = v.textOffset
//    val openFileDescriptor: OpenFileDescriptor = OpenFileDescriptor(project, virtualFile, lineNum - 1, columnNum)
    val openFileDescriptor: OpenFileDescriptor = OpenFileDescriptor(project, virtualFile, offset)
    val fileEditorManager: FileEditorManager = project.fileEditorManager
    invokeLater { fileEditorManager.openTextEditor(openFileDescriptor, true); }
}

fun PsiClass.findAnnotation(fullyQualifiedName: String): PsiAnnotation? {
    return this.modifierList?.findAnnotation(fullyQualifiedName)
}

val PsiClass.psiAnnotations: Array<PsiAnnotation> get() = this.modifierList?.annotations ?: arrayOf()
val PsiClass.isSpringController: Boolean
    get() {
        val psiClass: PsiClass = this
        return runReadAction<Boolean> {
            val annotationRestController: PsiAnnotation? =
                psiClass.findAnnotation("org.springframework.web.bind.annotation.RestController")
            val annotationController: PsiAnnotation? =
                psiClass.findAnnotation("org.springframework.stereotype.Controller")
            annotationRestController != null || annotationController != null
        }
    }

fun JvmAnnotationAttributeValue.parseValue(): Map<String, Any> {
    when (this) {
        is JvmAnnotationConstantValue -> {  // For PsiAnnotationConstantValue
            return mapOf<String, Any>(
                "type" to "const",
                "value" to this.constantValue.toString()
            )
        }

        is JvmAnnotationEnumFieldValue -> {  // For PsiAnnotationEnumFieldValue
            return mapOf<String, Any>(
                "type" to "enum",
                "value" to "${this.containingClassName}.${this.fieldName}"
            )
        }

        is JvmAnnotationArrayValue -> {  // For PsiAnnotationArrayValue
            return mapOf<String, Any>(
                "type" to "arr",
                "value" to this.values.map { it.parseValue() }//List<Map<String, Any>>(
            )
        }

        is JvmAnnotationClassValue -> {  // For PsiAnnotationClassValue
            return mapOf<String, Any>(
                "type" to "class",
                "value" to (this.qualifiedName ?: "")
            )
        }

        is JvmNestedAnnotationValue -> {  // For PsiNestedAnnotationValue
            // println(this.value)
            val annotation = this.value
            val value: Map<String, Map<String, Any>> = annotation.attributes.associateBy(
                { it.attributeName },
                { it.attributeValue?.parseValue() ?: mapOf() })
            return mapOf<String, Any>(
                "type" to "annotation",
                "qualifiedName" to (annotation?.qualifiedName ?: ""),
                "value" to value
            )
        }

        else -> {
            // handle unexpected type
            return mapOf<String, Any>(
                "type" to "unknown",
                "value" to ""
            )
        }
    }


}