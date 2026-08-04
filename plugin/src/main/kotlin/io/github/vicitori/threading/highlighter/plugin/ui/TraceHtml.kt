package io.github.vicitori.threading.highlighter.plugin.ui

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import io.github.vicitori.threading.highlighter.plugin.models.TraceDiagnostics
import io.github.vicitori.threading.highlighter.plugin.models.TraceSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        body.append(HtmlChunk.tag("div").style("color:#808080").children(
            HtmlChunk.tag("b").addText(summary.markerCount.toString()),
            HtmlChunk.text(" markers in "),
            HtmlChunk.tag("b").addText(summary.fileCount.toString()),
            HtmlChunk.text(" file(s)")
        ))
        for ((file, entries) in summary.byFile) {
            body.append(HtmlChunk.tag("div").style("margin-top:12px").child(HtmlChunk.tag("b").addText(file)))
            val rows = HtmlBuilder()
            rows.append(row("Line", "Marker", "Class", header = true))
            for (entry in entries) {
                rows.append(row(entry.line.toString(), entry.marker.displayName, entry.trace.className))
            }
            body.append(HtmlChunk.tag("table")
                .attr("cellspacing", "0").attr("cellpadding", "4").attr("width", "100%")
                .style("margin-top:4px")
                .child(rows.toFragment()))
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

    private val LAST_SEEN_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss").withZone(ZoneId.systemDefault())

    /**
     * Details popup: each frame's trace is a link whose href is the record index, so
     * the caller's hyperlink listener can navigate to it.
     */
    fun details(records: List<Pair<MarkerInfo, TraceRecord>>, fileName: String, lineNumber: Int, stale: Boolean): String {
        val body = HtmlBuilder()
        body.append(HtmlChunk.tag("div").style("color:#808080").children(
            HtmlChunk.text("$fileName:"), HtmlChunk.tag("b").addText(lineNumber.toString())
        ))
        if (stale) {
            body.append(HtmlChunk.tag("div").style("color:#C8783C;margin-top:4px").addText(
                "\u26a0 File edited after recording — the line number may be inaccurate."
            ))
        }
        records.forEachIndexed { index, (marker, trace) ->
            val block = HtmlBuilder()
            block.append(HtmlChunk.tag("b").addText(marker.displayName))
            block.append(HtmlChunk.tag("div").style("margin-top:2px").addText(marker.description))
            block.append(HtmlChunk.tag("div").style("margin-top:4px").children(
                HtmlChunk.tag("span").style("color:#808080").addText("at "),
                HtmlChunk.link(index.toString(), "${trace.className}.${trace.methodName}")
            ))
            block.append(HtmlChunk.tag("div").style("color:#808080;margin-top:2px").addText(
                "last seen " + LAST_SEEN_FORMAT.format(Instant.ofEpochMilli(trace.lastSeenTimestampEpochMillis))
            ))
            body.append(HtmlChunk.tag("div").style("margin-top:10px").child(block.toFragment()))
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
        val clsStyle = if (header) "text-align:left" else "text-align:left;color:#808080"
        return HtmlChunk.tag("tr").children(
            HtmlChunk.tag(cell).style("text-align:left;width:44px").addText(line),
            HtmlChunk.tag(cell).style("text-align:left").addText(marker),
            HtmlChunk.tag(cell).style(clsStyle).addText(cls)
        )
    }

    private fun page(body: HtmlBuilder): String =
        HtmlChunk.html().child(HtmlChunk.body().child(body.toFragment())).toString()
}
