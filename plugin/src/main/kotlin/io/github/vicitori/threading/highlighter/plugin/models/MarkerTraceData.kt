package io.github.vicitori.threading.highlighter.plugin.models

import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord

data class MarkerTraceData(
    val marker: MarkerInfo,
    val traces: List<TraceRecord>
)
