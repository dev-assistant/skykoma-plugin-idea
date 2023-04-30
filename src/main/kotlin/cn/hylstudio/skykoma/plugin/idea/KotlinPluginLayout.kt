package cn.hylstudio.skykoma.plugin.idea// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.openapi.application.PathManager
import java.io.File

object KotlinPluginLayout {
    /**
     * Directory with the bundled Kotlin compiler distribution. Includes the compiler itself and a set of compiler plugins
     * with a compatible version.
     */
    @JvmStatic
    val kotlinc: File
        get() = kotlincProvider.value

    private val kotlincProvider: Lazy<File>

    init {
        val jarInsideLib = PathManager.getJarPathForClass(KotlinPluginLayout::class.java)
                ?.let { File(it) }
                ?: error("Can't find jar file for ${KotlinPluginLayout::class.simpleName}")

        check(jarInsideLib.extension == "jar") { "$jarInsideLib should be jar file" }

        val kotlinPluginRoot = jarInsideLib
                .parentFile
                .also { check(it.name == "lib") { "$it should be lib directory" } }
                .parentFile

        fun resolve(path: String) = kotlinPluginRoot.resolve(path).also { check(it.exists()) { "$it doesn't exist" } }

        kotlincProvider = lazy { resolve("kotlinc") }
    }
}