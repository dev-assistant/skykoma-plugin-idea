package cn.hylstudio.skykoma.plugin.idea.util

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiManager
import com.intellij.ui.AppIcon
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import javax.swing.JFrame

val Module.rootManager: ModuleRootManager get() = ModuleRootManager.getInstance(this)
val ModuleRootManager.srcRoots: List<VirtualFile> get() = getSourceRoots(JavaSourceRootType.SOURCE)
val ModuleRootManager.resourceRoots: List<VirtualFile> get() = getSourceRoots(JavaResourceRootType.RESOURCE)
val ModuleRootManager.testSrcRoots: List<VirtualFile> get() = getSourceRoots(JavaSourceRootType.TEST_SOURCE)
val ModuleRootManager.testResourceRoots: List<VirtualFile> get() = getSourceRoots(JavaResourceRootType.TEST_RESOURCE)
val Module.srcRoots: List<VirtualFile> get() = this.rootManager.srcRoots
val Module.testSrcRoots: List<VirtualFile> get() = this.rootManager.testSrcRoots
val Module.resourceRoots: List<VirtualFile> get() = this.rootManager.resourceRoots
val Module.testResourceRoots: List<VirtualFile> get() = this.rootManager.testResourceRoots
fun Module.printInfo() {
    val module = this
    val moduleName: String = module.name
    val sourceRoots: List<VirtualFile> = module.srcRoots
    val testSrcRoots: List<VirtualFile> = module.testSrcRoots
    val resourceRoots: List<VirtualFile> = module.resourceRoots
    val testResourceRoots: List<VirtualFile> = module.testResourceRoots
    println("moduleName:$moduleName")
    println("sourceRoots:$sourceRoots,testSrcRoots:$testSrcRoots,resourceRoots:$resourceRoots,testResourceRoots:$testResourceRoots")
}

fun getCurrentProject(): Project? {
    val dataContext: DataContext? = DataManager.getInstance().dataContextFromFocusAsync.blockingGet(2000)
    return LangDataKeys.PROJECT.getData(dataContext!!)
}

fun getOpenedProjects(): Array<Project> {
    return ProjectManager.getInstance().openProjects
}

fun openProjectByFolder(projectFolderDir: String): Project? {
    val openedProjects: Array<Project> = getOpenedProjects()
    val project: Project? = openedProjects.filter { it.basePath == projectFolderDir }.firstOrNull()
    if (project != null) {
        project.requestFocus()
        return project
    }
    return ProjectManager.getInstance().loadAndOpenProject(projectFolderDir)
}

fun filterProjectByName(projectName: String): Project? {
    return getOpenedProjects().filter { it.name == projectName }.firstOrNull()
}

val Project.psiManager: PsiManager get() = PsiManager.getInstance(this)
val Project.fileEditorManager: FileEditorManager get() = FileEditorManager.getInstance(this)
val Project.moduleManager: ModuleManager get() = ModuleManager.getInstance(this)
val Project.modules: Array<Module> get() = this.moduleManager.modules

fun Project.printInfo() {
    val it = this
    val projectName: String = it.name
    val projectDir: String? = it.basePath
    val projectDirName: String = getDirName(projectDir)
    val currentBranch = it.currentBranch
    val currentHash = it.currentHash
    println("projectName:$projectName, basePath:$projectDir, projectDirName:$projectDirName")
    println("currentBranch:$currentBranch, currentHash:$currentHash")
    println()
}

fun Project.requestFocus() {
    invokeLater {
        val frame: JFrame? = WindowManager.getInstance().getFrame(this)
        frame!!.toFront()
        AppIcon.getInstance().requestFocus(frame)
    }
}

fun Project.closeProject() {
    invokeLater {
        ProjectManager.getInstance().closeAndDispose(this)
    }
}

val Project.currentHash: String
    get() {
        val projectDir = this.basePath
        return projectDir.gitCurrentHash
    }

val Project.currentBranch: String
    get() {
        val projectDir = this.basePath
        return projectDir.gitCurrentBranch
    }

fun filterJavaFile(sourceRoot: VirtualFile): List<VirtualFile> =
        mutableListOf<VirtualFile>().also { javaFiles ->
            VfsUtil.iterateChildrenRecursively(
                    sourceRoot,
                    { true },
                    { if (!it.isDirectory && it.name.endsWith(".java")) javaFiles.add(it); true }
            )
        }

fun Project.scanJavaFile(scanCb: (Module, VirtualFile, Int, Int, VirtualFile) -> Unit) {
    val modules: Array<Module> = this.modules
    modules.forEach { module ->
        module.srcRoots.forEach { srcRoot ->
            val javaFiles: List<VirtualFile> = filterJavaFile(srcRoot)
//            println("scan moduleName:${module.name},javaFiles:${javaFiles.size},srcRoot:${srcRoot}")
            javaFiles.forEachIndexed { i, it ->
                scanCb(module, srcRoot, i, javaFiles.size, it)
            }
        }
    }
}

val VirtualFile.project:Project? get() =  ProjectLocator.getInstance().guessProjectForFile(this)