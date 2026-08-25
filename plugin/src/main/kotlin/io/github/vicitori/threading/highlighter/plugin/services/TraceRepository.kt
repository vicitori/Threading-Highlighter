package io.github.vicitori.threading.highlighter.plugin.services

import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Reads a marker's JSONL trace file into [TraceRecord]s.
 *
 * Owns file I/O, dedup and user-code filtering only; decoding a line into a
 * [TraceRecord] is delegated to [TraceLineCodec]. Keeps only user-code frames and,
 * for each location, the record with the newest timestamp.
 */
class TraceRepository {
    fun readTraceFile(
        path: Path,
        userPackages: List<String>,
    ): List<TraceRecord> {
        if (!path.exists()) {
            return emptyList()
        }

        val tracesByKey = mutableMapOf<String, TraceRecord>()

        for (line in path.readLines()) {
            if (line.isBlank()) continue
            val record = TraceLineCodec.decode(line) ?: continue
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

    fun getTraceFileName(marker: MarkerInfo): String = ThreadingHighlighterConfig.getTraceFileName(marker)
}
