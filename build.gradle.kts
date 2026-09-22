import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    // Root project as an aggregator; module-specific plugins are applied in subprojects.
    kotlin("jvm") version "2.2.21" apply false
    id("org.jetbrains.intellij.platform") version "2.10.5" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
    // Code formatting/linting, applied to every module below.
    id("com.diffplug.spotless") version "7.0.4"
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

    // Single Spotless setup for all modules. Rules are applied per language so the
    // Java-only modules (common, agent) and the Kotlin modules (plugin, examples)
    // each get the right formatter. `spotlessCheck` runs in CI; `spotlessApply` fixes.
    apply(plugin = "com.diffplug.spotless")
    configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            // Palantir keeps the existing 4-space indentation, so the initial
            // reformat stays a small diff compared to Google Java Format.
            palantirJavaFormat()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlin {
            target("src/**/*.kt")
            // Disable the filename rule: IntelliJ plugin conventions frequently keep
            // a listener interface in a file named after its notifier counterpart.
            ktlint().editorConfigOverride(mapOf("ktlint_standard_filename" to "disabled"))
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
        }
    }
}

