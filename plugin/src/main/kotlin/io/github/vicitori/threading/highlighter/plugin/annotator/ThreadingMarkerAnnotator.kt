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
import java.awt.Font
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextArea

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
        val records = traceManager.getRecordsForLocation(fileName, lineNumber)
        return records.ifEmpty { null }
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
                        showMarkerDetails(records, fileName, lineNumber)
                    }
                }
            }
        }
    }

    private fun showMarkerDetails(
        records: List<Pair<MarkerInfo, TraceRecord>>,
        fileName: String,
        lineNumber: Int
    ) {
        val message = buildDetailsMessage(records, fileName, lineNumber)
        val textArea = createStyledTextArea(message)
        val scrollPane = JScrollPane(textArea)

        JOptionPane.showMessageDialog(
            null,
            scrollPane,
            "Threading Marker Details",
            JOptionPane.INFORMATION_MESSAGE
        )
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

    private fun createStyledTextArea(message: String): JTextArea {
        return JTextArea(message).apply {
            font = Font("JetBrains Mono", Font.PLAIN, 14)
            isEditable = false
            background = null
            lineWrap = false
            wrapStyleWord = false

            val lines = message.lines()
            val maxLineLength = lines.maxOfOrNull { it.length } ?: 60
            rows = minOf(lines.size + 2, 35)
            columns = minOf(maxLineLength + 10, 180)
        }
    }
}
