package io.github.vicitori.threading.highlighter.plugin.services

import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.plugin.models.TraceRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

class TraceRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun readTraceFile(path: Path, userPackages: List<String>): List<TraceRecord> {
        if (!path.exists()) {
            return emptyList()
        }
        val traces = mutableListOf<TraceRecord>()

        for (line in path.readLines()) {
            if (line.isBlank()) continue
            val record = parseTraceLine(line) ?: continue
            if (!UserCodeFilter.isUserCode(record.className, userPackages)) {
                continue
            }
            traces.add(record)
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
        } catch (_: Exception) {
            null
        }
    }
}
