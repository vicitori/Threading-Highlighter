plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.21"
    id("org.jetbrains.intellij.platform")
}

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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    intellijPlatform {
        intellijIdeaUltimate("2025.3.1")
        bundledPlugin("org.jetbrains.kotlin")
    }

    // Pure-logic unit tests (no platform runtime needed)
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // IntelliJ Platform test harness expects JUnit 4 on the classpath; the vintage
    // engine satisfies it and coexists with the Jupiter tests above
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testRuntimeOnly("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "253.*"
        }
    }

    // Used by the CI `verify` job to check compatibility against target IDEs
    pluginVerification {
        ides {
            recommended()
        }
    }

    // Credentials are supplied by the CI `release` job via environment variables
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

// Output of the agent's shadow jar; using the task provider lets Gradle wire the
// task dependency automatically.
val agentShadowJar = project(":agent").tasks.named("shadowJar")

tasks {
    named("buildPlugin") {
        dependsOn(":agent:shadowJar")
    }
    test {
        useJUnitPlatform()
    }

    // Bundle the agent jar inside the plugin distribution (lib/), so users get it
    // together with the plugin from Marketplace instead of building it by hand.
    withType<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask> {
        from(agentShadowJar) {
            into("${pluginName.get()}/lib")
        }
    }
}
