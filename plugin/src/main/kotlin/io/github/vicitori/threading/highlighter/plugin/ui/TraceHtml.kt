package io.github.vicitori.threading.highlighter.plugin.ui

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import io.github.vicitori.threading.highlighter.plugin.models.TraceDiagnostics
import io.github.vicitori.threading.highlighter.plugin.models.TraceSummary
import java.time.Instant

/**
 * Single place that turns trace data into HTML for every view (summary dialog,
 * gutter tooltip, details popup). Uses [HtmlChunk], so escaping is automatic and
 * the markup cannot get malformed by hand.
 */
object TraceHtml {

    /** Full summary: per-file tables. [diagnostics] is used only when nothing loaded. */
    fun summary(summary: TraceSummary, diagnostics: TraceDiagnostics): String {
        if (summary.isEmpty) return emptySummary(diagnostics)

        val body = HtmlBuilder()
        body.append(HtmlChunk.tag("h2").addText("Threading Trace Summary"))
        body.append(
            HtmlChunk.tag("p").children(
                HtmlChunk.text("Markers: "), HtmlChunk.tag("b").addText(summary.markerCount.toString()),
                HtmlChunk.text("   Files: "), HtmlChunk.tag("b").addText(summary.fileCount.toString())
            )
        )
        for ((file, entries) in summary.byFile) {
            body.append(HtmlChunk.tag("h3").addText(file))
            val rows = HtmlBuilder()
            rows.append(row("Line", "Marker", "Class", header = true))
            for (entry in entries) {
                rows.append(row(entry.line.toString(), entry.marker.displayName, entry.trace.className))
            }
            body.append(HtmlChunk.tag("table").attr("cellpadding", "3").child(rows.toFragment()))
        }
        return page(body)
    }

    /** Gutter tooltip: compact summary of the markers on one line. */
    fun tooltip(records: List<Pair<MarkerInfo, TraceRecord>>, stale: Boolean): String {
        val body = HtmlBuilder()
        body.append(HtmlChunk.tag("b").addText("Threading contracts hit here"))
        if (stale) {
            body.br().append(HtmlChunk.tag("i").addText(
                "\u26a0 file edited after recording — line may be inaccurate"
            ))
        }
        val markers = HtmlBuilder()
        records.distinctBy { it.first.markerFqn() }.forEach { (marker, _) ->
            markers.append(HtmlChunk.li().addText(marker.displayName))
        }
        body.append(HtmlChunk.ul().child(markers.toFragment()))
        body.append(HtmlChunk.tag("small").child(
            HtmlChunk.tag("i").addText("Click the icon for details and navigation")
        ))
        return page(body)
    }

    /**
     * Details popup: each frame's trace is a link whose href is the record index, so
     * the caller's hyperlink listener can navigate to it.
     */
    fun details(records: List<Pair<MarkerInfo, TraceRecord>>, fileName: String, lineNumber: Int, stale: Boolean): String {
        val body = HtmlBuilder()
        body.append(HtmlChunk.tag("h3").addText("Threading Marker Detected"))
        body.append(HtmlChunk.tag("p").children(
            HtmlChunk.text("Location: "), HtmlChunk.tag("b").addText("$fileName:$lineNumber")
        ))
        if (stale) {
            body.append(HtmlChunk.tag("p").child(HtmlChunk.tag("i").addText(
                "\u26a0 File was edited after this trace was recorded; the line number may be inaccurate."
            )))
        }
        records.forEachIndexed { index, (marker, trace) ->
            body.append(HtmlChunk.hr())
            val p = HtmlBuilder()
            p.append(HtmlChunk.tag("b").addText(marker.displayName)).br()
            p.append(HtmlChunk.text(marker.description)).br()
            p.append(HtmlChunk.text("Trace: "))
                .append(HtmlChunk.link(index.toString(), "${trace.className}.${trace.methodName}")).br()
            p.append(HtmlChunk.tag("small").addText(
                "Last seen: ${Instant.ofEpochMilli(trace.lastSeenTimestampEpochMillis)}"
            ))
            body.append(HtmlChunk.tag("p").child(p.toFragment()))
        }
        return page(body)
    }

    private fun emptySummary(d: TraceDiagnostics): String {
        val body = HtmlBuilder()
        body.append(HtmlChunk.tag("h3").addText("No traces loaded"))
        body.append(HtmlChunk.tag("p").addText("Project: ${d.projectName}"))
        body.append(HtmlChunk.tag("p").addText("Traces dir: ${d.tracesDir ?: "<unknown>"} (exists: ${d.tracesDirExists})"))
        val reasons = HtmlBuilder()
        listOf(
            "The agent has not written any trace files yet.",
            "Run the application with the agent, then use Reload Threading Traces."
        ).forEach { reasons.append(HtmlChunk.li().addText(it)) }
        body.append(HtmlChunk.ul().child(reasons.toFragment()))
        return page(body)
    }

    private fun row(line: String, marker: String, cls: String, header: Boolean = false): HtmlChunk {
        val cell = if (header) "th" else "td"
        return HtmlChunk.tag("tr").children(
            HtmlChunk.tag(cell).attr("align", "left").addText(line),
            HtmlChunk.tag(cell).attr("align", "left").addText(marker),
            HtmlChunk.tag(cell).attr("align", "left").addText(cls)
        )
    }

    private fun page(body: HtmlBuilder): String =
        HtmlChunk.html().child(HtmlChunk.body().child(body.toFragment())).toString()
}
