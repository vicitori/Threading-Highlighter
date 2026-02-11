package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.openapi.diagnostic.logger
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.plugin.models.TraceRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Responsible for reading and parsing trace files from disk.
 */
class TraceRepository {

    private val log = logger<TraceRepository>()
    private val json = Json { ignoreUnknownKeys = true }

    fun readTraceFile(path: Path, userPackages: List<String>): List<TraceRecord> {
        if (!path.exists()) {
            log.debug("Trace file does not exist: $path")
            return emptyList()
        }

        val traces = mutableListOf<TraceRecord>()
        var totalLines = 0
        var filteredLines = 0
        var userCodeLines = 0

        log.info("Reading trace file: $path with user packages: $userPackages")

        try {
            for (line in path.readLines()) {
                if (line.isBlank()) continue
                totalLines++

                val record = parseTraceLine(line) ?: continue

                if (!UserCodeFilter.isUserCode(record.className, userPackages)) {
                    filteredLines++
                    continue
                }

                userCodeLines++
                traces.add(record)
            }

            log.info("Read ${path.fileName}: total=$totalLines, filtered=$filteredLines, userCode=$userCodeLines")
            if (userCodeLines > 0) {
                log.info("Sample user code traces: ${traces.take(3).map { "${it.className}#${it.methodName}" }}")
            }
        } catch (e: Exception) {
            log.error("Failed to read trace file: $path", e)
        }

        return traces
    }

    fun getTraceFileName(marker: MarkerInfo): String {
        return marker.markerFqn().replace('#', '_').replace('$', '_') + ".jsonl"
    }

    private fun parseTraceLine(line: String): TraceRecord? {
        return try {
            val jsonObject = json.parseToJsonElement(line).jsonObject

            val className = jsonObject["className"]?.jsonPrimitive?.content ?: return null
            val methodName = jsonObject["methodName"]?.jsonPrimitive?.content ?: return null
            val fileName = jsonObject["fileName"]?.jsonPrimitive?.content
            val lineNumber = jsonObject["lineNumber"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
            val timestamp = jsonObject["lastSeenTimestampEpochMillis"]?.jsonPrimitive?.long ?: 0L

            TraceRecord(className, methodName, fileName, lineNumber, timestamp)
        } catch (e: Exception) {
            log.warn("Failed to parse trace line: $line", e)
            null
        }
    }
}
