package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.marker.Markers
import io.github.vicitori.threading.highlighter.plugin.models.MarkerTraceData
import io.github.vicitori.threading.highlighter.plugin.models.TraceRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import java.nio.file.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class TraceManager(private val project: Project) : Disposable {
    private val repository = TraceRepository()
    private val stateService = MarkerStateService.getInstance(project)
    private val traceDataByMarker = mutableMapOf<String, MarkerTraceData>()
    private val locationIndex = mutableMapOf<String, MutableMap<Int, MutableList<Pair<MarkerInfo, TraceRecord>>>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reloadTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    companion object {
        private const val DEBOUNCE_DELAY_MS = 500L
        fun getInstance(project: Project): TraceManager = project.service()
    }

    init {
        setupFileWatcher()
        setupDebouncedReload()
    }

    fun areMarkersEnabled(): Boolean = stateService.areMarkersEnabled()
    fun enableMarkers() = stateService.enableMarkers()
    fun disableMarkers() = stateService.disableMarkers()
    fun reloadTraces() {
        val tracesDirs = getTracesDirectories()
        traceDataByMarker.clear()
        clearIndex()
        val markers = getAllMarkers()
        val userPackages = UserCodeFilter.getUserPackages(project)

        for (tracesDir in tracesDirs) {
            if (!tracesDir.exists()) {
                continue
            }
            for (marker in markers) {
                val traceFile = tracesDir.resolve(repository.getTraceFileName(marker))
                val traces = repository.readTraceFile(traceFile, userPackages)
                if (traces.isEmpty()) continue
                val existingData = traceDataByMarker[marker.markerFqn()]
                if (existingData != null) {
                    val mergedTraces = (existingData.traces + traces).distinctBy {
                        "${it.className}#${it.methodName}#${it.fileName}#${it.lineNumber}"
                    }
                    traceDataByMarker[marker.markerFqn()] = MarkerTraceData(marker, mergedTraces)
                } else {
                    traceDataByMarker[marker.markerFqn()] = MarkerTraceData(marker, traces)
                }
            }
        }
        rebuildIndex()
        enableMarkers()
    }

    fun clearTraces() {
        traceDataByMarker.clear()
        clearIndex()
        disableMarkers()
    }

    fun getRecordsForLocation(fileName: String, lineNumber: Int): List<Pair<MarkerInfo, TraceRecord>> {
        return locationIndex[fileName]?.get(lineNumber) ?: emptyList()
    }

    fun buildDebugSummary(): String {
        val tracesDirs = getTracesDirectories()
        val projectPath = project.basePath ?: "<unknown>"

        if (traceDataByMarker.isEmpty()) {
            val dirsInfo = tracesDirs.joinToString("\n") { "  - $it (exists: ${it.exists()})" }
            return buildString {
                appendLine("No traces loaded.")
                appendLine()
                appendLine("Current project: ${project.name}")
                appendLine("Project path: $projectPath")
                appendLine("Looking for traces in:")
                appendLine(dirsInfo)
                appendLine()
                appendLine("Possible reasons:")
                appendLine(" - The agent has not written any trace files yet.")
                appendLine(" - Wrong project is opened (should be 'examples', not 'Threading-Highlighter').")
                appendLine(" - The traces directory path is wrong.")
                appendLine(" - You need to use 'Reload Threading Trace' or reopen the project.")
                appendLine()
                appendLine("Expected locations:")
                appendLine("  - $projectPath.ij-threading-highlighter/")
                appendLine("  - $projectPath/<subproject>/.ij-threading-highlighter/")
            }
        }

        val byFile = buildLocationSummary()

        if (byFile.isEmpty()) {
            return "Traces loaded, but none of them have fileName/lineNumber.\n" +
                    "This usually means stack traces did not contain source file info."
        }

        return buildString {
            appendLine("Threading Highlighter trace summary")
            appendLine("=".repeat(70))
            appendLine("Total markers: ${traceDataByMarker.size}")
            appendLine("Total files with traces: ${byFile.size}")
            appendLine()

            val sortedFiles = byFile.keys.sorted()
            for (file in sortedFiles) {
                val entries = byFile[file]!!.sortedBy { it.third }
                appendLine("File: $file")
                for ((markerFqn, className, line) in entries) {
                    appendLine("  line $line : $markerFqn  (class: $className)")
                }
                appendLine()
            }
        }
    }

    private fun setupFileWatcher() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val hasTraceChanges = events.any { event ->
                    val path = event.path
                    path.contains(".ij-threading-highlighter") && path.endsWith(".jsonl")
                }

                if (hasTraceChanges) {
                    reloadTrigger.tryEmit(Unit)
                }
            }
        })
    }

    @OptIn(FlowPreview::class)
    private fun setupDebouncedReload() {
        scope.launch {
            reloadTrigger.debounce(DEBOUNCE_DELAY_MS).collect {
                reloadTracesAndRefreshEditors()
            }
        }
    }

    private fun reloadTracesAndRefreshEditors() {
        reloadTraces()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    private fun rebuildIndex() {
        locationIndex.clear()
        for ((_, traceData) in traceDataByMarker) {
            for (trace in traceData.traces) {
                val fileName = trace.fileName ?: continue
                val lineNumber = trace.lineNumber
                if (lineNumber <= 0) continue
                locationIndex.computeIfAbsent(fileName) { mutableMapOf() }
                    .computeIfAbsent(lineNumber) { mutableListOf() }
                    .add(traceData.marker to trace)
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

    private fun getTracesDirectories(): List<Path> {
        val tracesDir = ThreadingHighlighterConfig.getTracesPath()
        return listOf(tracesDir)
    }

    private fun getAllMarkers(): List<MarkerInfo> = listOf(
        Markers.SLOW_OPERATION,
        Markers.NON_EDT,
        Markers.EDT
    )

    override fun dispose() {
        scope.cancel()
    }
}
