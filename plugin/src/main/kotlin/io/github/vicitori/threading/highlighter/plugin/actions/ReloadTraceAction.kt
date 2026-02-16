package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager

class ReloadTraceAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val hasProject = e.project != null
        e.presentation.isEnabledAndVisible = hasProject
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val traceManager = TraceManager.getInstance(project)
        traceManager.reloadTraces()

        val daemon = DaemonCodeAnalyzer.getInstance(project)
        daemon.restart()

        val message = "Threading traces reloaded. Check IDE logs for details (Help → Show Log)."

        NotificationGroupManager.getInstance().getNotificationGroup("Threading Highlighter").createNotification(
            "Threading traces reloaded", message, NotificationType.INFORMATION
        ).notify(project)
    }
}
