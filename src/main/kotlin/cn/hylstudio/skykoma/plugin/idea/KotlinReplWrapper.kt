package cn.hylstudio.skykoma.plugin.idea


import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.kotlin.utils.PathUtil
import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.EmbeddedKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.RuntimeKernelProperties
import org.jetbrains.kotlinx.jupyter.libraries.DefaultResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.parseCommandLine
import org.jetbrains.kotlinx.jupyter.protocol.startup.getConfig
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelArgs
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.runServer
import org.jetbrains.kotlinx.jupyter.startup.KernelJupyterParamsSerializer
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParams
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.script.experimental.jvm.util.KotlinJars
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrStdlib


class KotlinReplWrapper(private val pluginClassLoader: ClassLoader) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    val iKotlinClass: Class<*> = object {}::class.java.enclosingClass

    companion object {
        private var instance: KotlinReplWrapper? = null

        @JvmStatic
        fun getInstance(pluginClassLoader: ClassLoader): KotlinReplWrapper {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = KotlinReplWrapper(pluginClassLoader)
                    }
                }
            }
            return instance!!
        }
    }

    fun makeEmbeddedRepl(jsonArgs: String) {
        log.info("makeEmbeddedRepl, jsonArgs=[{}]", jsonArgs)
        val listType = object : TypeToken<ArrayList<String>>() {}.type
        val argsList: List<String> = Gson().fromJson(jsonArgs, listType)
        main(*argsList.toTypedArray()) // 使用 * 运算符传递变长参数
    }

    val defaultRuntimeProperties by lazy {
        val defaultMap = mapOf(
            "version" to "0.0.1",
            "kotlinVersion" to "3.1.10",
            "currentBranch" to "skykoma-plugin",
            "currentSha" to "skykoma-plugin",
            "jvmTargetForSnippets" to "21",
        )
        RuntimeKernelProperties(defaultMap)
    }

    fun main(vararg args: String) {
        val loggerFactory = DefaultKernelLoggerFactory
        try {
            log.debug("Kernel args: " + args.joinToString { it })
            val kernelArgs = parseCommandLine(*args)
            val processCp =
                System.getProperty("java.class.path").split(File.pathSeparator).toTypedArray().map { File(it) }
            val systemClassPath = getSystemClassPath() ?: emptyList()
            val ideaCp1 =
                scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true, classLoader = pluginClassLoader)
            log.info("ideaCp1: " + ideaCp1.joinToString())
            val ideaCp2 = KotlinJars.kotlinScriptStandardJars
            //compilerClasspath kotlin-compiler-embeddable-2.3.10.jar
            //compilerWithScriptingClasspath kotlin-compiler-embeddable-2.3.10.jar
            //stdlibOrNull kotlin-stdlib-2.4.0-dev-6891.jar
            //reflectOrNull  idea-2026.1-win\lib\intellij.libraries.kotlin.reflect.jar
            //scriptRuntimeOrNull kotlin-script-runtime-2.4.0-dev-6891.jar
            //kotlinScriptStandardJars  kotlin-stdlib-2.4.0-dev-6891.jar kotlin-script-runtime-2.4.0-dev-6891.jar"
            //kotlinScriptStandardJarsWithReflect idea-2026.1-win\lib\intellij.libraries.kotlin.reflect.jar"
            log.info("ideaCp2: " + ideaCp2.joinToString())
            val extraClasspathValue = PropertiesComponent.getInstance()
                .getValue(SkykomaConstants.JUPYTER_EXTRA_CLASSPATH, SkykomaConstants.JUPYTER_EXTRA_CLASSPATH_DEFAULT)
            val extraClasspath = extraClasspathValue.split(File.pathSeparator)
                .filter { it.isNotBlank() }
                .map { File(it.trim()) }
            val cp = systemClassPath + ideaCp1 + ideaCp2 + extraClasspath
            val scriptClassPath = cp.distinct()
            val kernelOwnParams =
                KotlinKernelOwnParams(
                    scriptClasspath = scriptClassPath,
                    homeDir = kernelArgs.ownParams.homeDir,
                    debugPort = kernelArgs.ownParams.debugPort,
                    clientType = kernelArgs.ownParams.clientType,
                    jvmTargetForSnippets = defaultRuntimeProperties.jvmTargetForSnippets,
                    replCompilerMode = ReplCompilerMode.K2,
                    extraCompilerArguments = emptyList(),
                )
            val kernelConfig =
                KernelArgs(
                    cfgFile = kernelArgs.cfgFile,
                    ownParams = kernelOwnParams,
                ).getConfig(KernelJupyterParamsSerializer)
            val replConfig =
                ReplConfig.create(
                    DefaultResolutionInfoProviderFactory,
                    loggerFactory,
                    kernelRunMode = EmbeddedKernelRunMode,
                    scriptReceivers = null,
                )
            val replSettings =
                DefaultReplSettings(
                    kernelConfig,
                    replConfig,
                    loggerFactory,
                    defaultRuntimeProperties,
                )
            runServer(replSettings)
        } catch (e: Exception) {
            log.error("exception running kernel with args: \"${args.joinToString()}\"", e)
        }
    }


    fun getSystemClassPath(): List<File>? {
        val systemClassLoader = ClassLoader.getSystemClassLoader()

        val cp = classpathFromClassloader(systemClassLoader)

        if (cp != null) {
            log.info("systemClassLoader classpath: " + cp.joinToString())
        }
        return cp
    }
}