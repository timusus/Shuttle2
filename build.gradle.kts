buildscript {
    repositories {
        google()
        jcenter()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
        maven(url = "https://maven.fabric.io/public")
    }
    dependencies {
        classpath(BuildPlugins.androidGradlePlugin)
        classpath(BuildPlugins.kotlinGradlePlugin)
        classpath(BuildPlugins.safeArgsPlugin)
        classpath(BuildPlugins.googleServicesPlugin)
        classpath(BuildPlugins.fabricPlugin)
    }
}

plugins {
    id(BuildPlugins.gradleVersionPlugin) version (BuildPlugins.Versions.gradleVersionPlugin)
}

allprojects {
    repositories {
        google()
        jcenter()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
        maven(url = "https://jitpack.io")
    }
}

tasks.register("clean").configure {
    delete("build")
}