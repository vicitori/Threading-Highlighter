package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager
import io.github.vicitori.threading.highlighter.plugin.ui.TextInfoDialog

class ShowTraceSummaryAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val hasProject = e.project != null
        e.presentation.isEnabledAndVisible = hasProject
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val summary = TraceManager.getInstance(project).buildDebugSummary()
        TextInfoDialog(project, "Threading Trace Summary", summary).show()
    }
}
