package io.github.vicitori.threading.highlighter.examples

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.util.SlowOperations

class SlowOperationOnEdtAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SlowOperations.assertSlowOperationsAreAllowed()
        showNotification(project, "Completed!")
    }

    private fun showNotification(project: Project, message: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("Main Group")
            .createNotification(message, NotificationType.INFORMATION).notify(project)
    }
}
