package cn.hylstudio.skykoma.plugin.idea.util

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