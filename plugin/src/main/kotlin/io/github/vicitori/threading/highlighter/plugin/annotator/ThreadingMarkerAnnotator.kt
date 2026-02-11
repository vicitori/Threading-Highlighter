package io.github.vicitori.threading.highlighter.plugin.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import io.github.vicitori.threading.highlighter.plugin.icons.PluginIcons
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager
import javax.swing.JOptionPane

class ThreadingMarkerAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val project = element.project
        val containingFile = element.containingFile ?: return
        val fileName = containingFile.name

        val traceManager = TraceManager.getInstance(project)
        if (!traceManager.areMarkersEnabled()) {
            return
        }
        if (element.firstChild != null) return
        val document = containingFile.viewProvider.document ?: return
        val lineNumber = document.getLineNumber(element.textRange.startOffset) + 1

        val records = traceManager.getRecordsForLocation(fileName, lineNumber)
        if (records.isEmpty()) return
        val lineStartOffset = document.getLineStartOffset(lineNumber - 1)
        val lineEndOffset = document.getLineEndOffset(lineNumber - 1)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset))
        val firstNonWhitespaceOffset = lineStartOffset + lineText.indexOfFirst { !it.isWhitespace() }
        if (element.textRange.startOffset != firstNonWhitespaceOffset) return
        val message = "Threading marker detected: ${records.size} occurrence(s)"
        holder.newAnnotation(HighlightSeverity.INFORMATION, message).range(element.textRange)
            .gutterIconRenderer(object : GutterIconRenderer() {
                override fun getIcon() = PluginIcons.ThreadingMarker
                override fun getTooltipText() = message
                override fun isNavigateAction() = true
                override fun getAlignment() = Alignment.LEFT
                override fun equals(other: Any?): Boolean {
                    return other is GutterIconRenderer && other.icon == icon
                }

                override fun hashCode(): Int {
                    return icon.hashCode()
                }

                override fun getClickAction(): AnAction {
                    return object : AnAction() {
                        override fun actionPerformed(e: AnActionEvent) {
                            showMarkerDetails(records, fileName, lineNumber)
                        }
                    }
                }
            }).create()
    }

    private fun showMarkerDetails(
        records: List<Pair<io.github.vicitori.threading.highlighter.common.marker.MarkerInfo, io.github.vicitori.threading.highlighter.plugin.models.TraceRecord>>,
        fileName: String,
        lineNumber: Int
    ) {
        val message = buildString {
            appendLine("Threading Marker Detected")
            appendLine("=".repeat(50))
            appendLine("Location: $fileName:$lineNumber")
            appendLine()
            for ((marker, trace) in records) {
                appendLine("Marker: ${marker.markerFqn()}")
                appendLine("Description: ${getMarkerDescription(marker)}")
                appendLine("Last seen: ${java.time.Instant.ofEpochMilli(trace.lastSeenTimestampEpochMillis)}")
                appendLine("Trace: ${trace.className}.${trace.methodName}")
                appendLine()
            }
        }
        JOptionPane.showMessageDialog(
            null, message, "Threading Marker Details", JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun getMarkerDescription(marker: io.github.vicitori.threading.highlighter.common.marker.MarkerInfo): String {
        return when (marker.methodName) {
            "assertSlowOperationsAreAllowed" ->
                "Slow operations are allowed here. This indicates code that may perform I/O or heavy computation."

            "assertIsDispatchThread" ->
                "This code must run on the EDT (Event Dispatch Thread). UI operations are allowed."

            "assertIsNonDispatchThread" ->
                "This code must NOT run on the EDT. Background/pooled thread required."

            else -> "Threading marker: ${marker.methodName}"
        }
    }
}
