plugins {
    java
    id("com.gradleup.shadow")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("net.bytebuddy:byte-buddy:1.15.11")
    implementation("net.bytebuddy:byte-buddy-agent:1.15.11")

    // Round-trip tests decode the agent's hand-written JSON with the same model and
    // library the plugin uses, so the two sides stay in sync.
    testImplementation(project(":common"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("")

        // Relocate Byte Buddy to avoid classpath conflicts with the host application (e.g. IntelliJ IDE)
        relocate("net.bytebuddy", "io.github.vicitori.shaded.bytebuddy")

        manifest {
            attributes(
                mapOf(
                    "Premain-Class" to "io.github.vicitori.threading.highlighter.agent.ThreadingHighlighterAgent",
                    "Can-Redefine-Classes" to "true",
                    "Can-Retransform-Classes" to "true"
                )
            )
        }
    }
}
