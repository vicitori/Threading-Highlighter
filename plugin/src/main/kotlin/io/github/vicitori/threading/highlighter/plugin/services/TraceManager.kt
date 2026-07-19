package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.marker.Markers
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import io.github.vicitori.threading.highlighter.plugin.models.MarkerTraceData
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

    companion object {
        private val LOG = logger<TraceManager>()
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
            LOG.info("loadSnapshot: traces dir does not exist: $tracesDir")
            return TraceSnapshot.EMPTY
        }

        // reading the project model off the EDT requires a read lock
        val userPackages = ReadAction.compute<List<String>, RuntimeException> {
            UserCodeFilter.getUserPackages(project)
        }
        LOG.info("loadSnapshot: tracesDir=$tracesDir, userPackages=$userPackages")

        val dataByMarker = LinkedHashMap<String, MarkerTraceData>()
        for (marker in Markers.getAll()) {
            val traceFile = tracesDir.resolve(repository.getTraceFileName(marker))
            if (!traceFile.exists()) {
                continue
            }
            val traces = repository.readTraceFile(traceFile, userPackages)
            LOG.info("loadSnapshot: marker=${marker.markerFqn()} kept ${traces.size} record(s)")
            if (traces.isNotEmpty()) {
                dataByMarker[marker.markerFqn()] = MarkerTraceData(marker, traces)
            }
        }
        LOG.info("loadSnapshot: total markers with data = ${dataByMarker.size}")
        return TraceSnapshot.of(dataByMarker)
    }

    fun buildDebugSummary(): String {
        val currentSnapshot = snapshot
        val projectPath = project.basePath ?: "<unknown>"
        val tracesDir = project.basePath?.let { ThreadingHighlighterConfig.findTracesPath(it) }

        if (currentSnapshot.isEmpty) {
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

        val byFile = buildLocationSummary(currentSnapshot)

        if (byFile.isEmpty()) {
            return "Traces loaded, but none of them have fileName/lineNumber.\n" + "This usually means stack traces did not contain source file info."
        }

        return buildString {
            appendLine("Threading Highlighter Trace Summary")
            appendLine("─".repeat(70))
            appendLine("Total markers: ${currentSnapshot.markerCount}")
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

    private fun buildLocationSummary(snapshot: TraceSnapshot): Map<String, List<Triple<String, String, Int>>> {
        val byFile = mutableMapOf<String, MutableList<Triple<String, String, Int>>>()
        snapshot.forEachLocation { fileName, line, marker, trace ->
            byFile.computeIfAbsent(fileName) { mutableListOf() }
                .add(Triple(marker.markerFqn(), trace.className, line))
        }
        return byFile
    }

    /**
     * Same content as [buildDebugSummary] but as HTML: a per-file table that reads
     * better than the monospaced text dump and does not break on long class names.
     */
    fun buildHtmlSummary(): String {
        val currentSnapshot = snapshot
        if (currentSnapshot.isEmpty) {
            return "<html><body><p>No traces loaded.</p></body></html>"
        }
        val byFile = buildLocationSummary(currentSnapshot)
        if (byFile.isEmpty()) {
            return "<html><body><p>Traces loaded, but none of them have a source file and line.</p></body></html>"
        }

        return buildString {
            append("<html><body>")
            append("<h2>Threading Trace Summary</h2>")
            append("<p>Markers: <b>${currentSnapshot.markerCount}</b> &nbsp; ")
            append("Files with traces: <b>${byFile.size}</b></p>")
            for (file in byFile.keys.sorted()) {
                append("<h3>${esc(file)}</h3>")
                append("<table cellpadding='3'>")
                append("<tr><th align='left'>Line</th><th align='left'>Marker</th><th align='left'>Class</th></tr>")
                for ((markerFqn, className, line) in byFile.getValue(file).sortedBy { it.third }) {
                    append("<tr><td>$line</td><td>${esc(markerFqn)}</td><td>${esc(className)}</td></tr>")
                }
                append("</table>")
            }
            append("</body></html>")
        }
    }

    private fun esc(s: String): String = StringUtil.escapeXmlEntities(s)
}
