package cn.hylstudio.skykoma.plugin.idea


import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jetbrains.kotlinx.jupyter.*
import org.slf4j.LoggerFactory

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.rawMessageCallback
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.getDefaultClasspathResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import org.jetbrains.kotlinx.jupyter.messaging.controlMessagesHandler
import org.jetbrains.kotlinx.jupyter.messaging.shellMessagesHandler
import org.jetbrains.kotlinx.jupyter.repl.creating.DefaultReplFactory
import org.jetbrains.kotlinx.jupyter.startup.KernelArgs
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.getConfig
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvm.util.KotlinJars
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
            "currentBranch" to "skykoma-plugin",
            "currentSha" to "skykoma-plugin",
            "jvmTargetForSnippets" to "17",
        )
        RuntimeKernelProperties(defaultMap)
    }

    fun main(vararg args: String) {
        try {
            log.info("Kernel args: " + args.joinToString { it })
            val kernelArgs = parseCommandLine(*args)
            val libraryInfoProvider = getDefaultClasspathResolutionInfoProvider()
            val kernelConfig = kernelArgs.getConfig()
            val replConfig = ReplConfig.create(libraryInfoProvider, kernelArgs.homeDir)
            val runtimeProperties = createRuntimeProperties(kernelConfig, defaultProperties = defaultRuntimeProperties)
//            kernelServer(kernelConfig, replConfig, runtimeProperties)
            embedKernelIdea(kernelArgs, replConfig, runtimeProperties,null)
        } catch (e: Exception) {
            log.error("exception running kernel with args: \"${args.joinToString()}\"", e)
        }
    }

    private fun parseCommandLine(vararg args: String): KernelArgs {
        var cfgFile: File? = null
        var classpath: List<File>? = null
        var homeDir: File? = null
        var debugPort: Int? = null
        var clientType: String? = null
        var jvmTargetForSnippets: String? = null
        args.forEach { arg ->
            when {
                arg.startsWith("-cp=") || arg.startsWith("-classpath=") -> {
                    classpath?.let {
                        throw IllegalArgumentException("classpath already set to ${it.joinToString(File.pathSeparator)}")
                    }
                    classpath = arg.substringAfter('=').split(File.pathSeparator).map { File(it) }
                }

                arg.startsWith("-home=") -> {
                    homeDir = File(arg.substringAfter('='))
                }

                arg.startsWith("-debugPort=") -> {
                    debugPort = arg.substringAfter('=').toInt()
                }

                arg.startsWith("-client=") -> {
                    clientType = arg.substringAfter('=')
                }

                arg.startsWith("-jvmTarget") -> {
                    jvmTargetForSnippets = arg.substringAfter('=')
                }

                else -> {
                    cfgFile?.let { throw IllegalArgumentException("config file already set to $it") }
                    cfgFile = File(arg)
                }
            }
        }
        val cfgFileValue = cfgFile ?: throw IllegalArgumentException("config file is not provided")
        if (!cfgFileValue.exists() || !cfgFileValue.isFile) throw IllegalArgumentException("invalid config file $cfgFileValue")

        return KernelArgs(cfgFileValue, classpath ?: emptyList(), homeDir, debugPort, clientType, jvmTargetForSnippets)
    }

    fun printClassPath() {
        val cl = ClassLoader.getSystemClassLoader()

        val cp = classpathFromClassloader(cl)

        if (cp != null) {
            log.info("Current classpath: " + cp.joinToString())
        }
    }

    /**
     * This function is to be run in projects which use kernel as a library,
     * so we don't have a big need in covering it with tests
     *
     * The expected use case for this function is embedding into a Java application that doesn't necessarily support extensions written in Kotlin
     * The signature of this function should thus be simple, and e.g. allow resolutionInfoProvider to be null instead of having to pass EmptyResolutionInfoProvider
     * because EmptyResolutionInfoProvider is a Kotlin singleton object, and it takes a while to understand how to use it from Java code.
     */
    @Suppress("unused")
    fun embedKernelIdea(
        kernelConfigTmp:KernelArgs,
        replConfigTmp:ReplConfig,
        replRuntimePropertiesTmp:ReplRuntimeProperties,
        resolutionInfoProvider: ResolutionInfoProvider?,
        scriptReceivers: List<Any>? = null
    ) {
        val processCp = System.getProperty("java.class.path").split(File.pathSeparator).toTypedArray().map { File(it) }
        val ideaCp1 = scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true, classLoader = pluginClassLoader)
        val ideaCp2 = KotlinJars.kotlinScriptStandardJars
        val cp = ideaCp1 + ideaCp2
//        val kernelConfig = KernelArgs(kernelConfigTmp.cfgFile, cp, null, kernelConfigTmp.debugPort, kernelConfigTmp.clientType, kernelConfigTmp.jvmTargetForSnippets).getConfig()
        val kernelConfig = KernelArgs(kernelConfigTmp.cfgFile, cp, null, null, null, null).getConfig()

        val replConfig = ReplConfig.create(
            resolutionInfoProvider ?: EmptyResolutionInfoProvider,
            null,
            true,
        )
        kernelServer(kernelConfig, replConfig, scriptReceivers = scriptReceivers ?: emptyList())
    }

    fun kernelServer(
        kernelConfig: KernelConfig,
        replConfig: ReplConfig,
        runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties,
        scriptReceivers: List<Any> = emptyList()
    ) {
        log.info("Starting server with config: $kernelConfig")

        JupyterConnectionImpl(kernelConfig).use { conn: JupyterConnectionInternal ->

            printClassPath()

            log.info("Begin listening for events")

            val executionCount = AtomicLong(1)

            val commManager = CommManagerImpl(conn)
            val repl = DefaultReplFactory(
                kernelConfig,
                replConfig,
                runtimeProperties,
                scriptReceivers,
                conn,
                commManager
            ).createRepl()

            val mainThread = Thread.currentThread()

            fun socketLoop(
                interruptedMessage: String,
                vararg threadsToInterrupt: Thread,
                loopBody: () -> Unit,
            ) {
                while (true) {
                    try {
                        loopBody()
                    } catch (e: InterruptedException) {
                        log.debug(interruptedMessage)
                        threadsToInterrupt.forEach { it.interrupt() }
                        break
                    }
                }
            }

            conn.addMessageCallback(
                rawMessageCallback(JupyterSocketType.CONTROL, null) { rawMessage ->
                    conn.controlMessagesHandler(rawMessage, repl)
                },
            )

            conn.addMessageCallback(
                rawMessageCallback(JupyterSocketType.SHELL, null) { rawMessage ->
                    conn.updateSessionInfo(rawMessage)
                    conn.shellMessagesHandler(rawMessage, repl, commManager, executionCount)
                },
            )

            val controlThread = thread {
                socketLoop("Control: Interrupted", mainThread) {
                    conn.control.runCallbacksOnMessage()
                }
            }

            val hbThread = thread {
                socketLoop("Heartbeat: Interrupted", mainThread) {
                    conn.heartbeat.onData { socket.send(it, 0) }
                }
            }

            socketLoop("Main: Interrupted", controlThread, hbThread) {
                conn.shell.runCallbacksOnMessage()
            }

            try {
                controlThread.join()
                hbThread.join()
            } catch (_: InterruptedException) {
            }

            log.info("Shutdown server")
        }
    }

}