plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "ThreadingHighlighter"

include(":common")
include(":agent")
include(":plugin")
include(":examples")
