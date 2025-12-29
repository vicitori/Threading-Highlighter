plugins {
    // Root project as an aggregator; module-specific plugins are applied in subprojects.
    kotlin("jvm") version "2.2.21" apply false
    id("org.jetbrains.intellij.platform") version "2.10.5" apply false
    id("com.gradleup.shadow") version "8.3.6" apply false
}

group = "io.github.vicitori.threading.highlighter"
version = "0.1.0-SNAPSHOT"

subprojects {
    repositories {
        mavenCentral()
    }
}

