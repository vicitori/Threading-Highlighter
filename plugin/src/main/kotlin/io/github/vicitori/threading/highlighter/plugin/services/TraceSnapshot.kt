package io.github.vicitori.threading.highlighter.plugin.services

import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import io.github.vicitori.threading.highlighter.plugin.models.MarkerTraceData

/**
 * Immutable view of all loaded traces.
 *
 * Built off the EDT and published with a single volatile write (see [TraceManager]),
 * so readers on background threads (the annotator) always see a fully-built,
 * consistent index without locking. Once created, an instance is never mutated.
 */
class TraceSnapshot private constructor(
    private val traceDataByMarker: Map<String, MarkerTraceData>,
    // reverse index for fast per-line lookups by the annotator
    private val locationIndex: Map<String, Map<Int, List<Pair<MarkerInfo, TraceRecord>>>>
) {
    val isEmpty: Boolean get() = traceDataByMarker.isEmpty()
    val markerCount: Int get() = traceDataByMarker.size

    fun getRecordsForLocation(fileName: String, lineNumber: Int): List<Pair<MarkerInfo, TraceRecord>> =
        locationIndex[fileName]?.get(lineNumber) ?: emptyList()

    fun forEachLocation(action: (fileName: String, line: Int, marker: MarkerInfo, trace: TraceRecord) -> Unit) {
        for ((fileName, lineMap) in locationIndex) {
            for ((line, records) in lineMap) {
                for ((marker, trace) in records) {
                    action(fileName, line, marker, trace)
                }
            }
        }
    }

    companion object {
        val EMPTY = TraceSnapshot(emptyMap(), emptyMap())

        /** Builds the reverse index once; the result is safe to share across threads. */
        fun of(traceDataByMarker: Map<String, MarkerTraceData>): TraceSnapshot {
            if (traceDataByMarker.isEmpty()) return EMPTY

            val index = HashMap<String, MutableMap<Int, MutableList<Pair<MarkerInfo, TraceRecord>>>>()
            for (traceData in traceDataByMarker.values) {
                for (trace in traceData.traces) {
                    val fileName = trace.fileName ?: continue
                    if (trace.lineNumber <= 0) continue
                    index.computeIfAbsent(fileName) { HashMap() }
                        .computeIfAbsent(trace.lineNumber) { ArrayList() }
                        .add(traceData.marker to trace)
                }
            }
            return TraceSnapshot(traceDataByMarker, index)
        }
    }
}
