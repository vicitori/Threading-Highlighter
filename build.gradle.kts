plugins {
    // Root project as an aggregator; module-specific plugins are applied in subprojects.
    kotlin("jvm") version "2.2.21" apply false
    id("org.jetbrains.intellij.platform") version "2.10.5" apply false
    id("com.gradleup.shadow") version "8.3.6" apply false
}

// Single source of truth for coordinates. Applied to every module below so the
// plugin zip and the agent jar are versioned consistently (the IntelliJ Platform
// plugin picks up project.version as the plugin version). Keep this a release
// version without "-SNAPSHOT": the JetBrains Marketplace rejects snapshot builds.
val projectGroup = "io.github.vicitori.threading.highlighter"
val projectVersion = "0.1.0"

group = projectGroup
version = projectVersion

subprojects {
    group = projectGroup
    version = projectVersion

    repositories {
        mavenCentral()
    }
}

