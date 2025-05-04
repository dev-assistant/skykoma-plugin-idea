package cn.hylstudio.skykoma.plugin.idea.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.actions.RefreshAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.GitUtil
import git4idea.branch.GitBrancher
import git4idea.branch.GitBranchesCollection
import git4idea.fetch.GitFetchSupport
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

fun getDirName(dir: String?): String {
    return if (dir != null) File(dir).name else ""
}

fun execCmd(cmd: String, cwd: String? = null): Pair<String, String> {
    try {
        val cwdFile: File? = if (cwd != null) File(cwd) else null
        val st = StringTokenizer(cmd)
        val cmdArray = arrayOfNulls<String>(st.countTokens())
        var i = 0
        while (st.hasMoreTokens()) {
            cmdArray[i] = st.nextToken()
            i++
        }
        val process: Process = Runtime.getRuntime().exec(cmdArray, null, cwdFile)
        val stdout = BufferedReader(InputStreamReader(process.inputStream))
        val stderr = BufferedReader(InputStreamReader(process.errorStream))
        val stdOutput: String = stdout.readText()
        val stdErrOutput: String = stderr.readText()
        // println("exec, cmd = $cmd, stdout = $stdOutput, stderr = $stdErrOutput")
        return Pair(stdOutput, stdErrOutput)
    } catch (e: IOException) {
        println("exec error, cmd = $cmd, e = ${e.message}")
    }
    return Pair("", "")
}

val GitBranchesCollection.localBranchesList: List<GitLocalBranch> get() = ArrayList(this.localBranches)
val GitBranchesCollection.remoteBranchesList: List<GitRemoteBranch> get() = ArrayList(this.remoteBranches)

fun GitRemoteBranch.forceCheckout(newBranchName: String? = "") {
    var remoteBranchName = this.name    // origin/abcde
    var branchName = this.nameForRemoteOperations    // abcde

    val project = getCurrentProject()
    val repositoryManager = GitRepositoryManager.getInstance(project!!)
    val allRepositories = repositoryManager.repositories
    invokeLater {
        val brancher = GitBrancher.getInstance(project)
        if (newBranchName != null && newBranchName != "") {
            branchName = newBranchName
        }
        val repository = allRepositories[0]
        brancher.deleteBranch(branchName, listOf(repository))
        brancher.createBranch(branchName, mapOf(repository to remoteBranchName), true)
        brancher.checkout(branchName, false, listOf(repository), { println("checkout $branchName succ") })
    }
}
//val String?.gitCurrentHash: String
//    get() {
//        val cmd = "git -C $this rev-parse HEAD"
//        val output: Pair<String, String> = execCmd(cmd)
//        return output.first
//    }
//
//
//val String?.gitCurrentBranch: String
//    get() {
//        val cmd = "git -C $this rev-parse --abbrev-ref HEAD"
//        val output: Pair<String, String> = execCmd(cmd)
//        return output.first
//    }


val Project.repositoryManager: GitRepositoryManager get() = GitRepositoryManager.getInstance(this)
val Project.repositories: Collection<GitRepository> get() = GitUtil.getRepositories(this)
val Project.currentHash: String
    get() {
//        val projectDir = this.basePath
//        return projectDir.gitCurrentHash
        return this.repositories.iterator().next().currentRevision ?: ""
    }


val Project.currentBranch: String
    get() {
//        val projectDir = this.basePath
//        return projectDir.gitCurrentBranch
        return this.repositories.iterator().next().currentBranch?.name ?: ""
    }

fun Project.fetchAllRemotesRepos() {
    //execCmd("git fetch", "$projectDir")
    val fetchSupport = GitFetchSupport.fetchSupport(this)
    val repos = GitUtil.getRepositories(this)
    val result = fetchSupport.fetchAllRemotes(repos)
    result.showNotification()
}

fun Project.refreshGitLogsUi(showUi: Boolean = true) {
    val project = this
    val myProjectLog = VcsProjectLog.getInstance(project)
    val logManager = myProjectLog.logManager!!
    val dataManager = logManager.dataManager
    val toolWindowManager = project.toolWindowManager
    WriteCommandAction.runWriteCommandAction(project) {
        if (showUi) {
            toolWindowManager.getToolWindow(ToolWindowId.VCS)?.activate {
                println("Git tool window activated.")
            }
        }
        val logUis = logManager.logUis
        WriteCommandAction.runWriteCommandAction(project) {
            if (logUis.size > 0) {
                dataManager.refresh(VcsLogUtil.getVisibleRoots(logUis[0]));
            } else {
                println("logUis empty")
            }
            RefreshAction.doRefresh(project)
            println("refresh ui finished")
        }
    }
}

fun VirtualFile.isIgnoredByGit(): Boolean {
    val virtualFile: VirtualFile = this
    val project: Project = ProjectLocator.getInstance().guessProjectForFile(virtualFile) ?: return false
    val repoManager = GitRepositoryManager.getInstance(project)
    val repository: GitRepository = repoManager.getRepositoryForFile(virtualFile) ?: return false
    val path: FilePath = LocalFilePath(virtualFile.path, virtualFile.isDirectory)
    return repository.ignoredFilesHolder.containsFile(path)
}
