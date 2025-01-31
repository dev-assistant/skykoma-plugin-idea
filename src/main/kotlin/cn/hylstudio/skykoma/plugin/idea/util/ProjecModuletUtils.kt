package cn.hylstudio.skykoma.plugin.idea.util

import com.intellij.ide.DataManager
import com.intellij.ide.GeneralSettings
import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiManager
import com.intellij.ui.AppIcon
import com.intellij.util.xml.DomManager
import git4idea.repo.GitRepositoryManager
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.io.File
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

fun openProjectByFolder(projectFolderDir: String, autoTrustParent: Boolean? = false): Project? {
    val openedProjects: Array<Project> = getOpenedProjects()
    val project: Project? = openedProjects.firstOrNull { it.basePath == projectFolderDir }
    if (project != null) {
        project.requestFocus()
        return project
    }
    if (autoTrustParent != null && autoTrustParent) {
        projectFolderDir.trustParent()
    }
    val GeneralSettings: GeneralSettings = GeneralSettings.getInstance()
    // com.intellij.ide.GeneralSettings#OPEN_PROJECT_NEW_WINDOW
    val OPEN_PROJECT_SAME_WINDOW = 1
    val oldVal = GeneralSettings.confirmOpenNewProject
    var hasUpdated = false
    if (oldVal != OPEN_PROJECT_SAME_WINDOW) {
        GeneralSettings.confirmOpenNewProject = OPEN_PROJECT_SAME_WINDOW
        hasUpdated = true;
    }
    val result: Project? = ProjectManager.getInstance().loadAndOpenProject(projectFolderDir)
    if (hasUpdated) {
        GeneralSettings.confirmOpenNewProject = oldVal
    }
    return result
}

fun filterProjectByName(projectName: String): Project? {
    return getOpenedProjects().firstOrNull { it.name == projectName }
}

val Project.psiManager: PsiManager get() = PsiManager.getInstance(this)
val Project.fileEditorManager: FileEditorManager get() = FileEditorManager.getInstance(this)
val Project.moduleManager: ModuleManager get() = ModuleManager.getInstance(this)
val Project.domManager: DomManager get() = DomManager.getDomManager(this)
val Project.rootManager: ProjectRootManager get() = ProjectRootManager.getInstance(this)
val Project.modules: Array<Module> get() = this.moduleManager.modules
fun String.trustParent(): Boolean {
    val parentPath = File(this).parent.toNioPath()
    var trustedPathsSettings = TrustedPathsSettings.getInstance()
    if (trustedPathsSettings.isPathTrusted(parentPath)) {
        return false
    }
    trustedPathsSettings.addTrustedPath(parentPath.toString())
    println("trusted path added: $parentPath")
    return true
}

fun Project.printInfo() {
    val it = this
    val projectName: String = it.name
    val projectDir: String? = it.basePath
    val projectDirName: String = getDirName(projectDir)
    val currentBranch = it.currentBranch
    val currentHash = it.currentHash
    val projectSdk = it.rootManager.projectSdk?.homePath ?: ""
    println("projectName:$projectName, basePath:$projectDir, projectDirName:$projectDirName")
    println("currentBranch:$currentBranch, currentHash:$currentHash")
    println("projectSdk:$projectSdk")
    println()
}

fun Project.requestFocus() {
    invokeLater {
        val frame: JFrame? = WindowManager.getInstance().getFrame(this)
        frame!!.toFront()
        AppIcon.getInstance().requestFocus(frame)
    }
}

fun Project.updateJdk(homePath: String) {
    invokeLater {
        ProjectUtils.updateProjectJdk(homePath, this)
    }
}

fun Project.mavenReImport() {
    invokeLater {
        ProjectUtils.mavenReImport(this)
    }
}

fun Project.closeProject() {
    invokeLater {
        ProjectManager.getInstance().closeAndDispose(this)
    }
}

val Project.repositoryManager: GitRepositoryManager get() = GitRepositoryManager.getInstance(this)
val Project.currentHash: String
    get() {
//        val projectDir = this.basePath
//        return projectDir.gitCurrentHash
        return this.repositoryManager.repositories[0].currentRevision ?: ""
    }


val Project.currentBranch: String
    get() {
//        val projectDir = this.basePath
//        return projectDir.gitCurrentBranch
        return this.repositoryManager.repositories[0].currentBranch?.name ?: ""
    }

fun filterFileByExtension(sourceRoot: VirtualFile, extension: String): List<VirtualFile> =
    mutableListOf<VirtualFile>().also { result ->
        VfsUtil.iterateChildrenRecursively(
            sourceRoot,
            { true },
            { if (!it.isDirectory && it.name.endsWith(extension)) result.add(it); true }
        )
    }

fun Project.scanJavaFiles(scanCb: (Module, VirtualFile, Int, Int, VirtualFile) -> Unit) {
    scanFileByExtension(".java", scanCb)
}

fun Project.scanXmlFiles(scanCb: (Module, VirtualFile, Int, Int, VirtualFile) -> Unit) {
    scanFileByExtension(".xml", scanCb)
}

fun Project.scanFileByExtension(extension: String, scanCb: (Module, VirtualFile, Int, Int, VirtualFile) -> Unit) {
    val modules: Array<Module> = this.modules
    modules.forEach { module ->
        module.srcRoots.forEach { srcRoot ->
            val srcResult: List<VirtualFile> = filterFileByExtension(srcRoot, extension)
//            println("scan moduleName:${module.name},javaFiles:${javaFiles.size},srcRoot:${srcRoot}")
            srcResult.forEachIndexed { i, it ->
                scanCb(module, srcRoot, i, srcResult.size, it)
            }
        }
        module.resourceRoots.forEach { resRoot ->
            val resResult: List<VirtualFile> = filterFileByExtension(resRoot, extension)
//            println("scan moduleName:${module.name},javaFiles:${javaFiles.size},resRoot:${resRoot}")
            resResult.forEachIndexed { i, it ->
                scanCb(module, resRoot, i, resResult.size, it)
            }
        }
        module.testSrcRoots.forEach { testSrcRoot ->
            val testSrcResult: List<VirtualFile> = filterFileByExtension(testSrcRoot, extension)
//            println("scan moduleName:${module.name},javaFiles:${javaFiles.size},testSrcRoot:${testSrcRoot}")
            testSrcResult.forEachIndexed { i, it ->
                scanCb(module, testSrcRoot, i, testSrcResult.size, it)
            }
        }
        module.testResourceRoots.forEach { testResRoot ->
            val testResResult: List<VirtualFile> = filterFileByExtension(testResRoot, extension)
//            println("scan moduleName:${module.name},javaFiles:${javaFiles.size},testResRoot:${testResRoot}")
            testResResult.forEachIndexed { i, it ->
                scanCb(module, testResRoot, i, testResResult.size, it)
            }
        }
    }
}

