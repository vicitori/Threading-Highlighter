package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager
import io.github.vicitori.threading.highlighter.plugin.ui.TextInfoDialog
import io.github.vicitori.threading.highlighter.plugin.ui.TraceHtml

class ShowTraceSummaryAction : AnAction() {

    // update() only reads e.project, so it is safe to compute off the EDT
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val manager = TraceManager.getInstance(project)
        val html = TraceHtml.summary(manager.buildSummary(), manager.buildDiagnostics())
        TextInfoDialog(project, "Threading Trace Summary", html, isHtml = true).show()
    }
}
