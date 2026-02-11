package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager

/**
 * Diagnostic action: shows a summary of all files/lines
 * that the plugin currently sees from .ij-threading-highlighter directories.
 */
class ShowTraceSummaryAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val hasProject = e.project != null
        e.presentation.isEnabledAndVisible = hasProject
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val traceManager = TraceManager.getInstance(project)

        val summary = traceManager.buildDebugSummary()

        Messages.showInfoMessage(
            project, summary, "Threading Trace Summary"
        )
    }
}
