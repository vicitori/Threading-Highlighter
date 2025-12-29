plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = "io.github.vicitori.threading.highlighter.examples"
version = "0.0.1"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2025.3.1")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("253")
            untilBuild.set("253.*")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    runIde {
        jvmArgs(
            "-javaagent:${project.rootProject.projectDir}/agent/build/libs/agent.jar",
            "-Dthreading.highlighter.trace.dir=${project.rootProject.projectDir}/examples",
            "-Dthreading.highlighter.user.package=io.github.vicitori.threading.highlighter.examples"
        )
    }
}
