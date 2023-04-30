package cn.hylstudio.skykoma.plugin.idea


import org.jetbrains.kotlinx.jupyter.EvalRequestData
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.RuntimeKernelProperties
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.repl.EvalResultEx
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.slf4j.LoggerFactory
import kotlin.script.experimental.jvm.util.KotlinJars
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrStdlib

class KotlinReplWrapper(private val classLoader: ClassLoader) {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private var repl: ReplForJupyter

    init {
        this.repl = this.makeEmbeddedRepl()
    }

    private fun makeEmbeddedRepl(): ReplForJupyter {
//        val property = System.getProperty("java.class.path")
//        var embeddedClasspath: MutableList<File> = property.split(File.pathSeparator).map(::File).toMutableList()
//        val isInRuntime = embeddedClasspath.size == 1
//        if (isInRuntime) {
//            System.setProperty("kotlin.script.classpath", property)
//
//            val compiler = KotlinJars.compilerClasspath
//            if (compiler.isNotEmpty()) {
//                val tempdir = compiler[0].parent
//                embeddedClasspath =
//                    File(tempdir).walk(FileWalkDirection.BOTTOM_UP).sortedBy { it.isDirectory }.toMutableList()
//            }
//        }
        val embeddedClasspath =
                scriptCompilationClasspathFromContextOrStdlib(classLoader = classLoader, wholeClasspath = true) +
//                        scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true) +
                        KotlinJars.kotlinScriptStandardJars
//        embeddedClasspath = embeddedClasspath.distinctBy { it.name } as MutableList<File>
        logger.info("classpath: $embeddedClasspath")
        val port = 7172

        return createRepl(
                resolutionInfoProvider = EmptyResolutionInfoProvider,
                scriptClasspath = embeddedClasspath,
                libraryResolver = resolveIntellij(),
                displayHandler = NoOpDisplayHandler,
                isEmbedded = true,
                runtimeProperties = RuntimeKernelProperties(
                        mapOf(
                                "version" to " 0.11.0.348",//may be error
                                "currentBranch" to "stable-kotlin",
                                "currentSha" to "d349508fbf0e94bf2f76435fcc8534cfa2bea380",
                                "librariesFormatVersion" to "2",
                                "jvmTargetForSnippets" to "17"
                        )
                )
        )
    }

    companion object {
        fun resolveIntellij(): LibraryResolver {
            val lib = "intellij" to """
            {
                "imports": [
                    "com.intellij.openapi.ui.Messages.*"
                ],
                "init": []
            }
                """.trimIndent()

            var a = listOf(lib).toLibraries()
            return a
        }
    }
//    private fun resolveIntellij(): LibraryResolver? {
//        return object : LibraryResolver {
//            override fun resolve(reference: LibraryReference, arguments: List<Variable>): LibraryDefinition? {
//                return try {
//
//                    val classLoader = Thread.currentThread().contextClassLoader
//                    val libraryClass = classLoader.loadClass(reference.key)
//                    return li
//                } catch (e: ClassNotFoundException) {
//                }
//            }
//        }
//    }

    fun eval(code: Code, jupyterId: Int = -1, storeHistory: Boolean = true): EvalResultEx {
        return repl.evalEx(EvalRequestData(code, jupyterId, storeHistory))
    }

}