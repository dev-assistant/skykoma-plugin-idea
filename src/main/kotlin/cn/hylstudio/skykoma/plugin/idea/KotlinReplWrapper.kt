package cn.hylstudio.skykoma.plugin.idea


import cn.hylstudio.skykoma.plugin.idea.jupyter.PatchedCompilerServiceProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.kotlinx.jupyter.api.EmbeddedKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.api.embedded.InMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.RuntimeKernelProperties
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutorImpl
import org.jetbrains.kotlinx.jupyter.libraries.DefaultResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacilityImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProviderImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageHandlerImpl
import org.jetbrains.kotlinx.jupyter.messaging.comms.server.ServerCommCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import org.jetbrains.kotlinx.jupyter.parseCommandLine
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.comms.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.jetbrains.kotlinx.jupyter.protocol.startup.getConfig
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelArgs
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.creating.DefaultReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.repl.embedded.NoOpInMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.startup.JupyterServerRunner
import org.jetbrains.kotlinx.jupyter.startup.KernelJupyterParamsSerializer
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParams
import org.jetbrains.kotlinx.jupyter.util.closeWithTimeout
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlin.script.experimental.jvm.util.KotlinJars
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrStdlib
import kotlin.time.Duration.Companion.seconds


class KotlinReplWrapper(private val pluginClassLoader: ClassLoader) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    val iKotlinClass: Class<*> = object {}::class.java.enclosingClass

    @Volatile
    var scriptClassPath: List<File> = emptyList()
        private set

    @Volatile
    var systemClassPath: List<File> = emptyList()
        private set

    @Volatile
    var pluginClassPath: List<File> = emptyList()
        private set

    @Volatile
    var extraClassPath: List<File> = emptyList()
        private set

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
            val systemClassPath = getSystemClassLoaderClassPath() ?: emptyList()
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
            this.scriptClassPath = scriptClassPath
            this.systemClassPath = systemClassPath
            this.pluginClassPath = (ideaCp1 + ideaCp2).distinct()
            this.extraClassPath = extraClasspath
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

    //force patch PatchedCompilerServiceProvider
    private fun runServer(replSettings: DefaultReplSettings) {
        val kernelConfig = replSettings.kernelConfig
        val loggerFactory = replSettings.loggerFactory
        val logger = loggerFactory.getLogger(iKotlinClass)
        val ports = kernelConfig.jupyterParams.ports
        val serverRunner =
            JupyterServerRunner.instances
                .find { it.canRun(ports) }
                ?: error("No server runner found for ports $ports")
        logger.debug(
            "Starting server with config: {} (using {} server runner)",
            kernelConfig,
            serverRunner.javaClass.simpleName,
        )

        val interruptionFuture = CompletableFuture<Unit>()
        val closeableResources = mutableListOf<Closeable>()
        tryFinally(
            action = {
                serverRunner.start(
                    jupyterParams = kernelConfig.jupyterParams,
                    loggerFactory = loggerFactory,
                    setup = { sockets ->
                        logger.debug("Begin listening for events")

                        val messageHandler = createMessageHandler(replSettings, sockets)
                        closeableResources.add(messageHandler)
                        replSettings.replConfig.kernelRunMode.initializeSession(
                            messageHandler.repl.notebook,
                            org.jetbrains.kotlinx.jupyter.api.CodeEvaluator { code ->
                                val executeRequest =
                                    org.jetbrains.kotlinx.jupyter.messaging.ExecuteRequest(
                                        code,
                                        storeHistory = false,
                                    )
                                val messageData =
                                    org.jetbrains.kotlinx.jupyter.messaging.MessageData(
                                        header = org.jetbrains.kotlinx.jupyter.messaging.makeHeader(
                                            org.jetbrains.kotlinx.jupyter.messaging.MessageType.EXECUTE_REQUEST,
                                        ),
                                        content = executeRequest,
                                    )
                                val message =
                                    org.jetbrains.kotlinx.jupyter.messaging.Message(
                                        data = messageData,
                                    )
                                messageHandler.handleMessage(
                                    JupyterSocketType.SHELL,
                                    message.toRawMessage(),
                                )
                            },
                        )

                        sockets.control.onRawMessage {
                            try {
                                messageHandler.handleMessage(JupyterSocketType.CONTROL, it)
                            } catch (_: InterruptedException) {
                                interruptionFuture.complete(Unit)
                            }
                        }

                        sockets.shell.onRawMessage {
                            try {
                                messageHandler.handleMessage(JupyterSocketType.SHELL, it)
                            } catch (_: InterruptedException) {
                                interruptionFuture.complete(Unit)
                            }
                        }
                    },
                    registerCloseable = closeableResources::add,
                )
                try {
                    interruptionFuture.get()
                } catch (_: InterruptedException) {
                    // ignore
                }
            },
            finally = {
                closeWithTimeout(
                    timeoutMs = 15.seconds.inWholeMilliseconds,
                    doClose = {
                        for (closeable in closeableResources.asReversed()) {
                            try {
                                closeable.close()
                            } catch (t: Throwable) {
                                log.warn("Failed to close resource: $closeable", t)
                            }
                        }
                    },
                )
            },
        )
    }

    private fun createMessageHandler(
        replSettings: DefaultReplSettings,
        socketManager: JupyterServerSockets,
    ): MessageHandlerImpl {
        val loggerFactory = replSettings.loggerFactory
        val messageFactoryProvider = MessageFactoryProviderImpl()
        val communicationFacility: JupyterCommunicationFacility =
            JupyterCommunicationFacilityImpl(socketManager, messageFactoryProvider)
        val executor = JupyterExecutorImpl(loggerFactory)
        val commCommunicationFacility = ServerCommCommunicationFacility(communicationFacility)
        val commManager = CommManagerImpl(commCommunicationFacility)

        val replProvider =
            object : DefaultReplComponentsProvider(
                replSettings,
                communicationFacility,
                commManager,
                NoOpInMemoryReplResultsHolder,
            ) {
                override fun provideForceCompilerServiceProvider(): CompilerServiceProvider =
                    PatchedCompilerServiceProvider(pluginClassLoader)

                override fun provideCompilerServiceSpiClassloader(): ClassLoader = pluginClassLoader
            }
        val repl = replProvider.createRepl()
        return MessageHandlerImpl(loggerFactory, repl, commManager, messageFactoryProvider, socketManager, executor)
    }


    fun getSystemClassLoaderClassPath(): List<File>? {
        val systemClassLoader = ClassLoader.getSystemClassLoader()

        val cp = classpathFromClassloader(systemClassLoader)

        if (cp != null) {
            log.info("systemClassLoader classpath: " + cp.joinToString())
        }
        return cp
    }
}