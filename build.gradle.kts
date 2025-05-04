//import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.kotlin.konan.properties.loadProperties

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
//allprojects {
//    repositories {
//        mavenLocal()
//        maven {  setUrl("https://maven.aliyun.com/repository/public") }
//        maven { setUrl("https://nexus.bsdn.org/content/groups/public/") }
//            maven{setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies/")}
//        mavenCentral()
//    }
//
//    buildscript { 
//        repositories {
//            maven { setUrl("https://maven.aliyun.com/repository/public")}
//            maven { setUrl("https://nexus.bsdn.org/content/groups/public/")}
//            maven { setUrl("https://plugins.gradle.org/m2/")}
//            maven{setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies/")}
//        }
//    }
//}
repositories {
    mavenLocal()
    intellijPlatform {
        defaultRepositories()
    }
}
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.kotlin.jupyter.api") version "0.12.0-424"
//    id("org.jetbrains.intellij") version "1.13.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
//    id("org.jetbrains.intellij.platform.migration") version "2.5.0"
}

group = "cn.hylstudio.skykoma.plugin.idea"
version = if (project.hasProperty("projVersion")) {
    project.findProperty("projVersion") as String
} else {
    "0.1.0"
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
//        plugins()
//        bundledPlugins()
        bundledPlugins(
            listOf(
                "Git4Idea",
                "com.intellij.java",
                "org.jetbrains.idea.maven"
            )
        )
    }

    annotationProcessor("org.projectlombok:lombok:1.18.38")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
//    implementation("com.google.code.gson:gson:2.10.1")
//    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.jetbrains.kotlinx:kotlin-jupyter-api:0.12.0-424")
    implementation("org.jetbrains.kotlinx:kotlin-jupyter-kernel:0.12.0-424")

    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.23")
//    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-impl-embeddable:1.8.20")
//    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20")
//    implementation("org.jetbrains.kotlin:kotlin-script-runtime:1.8.20")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:1.9.23")
//    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-impl:1.8.20")
//    implementation("org.jetbrains.kotlin:kotlin-scripting-common:1.8.20")
//    implementation("org.jetbrains.kotlin:kotlin-compiler-fe10-for-ide:1.8.20")
//    implementation("org.jetbrains.kotlin:kotlin-compiler-common-for-ide:1.8.20")
//    implementation("org.jetbrains.kotlin:kotlin-compiler-fe10-for-ide:1.8.20") {
//        exclude(group = "*", module = "*")
//    }
//    implementation("org.jetbrains.kotlin:kotlin-compiler-common-for-ide:1.8.20") {
//        exclude(group = "*", module = "*")
//    }

    compileOnly("org.projectlombok:lombok:1.18.22")
    implementation("io.vertx:vertx-core:4.3.2")
    implementation("io.vertx:vertx-web:4.3.2")
    implementation("io.vertx:vertx-web-client:4.3.2")
}

tasks {
//    initializeIntelliJPlugin {
//        selfUpdateCheck.set(false)
//    }
    withType {
        compileJava {
            options.encoding = "UTF-8"
//            sourceCompatibility = "11"
//            targetCompatibility = "11"
        }
//        compileKotlin{
//            kotlinOptions {
//                jvmTarget = "11"
//            }
//        }
    }
    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        pluginVersion.set("${project.version}")
        sinceBuild.set("242")
//        untilBuild.set("251.*")
    }

//    verifyPluginConfiguration {
//        kotlinStdlibDefaultDependency.set(false)
//    }

//    prepareSandbox {
//        doLast {
//            val config = loadProperties(file("local.properties").path)
//            val bundleKotlincSrc = config.getProperty("bundle.kotlinc.path", dependencySrcKotlincPath())
//            val bundleKotlincDest = listOf(
//                    defaultDestinationDir.map { it.path }.getOrElse(""),
//                    project.name, "kotlinc"
//            ).joinToString(File.separator)
//            println("bundleKotlincSrc = \"$bundleKotlincSrc\"")
//            println("bundleKotlincDest = \"$bundleKotlincDest\"")
//            copy {
//                from(bundleKotlincSrc)
//                into(bundleKotlincDest)
//            }
//        }
//    }
//    publishPlugin {
//        var publishToken = ""
//        if (project.hasProperty("publishToken")) {
//            publishToken = project.findProperty("publishToken") as String
//        } else {
//            val localConfig = file("local.properties")
//            if (localConfig.exists()) {
//                val config = loadProperties(localConfig.path)
//                publishToken = config.getProperty("publishToken", "")
//            } else {
//                println("error: can't find public token")
//            }
//        }
//        token.set(publishToken)
//        host.set("https://github.com/dev-assistant/skykoma-plugin-idea")
//    }
}

//fun PrepareSandboxTask.dependencySrcKotlincPath(): String {
//    val pluginDependencies = pluginDependencies.get()
//    val dependencyPluginSrc = pluginDependencies.distinctBy { it.id }.map { it.artifact.parentFile }.firstOrNull()
//            ?: error("dependencyPluginSrc empty")
////            .gradle\caches\modules-2\files-2.1\com.jetbrains.intellij.idea\ideaIC\2023.1.1\770a0552545fe6ab0de3e2f8ac9adb7ea3046417\ideaIC-2023.1.1\plugins
//    return listOf(dependencyPluginSrc.path, "Kotlin", "kotlinc").joinToString(File.separator)
////    println(dependencyKotlincDir)
////    return dependencyKotlincDir
//}