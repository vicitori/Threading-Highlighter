package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import kotlinx.serialization.json.Json

private val LOG = logger<TraceLineCodec>()

/**
 * Decodes one JSONL line into a [TraceRecord].
 *
 * This is the read half of the trace-line contract; the agent owns the matching
 * `encode` half (hand-written, dependency-free — see A1). Kept separate from
 * [TraceRepository] so the repository only deals with files, dedup and filtering,
 * not with the wire format.
 */
object TraceLineCodec {
    private val json = Json { ignoreUnknownKeys = true } // ignore unknown fields so older files still load

    fun decode(line: String): TraceRecord? {
        return try {
            json.decodeFromString<TraceRecord>(line)
        } catch (e: ProcessCanceledException) {
            throw e // platform cancellation must never be swallowed
        } catch (e: Exception) {
            LOG.warn("Failed to parse trace line: ${line.take(100)}", e)
            null
        }
    }
}
