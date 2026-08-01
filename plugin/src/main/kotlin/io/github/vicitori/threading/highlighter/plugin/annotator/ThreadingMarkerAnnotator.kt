package io.github.vicitori.threading.highlighter.plugin.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import io.github.vicitori.threading.highlighter.plugin.icons.PluginIcons
import io.github.vicitori.threading.highlighter.plugin.services.MarkerStateService
import io.github.vicitori.threading.highlighter.plugin.services.PackagePathMatcher
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager
import io.github.vicitori.threading.highlighter.plugin.ui.MarkerDetailsPopup
import io.github.vicitori.threading.highlighter.plugin.ui.TraceHtml

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
        return PackagePathMatcher.matches(filePath, PackagePathMatcher.packageOf(trace.className), trace.fileName)
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
    override fun getTooltipText() = TraceHtml.tooltip(records, stale)
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

    // cached: getClickAction() is polled repeatedly, so avoid allocating each call
    private val clickAction = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            MarkerDetailsPopup.show(e, records, fileName, lineNumber, stale)
        }
    }

    override fun getClickAction(): AnAction = clickAction
}
