package io.github.vicitori.threading.highlighter.common.config

import java.nio.file.Path
import kotlin.io.path.Path

object ThreadingHighlighterConfig {
    const val TRACES_DIR_PROPERTY = "threading.highlighter.traces.dir"

    @JvmStatic
    fun getTracesPath(): Path {
        val dir = System.getProperty(TRACES_DIR_PROPERTY)
            ?: throw IllegalStateException(
                """System property '$TRACES_DIR_PROPERTY' is not set.
                   |Please configure it in your build.gradle.kts:
                   |systemProperty("$TRACES_DIR_PROPERTY", "${'$'}{project.projectDir}/.ij-threading-highlighter")
                """.trimMargin()
            )
        return Path(dir).toAbsolutePath().normalize()
    }
}
