package io.github.vicitori.threading.highlighter.plugin.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.time.Instant
import javax.swing.JEditorPane
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import io.github.vicitori.threading.highlighter.plugin.icons.PluginIcons
import io.github.vicitori.threading.highlighter.plugin.services.MarkerStateService
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager

/**
 * Draws a gutter icon on lines that the agent recorded as threading markers.
 *
 * Annotators run once per PSI element, so two guards keep exactly one icon per line:
 * only leaf elements are handled, and only the first element on the line draws.
 */
class ThreadingMarkerAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!shouldAnnotate(element)) return

        val containingFile = element.containingFile ?: return
        val document = containingFile.viewProvider.document ?: return
        val lineNumber = document.getLineNumber(element.textRange.startOffset) + 1

        // cheap positional filter first: it rejects almost every leaf element before
        // we do the more expensive trace lookup and path matching
        if (!isFirstElementOnLine(element, document, lineNumber)) return

        val records = getMarkerRecords(element, containingFile.name, lineNumber) ?: return

        // the file may have been edited after the trace was recorded, so the line
        // number can be off: warn the user instead of showing it as ground truth
        val fileTimestamp = containingFile.virtualFile?.timeStamp ?: 0L
        val newestTrace = records.maxOf { (_, trace) -> trace.lastSeenTimestampEpochMillis }
        val stale = fileTimestamp > newestTrace

        addGutterIcon(holder, element, records, containingFile.name, lineNumber, stale)
    }

    private fun shouldAnnotate(element: PsiElement): Boolean {
        // leaf elements only (no children), so each line is handled once
        if (element.firstChild != null) return false

        val stateService = MarkerStateService.getInstance(element.project)
        return stateService.areMarkersEnabled()
    }

    private fun getMarkerRecords(
        element: PsiElement,
        fileName: String,
        lineNumber: Int
    ): List<Pair<MarkerInfo, TraceRecord>>? {
        val traceManager = TraceManager.getInstance(element.project)
        val filePath = element.containingFile?.virtualFile?.path
        val records = traceManager.getRecordsForLocation(fileName, lineNumber)
            // StackTraceElement.fileName is a simple name, so files with the same name
            // in different packages would collide: keep only records whose class package
            // AND file name anchor the end of this file's path
            .filter { (_, trace) -> pathMatchesTrace(filePath, trace) }
        return records.ifEmpty { null }
    }

    private fun pathMatchesTrace(filePath: String?, trace: TraceRecord): Boolean {
        // path unknown (e.g. in-memory file): cannot disambiguate, keep the record
        if (filePath == null) return true

        val normalizedPath = filePath.replace('\\', '/')
        // nested/lambda classes use '$'; drop it before taking the package
        val packageName = trace.className.substringBefore('$').substringBeforeLast('.', missingDelimiterValue = "")
        val simpleFileName = trace.fileName

        // no file name from the stack frame: fall back to matching the package segment
        if (simpleFileName == null) {
            if (packageName.isEmpty()) return true
            return normalizedPath.contains("/${packageName.replace('.', '/')}/")
        }

        // require the path to end with "/<package>/<file>" (or just "/<file>" for the
        // default package), so the package and file name must both line up
        val anchor = if (packageName.isEmpty()) {
            "/$simpleFileName"
        } else {
            "/${packageName.replace('.', '/')}/$simpleFileName"
        }
        return normalizedPath.endsWith(anchor)
    }

    private fun isFirstElementOnLine(
        element: PsiElement,
        document: Document,
        lineNumber: Int
    ): Boolean {
        val lineStartOffset = document.getLineStartOffset(lineNumber - 1)
        val lineEndOffset = document.getLineEndOffset(lineNumber - 1)
        val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))
        val firstNonWhitespaceIndex = lineText.indexOfFirst { !it.isWhitespace() }
        if (firstNonWhitespaceIndex < 0) return false // blank line: nothing to anchor to
        return element.textRange.startOffset == lineStartOffset + firstNonWhitespaceIndex
    }

    private fun addGutterIcon(
        holder: AnnotationHolder,
        element: PsiElement,
        records: List<Pair<MarkerInfo, TraceRecord>>,
        fileName: String,
        lineNumber: Int,
        stale: Boolean
    ) {
        val staleSuffix = if (stale) " (file edited after recording, line may be inaccurate)" else ""
        val message = "Threading marker detected: ${records.size} occurrence(s)$staleSuffix"
        holder.newAnnotation(HighlightSeverity.INFORMATION, message)
            .range(element.textRange)
            .gutterIconRenderer(createGutterIconRenderer(records, fileName, lineNumber, message, stale))
            .create()
    }

    private fun createGutterIconRenderer(
        records: List<Pair<MarkerInfo, TraceRecord>>,
        fileName: String,
        lineNumber: Int,
        tooltipMessage: String,
        stale: Boolean
    ): GutterIconRenderer = ThreadingGutterIconRenderer(records, fileName, lineNumber, tooltipMessage, stale)
}

