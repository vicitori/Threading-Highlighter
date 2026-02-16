package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager
import java.awt.Font
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextArea

class ShowTraceSummaryAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val hasProject = e.project != null
        e.presentation.isEnabledAndVisible = hasProject
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val traceManager = TraceManager.getInstance(project)

        val summary = traceManager.buildDebugSummary()

        val textArea = JTextArea(summary)
        textArea.font = Font("JetBrains Mono", Font.PLAIN, 14)
        textArea.isEditable = false
        textArea.background = null
        textArea.lineWrap = false
        textArea.wrapStyleWord = false

        val lines = summary.lines()
        val maxLineLength = lines.maxOfOrNull { it.length } ?: 70
        textArea.rows = minOf(lines.size + 2, 35)
        textArea.columns = minOf(maxLineLength + 10, 180)

        val scrollPane = JScrollPane(textArea)

        JOptionPane.showMessageDialog(
            null,
            scrollPane,
            "Threading Trace Summary",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
}
