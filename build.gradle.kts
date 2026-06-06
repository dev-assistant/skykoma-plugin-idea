plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.kotlin.jupyter.api") version "0.19.0-951"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

repositories {
    mavenLocal()
    intellijPlatform {
        defaultRepositories()
    }
}

group = "cn.hylstudio.skykoma.plugin.idea"
version = if (project.hasProperty("projVersion")) {
    project.findProperty("projVersion") as String
} else {
    "0.1.13"
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val jupyterApiVersion = "0.19.0-951"
val kotlinCompilerVersion = "2.3.10"
val vertxVersion = "4.3.2"
val lombokVersion = "1.18.38"

dependencies {
    intellijPlatform {
        intellijIdea("2026.1")
        bundledPlugins(
            listOf(
                "Git4Idea",
                "com.intellij.java",
                "org.jetbrains.idea.maven",
                "org.jetbrains.kotlin"
            )
        )
    }

    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    compileOnly("org.projectlombok:lombok:$lombokVersion")

    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    implementation("org.jetbrains.kotlinx:kotlin-jupyter-api:$jupyterApiVersion")
    implementation("org.jetbrains.kotlinx:kotlin-jupyter-kernel:$jupyterApiVersion")

    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinCompilerVersion")
    compileOnly("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinCompilerVersion")

    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-web-client:$vertxVersion")
}

tasks {
    withType {
        compileJava {
            options.encoding = "UTF-8"
        }
    }
    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        pluginVersion.set("${project.version}")
        sinceBuild.set("242")
    }

    prepareSandbox {
        doLast {
            val libDir = file(listOf(destinationDir, project.name, "lib").joinToString(File.separator))
            val conflictingPrefixes = listOf(
                "kotlin-stdlib-", "kotlin-reflect-", "kotlin-script-runtime-",
                "kotlinx-serialization-", "kotlinx-coroutines-",
                "kotlin-daemon-embeddable-",
                "kotlin-build-tools-api-", "kotlin-serialization-compiler-plugin-"
            )
            libDir.listFiles()?.forEach { jar ->
                val name = jar.name
                if (conflictingPrefixes.any { name.startsWith(it) }) {
                    println("Deleting conflicting JAR: $name")
                    jar.delete()
                }
            }

            val productInfoSrc = platformPath.resolve("product-info.json").toFile()
            val sandboxRoot = destinationDir.parentFile
            val productInfoDest = sandboxRoot.resolve("product-info.json")
            if (productInfoSrc.exists() && !productInfoDest.exists()) {
                copy {
                    from(productInfoSrc)
                    into(sandboxRoot)
                }
                println("Copied product-info.json to sandbox root: $sandboxRoot")
            }
        }
    }
}
