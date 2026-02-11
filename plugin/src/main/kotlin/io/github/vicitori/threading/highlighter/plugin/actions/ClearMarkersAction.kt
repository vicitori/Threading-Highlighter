package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager

/**
 * Action to clear all threading markers from the editor.
 */
class ClearMarkersAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val hasProject = e.project != null
        e.presentation.isEnabledAndVisible = hasProject
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val traceManager = TraceManager.getInstance(project)
        traceManager.clearTraces()

        DaemonCodeAnalyzer.getInstance(project).restart()

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Threading Highlighter")
            .createNotification(
                "Threading markers cleared",
                "All gutter icons have been removed",
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}
