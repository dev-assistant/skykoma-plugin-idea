package cn.hylstudio.skykoma.plugin.idea


import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jetbrains.kotlinx.jupyter.api.CodeEvaluator
import org.jetbrains.kotlinx.jupyter.api.EmbeddedKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.rawMessageCallback
import org.jetbrains.kotlinx.jupyter.closeIfPossible
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.RuntimeKernelProperties
import org.jetbrains.kotlinx.jupyter.config.createRuntimeProperties
import org.jetbrains.kotlinx.jupyter.createMessageHandler
import org.jetbrains.kotlinx.jupyter.createReplSettings
import org.jetbrains.kotlinx.jupyter.libraries.DefaultResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.messaging.*
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.ResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.startup.DEFAULT
import org.jetbrains.kotlinx.jupyter.startup.KernelArgs
import org.jetbrains.kotlinx.jupyter.startup.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.startup.getConfig
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.concurrent.thread
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
//            val libraryInfoProvider = getDefaultClasspathResolutionInfoProvider()
            val kernelConfig = kernelArgs.getConfig()
            val replConfig = ReplConfig.create(
                resolutionInfoProviderFactory = DefaultResolutionInfoProviderFactory,
                homeDir = kernelArgs.homeDir
            )
            val runtimeProperties = createRuntimeProperties(kernelConfig, defaultProperties = defaultRuntimeProperties)
//            kernelServer(kernelConfig, replConfig, runtimeProperties)
            embedKernelIdea(kernelArgs, replConfig, runtimeProperties, null)
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

        return KernelArgs(
            cfgFileValue,
            classpath ?: emptyList(),
            homeDir,
            debugPort,
            clientType,
            jvmTargetForSnippets,
            ReplCompilerMode.K1
//            ReplCompilerMode.K2
        )
    }

    fun getSystemClassPath(): List<File>? {
        val systemClassLoader = ClassLoader.getSystemClassLoader()

        val cp = classpathFromClassloader(systemClassLoader)

        if (cp != null) {
            log.info("systemClassLoader classpath: " + cp.joinToString())
        }
        return cp
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
        kernelConfigTmp: KernelArgs,
        replConfigTmp: ReplConfig,
        replRuntimePropertiesTmp: ReplRuntimeProperties,
        resolutionInfoProviderFactory: ResolutionInfoProviderFactory?,
        scriptReceivers: List<Any>? = null
    ) {
        val processCp = System.getProperty("java.class.path").split(File.pathSeparator).toTypedArray().map { File(it) }
        val systemClassPath = getSystemClassPath() ?: emptyList()
        val ideaCp1 =
            scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true, classLoader = pluginClassLoader)
        log.info("ideaCp1: " + ideaCp1.joinToString())
        val ideaCp2 = KotlinJars.kotlinScriptStandardJars
        log.info("ideaCp2: " + ideaCp2.joinToString())
        val cp = systemClassPath + ideaCp1 + ideaCp2
        val scriptClassPath = cp.distinct()
//        val kernelConfig = KernelArgs(kernelConfigTmp.cfgFile, cp, null, kernelConfigTmp.debugPort, kernelConfigTmp.clientType, kernelConfigTmp.jvmTargetForSnippets).getConfig()
        val kernelConfig = KernelArgs(
            cfgFile = kernelConfigTmp.cfgFile,
            scriptClasspath=scriptClassPath,
            homeDir = null,
            debugPort = null,
            clientType = null,
            jvmTargetForSnippets = null,
            replCompilerMode = ReplCompilerMode.DEFAULT,
        ).getConfig()

        val replSettings =
            createReplSettings(
                DefaultKernelLoggerFactory,
                EmbeddedKernelRunMode,
                kernelConfig,
                resolutionInfoProviderFactory,
                scriptReceivers,
            )
        startZmqServer(replSettings)
    }

    fun startZmqServer(replSettings: DefaultReplSettings) {
        val kernelConfig = replSettings.kernelConfig
        val loggerFactory = replSettings.loggerFactory
        val logger = loggerFactory.getLogger(org.jetbrains.kotlinx.jupyter.iKotlinClass)
        logger.info("Starting server with config: $kernelConfig")

        JupyterConnectionImpl(loggerFactory, kernelConfig).use { conn: JupyterConnectionInternal ->
//            org.jetbrains.kotlinx.jupyter.printClassPath(logger)

            logger.info("Begin listening for events")

            val socketManager = conn.socketManager
            val messageHandler = createMessageHandler(replSettings, socketManager)
            initializeKernelSession(messageHandler, replSettings)

            val mainThread = Thread.currentThread()

            fun socketLoop(
                interruptedMessage: String,
                vararg threadsToInterrupt: Thread,
                loopBody: () -> Unit,
            ) {
                while (true) {
                    try {
                        loopBody()
                    } catch (_: InterruptedException) {
                        logger.debug(interruptedMessage)
                        threadsToInterrupt.forEach { it.interrupt() }
                        break
                    }
                }
            }

            fun JupyterConnection.addMessageCallbackForSocket(socketType: JupyterSocketType) {
                addMessageCallback(
                    rawMessageCallback(socketType, null) { rawMessage ->
                        messageHandler.handleMessage(socketType, rawMessage)
                    },
                )
            }

            conn.addMessageCallbackForSocket(JupyterSocketType.CONTROL)
            conn.addMessageCallbackForSocket(JupyterSocketType.SHELL)

            val controlThread =
                thread {
                    socketLoop("Control: Interrupted", mainThread) {
                        socketManager.control.runCallbacksOnMessage()
                    }
                }

            val hbThread =
                thread {
                    socketLoop("Heartbeat: Interrupted", mainThread) {
                        socketManager.heartbeat.onData { send(it) }
                    }
                }

            socketLoop("Main: Interrupted", controlThread, hbThread) {
                socketManager.shell.runCallbacksOnMessage()
            }

            try {
                controlThread.join()
                hbThread.join()
            } catch (_: InterruptedException) {
            } finally {
                messageHandler.closeIfPossible()
            }

            logger.info("Server is stopped")
        }
    }
}

