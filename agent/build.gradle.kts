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
    implementation("net.bytebuddy:byte-buddy:1.18.14")
    implementation("net.bytebuddy:byte-buddy-agent:1.18.14")

    // Shared pure-Java model/codec/config. common has no Kotlin runtime, so depending
    // on it keeps the agent's classpath isolated from the host IDE.
    implementation(project(":common"))

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }

    // The agent ships only as the shaded fat jar (agent.jar). The plain jar task
    // produces a thin jar nobody uses, so disable it: it avoids an unused artifact
    // and the path clash with shadowJar (both default to agent.jar).
    jar {
        enabled = false
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
                    "Can-Retransform-Classes" to "true",
                ),
            )
        }
    }
}
