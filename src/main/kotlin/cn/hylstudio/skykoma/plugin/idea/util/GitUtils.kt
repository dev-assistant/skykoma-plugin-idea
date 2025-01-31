package cn.hylstudio.skykoma.plugin.idea.util

import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBrancher
import git4idea.branch.GitBranchesCollection
import git4idea.repo.GitRepositoryManager
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader


fun getDirName(dir: String?): String {
    return if (dir != null) File(dir).name else ""
}

fun execCmd(cmd: String): Pair<String, String> {
    try {
        val process: Process = Runtime.getRuntime().exec(cmd)
        val stdout: BufferedReader = BufferedReader(InputStreamReader(process.inputStream))
        val stderr: BufferedReader = BufferedReader(InputStreamReader(process.errorStream))
        val stdOutput: String = stdout.readLine() ?: ""
        val stdErrOutput: String = stderr.readLine() ?: ""
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