package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.marker.Markers
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import io.github.vicitori.threading.highlighter.plugin.models.MarkerTraceData
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class TraceManager(private val project: Project) {
    private val repository = TraceRepository()
    private val stateService = MarkerStateService.getInstance(project)
    private val traceDataByMarker = mutableMapOf<String, MarkerTraceData>()
    private val locationIndex = mutableMapOf<String, MutableMap<Int, MutableList<Pair<MarkerInfo, TraceRecord>>>>()

    companion object {
        fun getInstance(project: Project): TraceManager = project.service()
    }

    init {
        reloadTraces()
    }

    fun reloadTraces() {
        val projectBasePath = project.basePath ?: return
        val tracesDir = ThreadingHighlighterConfig.findTracesPath(projectBasePath)

        traceDataByMarker.clear()
        clearIndex()

        if (!tracesDir.exists()) {
            return
        }

        val markers = Markers.getAll()
        val userPackages = UserCodeFilter.getUserPackages(project)

        for (marker in markers) {
            val traceFile = tracesDir.resolve(repository.getTraceFileName(marker))
            if (!traceFile.exists()) {
                continue
            }
            val traces = repository.readTraceFile(traceFile, userPackages)
            if (traces.isNotEmpty()) {
                traceDataByMarker[marker.markerFqn()] = MarkerTraceData(marker, traces)
            }
        }
        rebuildIndex()
        stateService.enableMarkers()
    }

    fun getRecordsForLocation(fileName: String, lineNumber: Int): List<Pair<MarkerInfo, TraceRecord>> {
        return locationIndex[fileName]?.get(lineNumber) ?: emptyList()
    }

    fun buildDebugSummary(): String {
        val projectPath = project.basePath ?: "<unknown>"
        val tracesDir = project.basePath?.let { ThreadingHighlighterConfig.findTracesPath(it) }

        if (traceDataByMarker.isEmpty()) {
            return buildString {
                appendLine("No traces loaded.")
                appendLine()
                appendLine("Current project: ${project.name}")
                appendLine("Project path: $projectPath")
                appendLine("Found traces directory: $tracesDir (exists: ${tracesDir?.exists() ?: false})")
                appendLine()
                appendLine("Possible reasons:")
                appendLine("  • The agent has not written any trace files yet.")
                appendLine("  • The traces directory is empty or contains no valid trace files.")
                appendLine("  • You need to run the application with the agent first.")
                appendLine("  • You need to use 'Reload Threading Trace' after running the app.")
            }
        }

        val byFile = buildLocationSummary()

        if (byFile.isEmpty()) {
            return "Traces loaded, but none of them have fileName/lineNumber.\n" + "This usually means stack traces did not contain source file info."
        }

        return buildString {
            appendLine("Threading Highlighter Trace Summary")
            appendLine("─".repeat(70))
            appendLine("Total markers: ${traceDataByMarker.size}")
            appendLine("Total files with traces: ${byFile.size}")
            appendLine()

            val sortedFiles = byFile.keys.sorted()
            for (file in sortedFiles) {
                val entries = byFile[file]!!.sortedBy { it.third }
                appendLine("File: $file")
                for ((markerFqn, className, line) in entries) {
                    appendLine("  ├─ line $line : $markerFqn")
                    appendLine("  │  └─ class: $className")
                }
                appendLine()
            }
        }
    }

    private fun rebuildIndex() {
        locationIndex.clear()
        for ((_, traceData) in traceDataByMarker) {
            for (trace in traceData.traces) {
                val fileName = trace.fileName ?: continue
                val lineNumber = trace.lineNumber
                if (lineNumber <= 0) continue
                locationIndex.computeIfAbsent(fileName) { mutableMapOf() }
                    .computeIfAbsent(lineNumber) { mutableListOf() }.add(traceData.marker to trace)
            }
        }
    }

    private fun clearIndex() {
        locationIndex.clear()
    }

    private fun buildLocationSummary(): Map<String, List<Triple<String, String, Int>>> {
        val byFile = mutableMapOf<String, MutableList<Triple<String, String, Int>>>()
        for ((fileName, lineMap) in locationIndex) {
            for ((lineNumber, records) in lineMap) {
                for ((marker, trace) in records) {
                    byFile.computeIfAbsent(fileName) { mutableListOf() }
                        .add(Triple(marker.markerFqn(), trace.className, lineNumber))
                }
            }
        }
        return byFile
    }
}
