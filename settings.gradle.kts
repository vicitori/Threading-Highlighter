plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ThreadingHighlighter"

include(":common")
include(":agent")
include(":plugin")
include(":examples")
