package io.github.vicitori.threading.highlighter.plugin.services

import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

class TraceRepository {
    private val json = Json { ignoreUnknownKeys = true }
    fun readTraceFile(path: Path, userPackages: List<String>): List<TraceRecord> {
        if (!path.exists()) {
            return emptyList()
        }

        val tracesByKey = mutableMapOf<String, TraceRecord>()

        for (line in path.readLines()) {
            if (line.isBlank()) continue
            val record = parseTraceLine(line) ?: continue
            if (!UserCodeFilter.isUserCode(record.className, userPackages)) {
                continue
            }

            val key = record.getKey()
            val existing = tracesByKey[key]
            if (existing == null || record.lastSeenTimestampEpochMillis > existing.lastSeenTimestampEpochMillis) {
                tracesByKey[key] = record
            }
        }
        return tracesByKey.values.toList()
    }

    fun getTraceFileName(marker: MarkerInfo): String {
        return ThreadingHighlighterConfig.getTraceFileName(marker)
    }

    private fun parseTraceLine(line: String): TraceRecord? {
        return try {
            json.decodeFromString<TraceRecord>(line)
        } catch (_: Exception) {
            null
        }
    }
}
