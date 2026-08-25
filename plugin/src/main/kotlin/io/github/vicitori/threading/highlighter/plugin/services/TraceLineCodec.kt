package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import io.github.vicitori.threading.highlighter.common.trace.TraceJson
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord

private val LOG = logger<TraceLineCodec>()

/**
 * Reads one JSONL line into a [TraceRecord].
 *
 * Uses the shared [TraceJson] codec in `common`, so the plugin and the agent use the
 * same format. This wrapper only adds friendly error handling; [TraceRepository]
 * takes care of files, dedup and filtering.
 */
object TraceLineCodec {
    fun decode(line: String): TraceRecord? = try {
        TraceJson.decode(line)
    } catch (e: ProcessCanceledException) {
        throw e // platform cancellation must never be swallowed
    } catch (e: Exception) {
        LOG.warn("Failed to parse trace line: ${line.take(100)}", e)
        null
    }
}
