package io.github.vicitori.threading.highlighter.plugin.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import io.github.vicitori.threading.highlighter.plugin.icons.PluginIcons
import io.github.vicitori.threading.highlighter.plugin.services.MarkerStateService
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager
import io.github.vicitori.threading.highlighter.plugin.ui.TextInfoDialog

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

        val records = getMarkerRecords(element, containingFile.name, lineNumber) ?: return

        if (!isFirstElementOnLine(element, document, lineNumber)) return

        addGutterIcon(holder, element, records, containingFile.name, lineNumber)
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
            // StackTraceElement.fileName is a simple name, so two files with the same
            // name in different packages would collide: keep only records whose class
            // package matches this file's path
            .filter { (_, trace) -> filePathMatchesClass(filePath, trace.className) }
        return records.ifEmpty { null }
    }

    private fun filePathMatchesClass(filePath: String?, className: String): Boolean {
        // path unknown (e.g. in-memory file): cannot disambiguate, keep the record
        if (filePath == null) return true

        // nested classes use '$', so substringBeforeLast('.') yields the package
        val packageName = className.substringBeforeLast('.', missingDelimiterValue = "")
        if (packageName.isEmpty()) return true // default package: nothing to match

        val packagePath = packageName.replace('.', '/')
        return filePath.replace('\\', '/').contains("/$packagePath/")
    }

    private fun isFirstElementOnLine(
        element: PsiElement,
        document: com.intellij.openapi.editor.Document,
        lineNumber: Int
    ): Boolean {
        val lineStartOffset = document.getLineStartOffset(lineNumber - 1)
        val lineEndOffset = document.getLineEndOffset(lineNumber - 1)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset))
        val firstNonWhitespaceOffset = lineStartOffset + lineText.indexOfFirst { !it.isWhitespace() }
        return element.textRange.startOffset == firstNonWhitespaceOffset
    }

    private fun addGutterIcon(
        holder: AnnotationHolder,
        element: PsiElement,
        records: List<Pair<MarkerInfo, TraceRecord>>,
        fileName: String,
        lineNumber: Int
    ) {
        val message = "Threading marker detected: ${records.size} occurrence(s)"
        holder.newAnnotation(HighlightSeverity.INFORMATION, message)
            .range(element.textRange)
            .gutterIconRenderer(createGutterIconRenderer(records, fileName, lineNumber, message))
            .create()
    }

    private fun createGutterIconRenderer(
        records: List<Pair<MarkerInfo, TraceRecord>>,
        fileName: String,
        lineNumber: Int,
        tooltipMessage: String
    ): GutterIconRenderer {
        return object : GutterIconRenderer() {
            override fun getIcon() = PluginIcons.ThreadingMarker
            override fun getTooltipText() = tooltipMessage
            override fun isNavigateAction() = true
            override fun getAlignment() = Alignment.LEFT

            override fun equals(other: Any?): Boolean {
                return other is GutterIconRenderer && other.icon == icon
            }

            override fun hashCode(): Int = icon.hashCode()

            override fun getClickAction(): AnAction {
                return object : AnAction() {
                    override fun actionPerformed(e: AnActionEvent) {
                        val project = e.project ?: return
                        showMarkerDetails(project, records, fileName, lineNumber)
                    }
                }
            }
        }
    }

    private fun showMarkerDetails(
        project: com.intellij.openapi.project.Project,
        records: List<Pair<MarkerInfo, TraceRecord>>,
        fileName: String,
        lineNumber: Int
    ) {
        val message = buildDetailsMessage(records, fileName, lineNumber)
        TextInfoDialog(project, "Threading Marker Details", message).show()
    }

    private fun buildDetailsMessage(
        records: List<Pair<MarkerInfo, TraceRecord>>,
        fileName: String,
        lineNumber: Int
    ): String = buildString {
        appendLine("Threading Marker Detected")
        appendLine("─".repeat(60))
        appendLine("Location: $fileName:$lineNumber")
        appendLine()

        for ((marker, trace) in records) {
            appendLine("Marker: ${marker.displayName}")
            appendLine("  Description: ${marker.description}")
            appendLine("  Last seen: ${java.time.Instant.ofEpochMilli(trace.lastSeenTimestampEpochMillis)}")
            appendLine("  Trace: ${trace.className}.${trace.methodName}")
            appendLine()
        }
    }
}
