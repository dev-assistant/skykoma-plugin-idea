package cn.hylstudio.skykoma.plugin.idea.jupyter

import kotlin.reflect.KClass
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.GetScriptingClassByClassLoader
import kotlin.script.experimental.jvm.JvmGetScriptingClass

class ContextClassLoaderAwareScriptClassGetter(
    private val fallbackClassLoader: ClassLoader,
) : GetScriptingClassByClassLoader {
    private val delegate = JvmGetScriptingClass()

    override fun invoke(
        classType: KotlinType,
        contextClass: KClass<*>,
        hostConfiguration: ScriptingHostConfiguration,
    ): KClass<*> {
        val cl = contextClass.java.classLoader ?: fallbackClassLoader
        return delegate(classType, cl, hostConfiguration)
    }

    override fun invoke(
        classType: KotlinType,
        contextClassLoader: ClassLoader?,
        hostConfiguration: ScriptingHostConfiguration,
    ): KClass<*> {
        val cl = fallbackClassLoader
        return delegate(classType, cl, hostConfiguration)
    }
}
