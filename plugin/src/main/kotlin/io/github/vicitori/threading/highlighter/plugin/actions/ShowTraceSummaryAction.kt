package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager
import io.github.vicitori.threading.highlighter.plugin.ui.TextInfoDialog

class ShowTraceSummaryAction : AnAction() {

    // update() only reads e.project, so it is safe to compute off the EDT
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val hasProject = e.project != null
        e.presentation.isEnabledAndVisible = hasProject
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val summary = TraceManager.getInstance(project).buildHtmlSummary()
        TextInfoDialog(project, "Threading Trace Summary", summary, isHtml = true).show()
    }
}
