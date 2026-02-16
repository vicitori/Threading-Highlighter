plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

group = "io.github.vicitori.threading.highlighter.examples"
version = "0.0.1"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":common"))
    intellijPlatform {
        intellijIdeaUltimate("2025.3.1")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "253.*"
        }
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    runIde {
        dependsOn(":agent:shadowJar", ":plugin:buildPlugin")

        // Run IDE with agent to capture markers
        jvmArgs(
            "-javaagent:${project.rootProject.projectDir}/agent/build/libs/agent.jar"
        )

        // Set the project base directory for agent to construct traces path
        systemProperty(
            "threading.highlighter.project.dir", "${project.projectDir}"
        )
    }
}
