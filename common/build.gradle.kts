plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.21"
}

dependencies {
    // Только аннотации для сериализации (compile-only)
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
}

kotlin {
    jvmToolchain(21)
}
