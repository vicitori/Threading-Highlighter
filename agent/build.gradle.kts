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
}

tasks {
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
