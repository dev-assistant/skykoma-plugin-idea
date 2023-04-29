// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
//allprojects {
//    repositories {
//        mavenLocal()
//        maven {  setUrl("https://maven.aliyun.com/repository/public") }
//        maven { setUrl("https://nexus.bsdn.org/content/groups/public/") }
//        mavenCentral()
//    }
//
//    buildscript { 
//        repositories { 
//            maven { setUrl("https://maven.aliyun.com/repository/public")}
//            maven { setUrl("https://nexus.bsdn.org/content/groups/public/")}
//            maven { setUrl("https://plugins.gradle.org/m2/")}
//        }
//    }
//}

plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.13.0"
}

group = "cn.hylstudio.skykoma.plugin.idea"
version = "0.0.7"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version.set("2022.1.4")
    plugins.set(listOf("com.intellij.java"))
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
//    implementation("com.google.code.gson:gson:2.10.1")
//    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.projectlombok:lombok:1.18.22")
    annotationProcessor("org.projectlombok:lombok:1.18.22")
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
        version.set("${project.version}")
        sinceBuild.set("212")
        untilBuild.set("231.*")
    }
}