/**
 * Gutter renderer for a single line's threading markers.
 *
 * [equals]/[hashCode] intentionally reflect data identity (file, line, record count)
 * rather than the shared icon: the IDE dedups renderers per position to detect
 * changes, so an icon-based equals would make every renderer look equal and leave
 * stale tooltips after a reload.
 */
private class ThreadingGutterIconRenderer(
    private val records: List<Pair<MarkerInfo, TraceRecord>>,
    private val fileName: String,
    private val lineNumber: Int,
    private val tooltipMessage: String,
    private val stale: Boolean
) : GutterIconRenderer() {
    override fun getIcon() = PluginIcons.ThreadingMarker
    override fun getTooltipText() = buildTooltipHtml()
    // click opens a details popup rather than navigating to code
    override fun isNavigateAction() = true
    override fun getAlignment() = Alignment.LEFT

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThreadingGutterIconRenderer) return false
        return fileName == other.fileName &&
                lineNumber == other.lineNumber &&
                records.size == other.records.size &&
                stale == other.stale
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + lineNumber
        result = 31 * result + records.size
        result = 31 * result + stale.hashCode()
        return result
    }

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            showDetailsPopup(e)
        }
    }

    // A lightweight, non-modal popup near the click, so it can sit next to the code
    // instead of a blocking dialog.
    private fun showDetailsPopup(e: AnActionEvent) {
        val pane = JEditorPane().apply {
            editorKit = HTMLEditorKitBuilder().build()
            text = buildDetailsHtml()
            isEditable = false
            caretPosition = 0
        }
        val scroll = JBScrollPane(pane).apply {
            preferredSize = Dimension(JBUI.scale(480), JBUI.scale(260))
        }
        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scroll, pane)
            .setTitle("Threading Marker Details")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()
            .showInBestPositionFor(e.dataContext)
    }

    private fun buildTooltipHtml(): String = buildString {
        append("<html><body>")
        append("<b>Threading marker</b> — ${records.size} occurrence(s)")
        if (stale) {
            append("<br/><i>⚠ file edited after recording; line may be inaccurate</i>")
        }
        for ((marker, _) in records.distinctBy { it.first.markerFqn() }) {
            append("<br/>• ${esc(marker.displayName)}")
        }
        append("<br/><small>Click for details</small>")
        append("</body></html>")
    }

    private fun buildDetailsHtml(): String = buildString {
        append("<html><body>")
        append("<h3>Threading Marker Detected</h3>")
        append("<p>Location: <b>${esc(fileName)}:$lineNumber</b></p>")
        if (stale) {
            append("<p><i>⚠ File was edited after this trace was recorded; ")
            append("the line number may be inaccurate.</i></p>")
        }
        for ((marker, trace) in records) {
            append("<hr/>")
            append("<p><b>${esc(marker.displayName)}</b><br/>")
            append("${esc(marker.description)}<br/>")
            append("Trace: ${esc(trace.className)}.${esc(trace.methodName)}<br/>")
            append("<small>Last seen: ${Instant.ofEpochMilli(trace.lastSeenTimestampEpochMillis)}</small></p>")
        }
        append("</body></html>")
    }

    private fun esc(s: String): String = StringUtil.escapeXmlEntities(s)
}
