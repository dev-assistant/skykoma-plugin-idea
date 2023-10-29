package cn.hylstudio.skykoma.plugin.idea.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable

fun runReadAction(runnable: Runnable) {
    ApplicationManager.getApplication().runReadAction(runnable)
}

fun <T> runReadAction(computable: Computable<T>): T {
    return ApplicationManager.getApplication().runReadAction(computable)
}

fun <T, E : Throwable?> runReadAction(throwableComputable: ThrowableComputable<T, E>): T {
    return ApplicationManager.getApplication().runReadAction(throwableComputable)
}

fun runWriteAction(runnable: Runnable) {
    ApplicationManager.getApplication().runWriteAction(runnable)
}

fun <T> runWriteAction(computable: Computable<T>): T {
    return ApplicationManager.getApplication().runWriteAction(computable)
}

fun <T, E : Throwable?> runWriteAction(throwableComputable: ThrowableComputable<T, E>): T {
    return ApplicationManager.getApplication().runWriteAction(throwableComputable)
}

fun invokeLater(runnable: () -> Unit): Unit {
    ApplicationManager.getApplication().invokeLater(runnable)
}
