package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.marker.Markers
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import io.github.vicitori.threading.highlighter.plugin.models.MarkerTraceData
import io.github.vicitori.threading.highlighter.plugin.models.TraceDiagnostics
import io.github.vicitori.threading.highlighter.plugin.models.TraceEntry
import io.github.vicitori.threading.highlighter.plugin.models.TraceSummary
import kotlin.io.path.exists

/**
 * Loads trace files for the project and answers "what markers are on this line?".
 *
 * One writer (the background reload task) publishes an immutable [TraceSnapshot]
 * with a single volatile write; many readers (the annotator on background threads)
 * read it lock-free. The service starts empty: data is loaded only on an explicit
 * reload, off the EDT.
 */
@Service(Service.Level.PROJECT)
class TraceManager(private val project: Project) {
    private val repository = TraceRepository()
    private val stateService = MarkerStateService.getInstance(project)

    @Volatile
    private var snapshot: TraceSnapshot = TraceSnapshot.EMPTY

    /** Latest published snapshot; safe to read from any thread (volatile). */
    val currentSnapshot: TraceSnapshot get() = snapshot

    companion object {
        fun getInstance(project: Project): TraceManager = project.service()
    }

    /**
     * Loads traces in a background task and publishes them atomically, then restarts
     * highlighting. Safe to call from the EDT: the disk I/O never runs on it.
     *
     * @param onReloaded run on the EDT after the new snapshot is published
     */
    fun reloadTraces(onReloaded: () -> Unit = {}) {
        object : Task.Backgroundable(project, "Loading threading traces", true) {
            override fun run(indicator: ProgressIndicator) {
                // single volatile write == happens-before for every later reader
                snapshot = loadSnapshot()
                stateService.markDataAvailable()
            }

            override fun onSuccess() {
                DaemonCodeAnalyzer.getInstance(project).restart()
                project.messageBus.syncPublisher(TraceUpdateListener.TOPIC).tracesUpdated()
                onReloaded()
            }
        }.queue()
    }

    fun getRecordsForLocation(fileName: String, lineNumber: Int): List<Pair<MarkerInfo, TraceRecord>> =
        snapshot.getRecordsForLocation(fileName, lineNumber)

    private fun loadSnapshot(): TraceSnapshot {
        val projectBasePath = project.basePath ?: return TraceSnapshot.EMPTY
        val tracesDir = ThreadingHighlighterConfig.findTracesPath(projectBasePath)
        if (!tracesDir.exists()) {
            return TraceSnapshot.EMPTY
        }

        // reading the project model off the EDT requires a read lock
        val userPackages = ReadAction.compute<List<String>, RuntimeException> {
            UserCodeFilter.getUserPackages(project)
        }

        val dataByMarker = LinkedHashMap<String, MarkerTraceData>()
        for (marker in Markers.getAll()) {
            val traceFile = tracesDir.resolve(repository.getTraceFileName(marker))
            if (!traceFile.exists()) {
                continue
            }
            val traces = repository.readTraceFile(traceFile, userPackages)
            if (traces.isNotEmpty()) {
                dataByMarker[marker.markerFqn()] = MarkerTraceData(marker, traces)
            }
        }
        return TraceSnapshot.of(dataByMarker)
    }

    /** Groups the current snapshot into a presentation-agnostic, pre-sorted summary. */
    fun buildSummary(): TraceSummary {
        val current = snapshot
        val byFile = sortedMapOf<String, MutableList<TraceEntry>>()
        current.forEachLocation { fileName, line, marker, trace ->
            byFile.getOrPut(fileName) { mutableListOf() }.add(TraceEntry(line, marker, trace))
        }
        byFile.values.forEach { it.sortBy { entry -> entry.line } }
        return TraceSummary(current.markerCount, byFile)
    }

    /** Context for the "nothing loaded" case, so an empty gutter is explainable. */
    fun buildDiagnostics(): TraceDiagnostics {
        val tracesDir = project.basePath?.let { ThreadingHighlighterConfig.findTracesPath(it) }
        return TraceDiagnostics(
            projectName = project.name,
            projectPath = project.basePath ?: "<unknown>",
            tracesDir = tracesDir?.toString(),
            tracesDirExists = tracesDir?.exists() ?: false
        )
    }
}
