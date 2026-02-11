package io.github.vicitori.threading.highlighter.plugin.models

import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo

data class MarkerTraceData(
    val marker: MarkerInfo, val traces: List<TraceRecord>
)
