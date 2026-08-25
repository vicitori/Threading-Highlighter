package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager

class ReloadTraceAction : AnAction() {
    // update() only reads e.project, so it is safe to compute off the EDT
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // reload runs in the background and restarts highlighting itself;
        // notify the user only once the new snapshot is actually published
        TraceManager.getInstance(project).reloadTraces {
            val message = "Use Tools | Threading Highlighter | Show Trace Summary for details."
            NotificationGroupManager
                .getInstance()
                .getNotificationGroup("Threading Highlighter")
                .createNotification(
                    "Threading traces reloaded",
                    message,
                    NotificationType.INFORMATION,
                ).notify(project)
        }
    }
}
