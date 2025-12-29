plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("net.bytebuddy:byte-buddy:1.15.11")
    implementation("net.bytebuddy:byte-buddy-agent:1.15.11")
}

tasks.shadowJar {
    archiveClassifier.set("")

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
