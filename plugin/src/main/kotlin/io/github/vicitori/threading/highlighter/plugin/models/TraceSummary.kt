package io.github.vicitori.threading.highlighter.plugin.models

import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord

/**
 * One marker occurrence at a source location, ready for display or navigation.
 * Presentation-agnostic: views decide how to render it.
 */
data class TraceEntry(
    val line: Int,
    val marker: MarkerInfo,
    val trace: TraceRecord,
)

/**
 * Snapshot of loaded traces grouped by file, produced by the data layer for views
 * (summary dialog, tool window). Files and entries are already sorted.
 */
data class TraceSummary(
    val markerCount: Int,
    val byFile: Map<String, List<TraceEntry>>,
) {
    val isEmpty: Boolean get() = byFile.isEmpty()
    val fileCount: Int get() = byFile.size
}

/**
 * Diagnostic context shown when nothing is loaded, so an empty gutter is explainable.
 */
data class TraceDiagnostics(
    val projectName: String,
    val projectPath: String,
    val tracesDir: String?,
    val tracesDirExists: Boolean,
)
