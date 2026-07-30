package io.github.vicitori.threading.highlighter.plugin.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import io.github.vicitori.threading.highlighter.plugin.services.TraceNavigator
import java.awt.Dimension
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

/**
 * Shows a lightweight, non-modal popup with a line's marker details next to the code.
 * Trace frames are links: clicking one navigates to that source location.
 */
object MarkerDetailsPopup {
    private const val WIDTH = 480
    private const val HEIGHT = 260

    fun show(e: AnActionEvent, records: List<Pair<MarkerInfo, TraceRecord>>, fileName: String, lineNumber: Int, stale: Boolean) {
        val project = e.project
        val pane = JEditorPane().apply {
            editorKit = HTMLEditorKitBuilder().build()
            text = TraceHtml.details(records, fileName, lineNumber, stale)
            isEditable = false
            caretPosition = 0
        }
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(
                JBScrollPane(pane).apply { preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT)) },
                pane
            )
            .setTitle("Threading Marker Details")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()

        // href carries the record index; navigate to that frame's source
        pane.addHyperlinkListener { event ->
            if (event.eventType != HyperlinkEvent.EventType.ACTIVATED || project == null) return@addHyperlinkListener
            val index = event.description?.toIntOrNull() ?: return@addHyperlinkListener
            records.getOrNull(index)?.let { (_, trace) ->
                if (TraceNavigator.navigateTo(project, trace)) popup.cancel()
            }
        }
        popup.showInBestPositionFor(e.dataContext)
    }
}
