package cn.hylstudio.skykoma.plugin.idea.jupyter

import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.slf4j.LoggerFactory
import java.util.ServiceLoader
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.host.with

class PatchedCompilerServiceProvider(
    private val fallbackClassLoader: ClassLoader,
) : CompilerServiceProvider {
    private val log = LoggerFactory.getLogger(this::class.java)

    override val priority: Int = 1000

    override fun createCompiler(
        params: CompilerParams,
        callbacks: KernelCallbacks,
        loggerFactory: KernelLoggerFactory,
    ): CompilerService {
        val delegate = loadDelegateProvider()
        log.info("Using delegate CompilerServiceProvider: ${delegate.javaClass.name} (priority=${delegate.priority})")
        val realService = delegate.createCompiler(params, callbacks, loggerFactory)
        try {
            patchCompilationConfig(realService)
        } catch (t: Throwable) {
            log.error("Failed to patch CompilerServiceImpl.compilationConfig", t)
        }
        return realService
    }

    private fun loadDelegateProvider(): CompilerServiceProvider {
        val providers =
            ServiceLoader
                .load(CompilerServiceProvider::class.java, fallbackClassLoader)
                .toList()
                .filter { it !is PatchedCompilerServiceProvider }
        return providers.maxByOrNull { it.priority }
            ?: error("No CompilerServiceProvider found via SPI in classloader $fallbackClassLoader")
    }

    private fun patchCompilationConfig(realService: CompilerService) {
        val cls = realService.javaClass
        val field =
            try {
                cls.getDeclaredField("compilationConfig")
            } catch (_: NoSuchFieldException) {
                log.warn("compilationConfig field not found in ${cls.name}; skipping patch")
                return
            }
        field.isAccessible = true
        val original = field.get(realService) as ScriptCompilationConfiguration
        val customGetter = ContextClassLoaderAwareScriptClassGetter(fallbackClassLoader)
        val patched =
            original.with {
                hostConfiguration.update {
                    it.with {
                        getScriptingClass(customGetter)
                    }
                }
            }
        field.set(realService, patched)
        log.info("Patched ${cls.name}.compilationConfig with ContextClassLoaderAwareScriptClassGetter, fallback=$fallbackClassLoader")
    }
}

