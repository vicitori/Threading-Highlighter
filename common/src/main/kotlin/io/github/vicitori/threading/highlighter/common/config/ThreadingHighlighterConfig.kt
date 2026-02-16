package io.github.vicitori.threading.highlighter.common.config

import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

object ThreadingHighlighterConfig {
    const val PROJECT_DIR_PROPERTY = "threading.highlighter.project.dir"
    const val TRACES_DIR_NAME = ".ij-threading-highlighter"

    @JvmStatic
    fun getTracesPath(baseDir: String): Path {
        return Path.of(baseDir).resolve(TRACES_DIR_NAME)
    }

    @JvmStatic
    fun findTracesPath(baseDir: String): Path {
        val basePath = Path.of(baseDir)

        val currentDirTraces = basePath.resolve(TRACES_DIR_NAME)
        if (currentDirTraces.exists() && currentDirTraces.isDirectory()) {
            return currentDirTraces
        }

        basePath.toFile().listFiles()?.forEach { subDir ->
            if (subDir.isDirectory) {
                val subDirTraces = subDir.toPath().resolve(TRACES_DIR_NAME)
                if (subDirTraces.exists() && subDirTraces.isDirectory()) {
                    return subDirTraces
                }
            }
        }

        // Search in parent directories (up to 3 levels)
        var currentPath = basePath.parent
        var levelsUp = 0
        while (currentPath != null && levelsUp < 3) {
            val parentTraces = currentPath.resolve(TRACES_DIR_NAME)
            if (parentTraces.exists() && parentTraces.isDirectory()) {
                return parentTraces
            }
            currentPath = currentPath.parent
            levelsUp++
        }

        return currentDirTraces
    }

    @JvmStatic
    fun getTracesPathFromSystemProperty(): Path {
        val projectDir = System.getProperty(PROJECT_DIR_PROPERTY)
            ?: throw IllegalStateException(
                """System property '$PROJECT_DIR_PROPERTY' is not set.
                   |Please configure it in your build.gradle.kts:
                   |systemProperty("$PROJECT_DIR_PROPERTY", "${'$'}{project.projectDir}")
                """.trimMargin()
            )
        return getTracesPath(projectDir)
    }

    @JvmStatic
    fun getTraceFileName(markerFqn: String): String {
        return markerFqn.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".jsonl"
    }

    @JvmStatic
    fun getTraceFileName(marker: MarkerInfo): String {
        return getTraceFileName(marker.markerFqn())
    }
}