private fun initializeKernelSession(
    messageHandler: MessageHandlerImpl,
    replSettings: DefaultReplSettings,
) {
    val codeEvaluator =
        CodeEvaluator { code ->
            val executeRequest =
                ExecuteRequest(
                    code,
                    storeHistory = false,
                )
            val messageData =
                MessageData(
                    header = makeHeader(MessageType.EXECUTE_REQUEST),
                    content = executeRequest,
                )
            val message =
                Message(
                    data = messageData,
                )
            messageHandler.handleMessage(
                JupyterSocketType.SHELL,
                message.toRawMessage(),
            )
        }

    replSettings.replConfig.kernelRunMode.initializeSession(
        messageHandler.repl.notebook,
        codeEvaluator,
    )
}
//        val replConfig = ReplConfig.create(
//            resolutionInfoProviderFactory = resolutionInfoProviderFactory ?: DefaultResolutionInfoProviderFactory,
//            loggerFactory = DefaultKernelLoggerFactory,
//            homeDir = kernelConfig.homeDir,
//            kernelRunMode = EmbeddedKernelRunMode,
//            scriptReceivers = scriptReceivers
//        )
//
//    fun kernelServer(
//        kernelConfig: KernelConfig,
//        replConfig: ReplConfig,
//        runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties,
//        scriptReceivers: List<Any> = emptyList()
//    ): ReplForJupyter {
//        printClassPath()
//        val openSocketAction: (JupyterSocketInfo, ZMQ.Context) -> JupyterSocket = { jupyterSocketInfo, zmqContext ->
//            SocketWrapper(
//                loggerFactory = DefaultKernelLoggerFactory,
//                name = jupyterSocketInfo.name,
//                socket = zmqContext.socket(jupyterSocketInfo.zmqType(JupyterSocketSide.SERVER)),
//                address = kernelConfig.addressForSocket(jupyterSocketInfo),
//                hmac = kernelConfig.hmac
//            )
//        }
//        val socketManager = JupyterSocketManagerImpl(
//            15.seconds, openSocketAction
//        )
//        val communicationFacility: JupyterCommunicationFacility = JupyterCommunicationFacilityImpl(
//            socketManager, MessageFactoryProviderImpl()
//        )
//        val componentsProvider =
//            object : ReplComponentsProviderBase() {
//                override fun provideResolutionInfoProvider() = replConfig.resolutionInfoProvider
//                override fun provideScriptClasspath() = kernelConfig.scriptClasspath
//                override fun provideHomeDir() = kernelConfig.homeDir
//                override fun provideMavenRepositories() = replConfig.mavenRepositories
//                override fun provideLibraryResolver() = replConfig.libraryResolver
//                override fun provideRuntimeProperties() = runtimeProperties
//                override fun provideScriptReceivers() = scriptReceivers
//                override fun provideKernelRunMode() = replConfig.kernelRunMode
//                override fun provideDisplayHandler() = NoOpDisplayHandler
//                override fun provideCommunicationFacility(): JupyterCommunicationFacility = communicationFacility
//                override fun provideDebugPort(): Int? = kernelConfig.debugPort
//                override fun provideHttpClient() = replConfig.httpUtil.httpClient
//                override fun provideLibraryDescriptorsManager() = replConfig.httpUtil.libraryDescriptorsManager
//                override fun provideLibraryInfoCache() = replConfig.httpUtil.libraryInfoCache
//                override fun provideLibraryReferenceParser() = replConfig.httpUtil.libraryReferenceParser
//                override fun provideInMemoryReplResultsHolder() = NoOpInMemoryReplResultsHolder
//                override fun provideReplCompilerMode(): ReplCompilerMode = kernelConfig.replCompilerMode
//            }
//        val replForJupyter: ReplForJupyter = componentsProvider.createRepl()
////        replForJupyter.closeIfPossible()
//        return replForJupyter
//    }
