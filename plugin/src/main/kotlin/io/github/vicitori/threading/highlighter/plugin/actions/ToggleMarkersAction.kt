package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.vicitori.threading.highlighter.plugin.services.MarkerStateService

/**
 * Shows or hides all threading gutter markers without reloading traces.
 *
 * The action label reflects the next action ("Show"/"Hide") based on current state.
 */
class ToggleMarkersAction : AnAction() {
    // update() only reads the marker state service, so it is safe off the EDT
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        if (project != null) {
            val hidden = MarkerStateService.getInstance(project).isHiddenByUser()
            e.presentation.text = if (hidden) "Show Threading Markers" else "Hide Threading Markers"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        MarkerStateService.getInstance(project).toggleVisibility()
        // re-run highlighting so the annotator adds or drops icons immediately
        DaemonCodeAnalyzer.getInstance(project).restart()
    }
}
