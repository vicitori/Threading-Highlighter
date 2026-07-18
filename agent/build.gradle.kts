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

    // Shared pure-Java model/codec/config. common has no Kotlin runtime, so depending
    // on it does not break the agent's classpath isolation from the host IDE (A1).
    implementation(project(":common"))

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }

    // The agent ships only as the shaded fat jar (agent.jar). Give the thin jar a
    // classifier so it does not write to the same agent.jar path as shadowJar, which
    // otherwise makes consumers (e.g. the plugin sandbox) ambiguous about the producer.
    jar {
        archiveClassifier.set("thin")
    }

    shadowJar {
        archiveClassifier.set("")

        // Relocate Byte Buddy to avoid classpath conflicts with the host application (e.g. IntelliJ IDE)
        relocate("net.bytebuddy", "io.github.vicitori.shaded.bytebuddy")

        manifest {
            attributes(
                mapOf(
                    // Premain-Class: -javaagent at JVM startup; Agent-Class: attach to a running JVM
                    "Premain-Class" to "io.github.vicitori.threading.highlighter.agent.ThreadingHighlighterAgent",
                    "Agent-Class" to "io.github.vicitori.threading.highlighter.agent.ThreadingHighlighterAgent",
                    "Can-Redefine-Classes" to "true",
                    "Can-Retransform-Classes" to "true"
                )
            )
        }
    }
}
