package io.github.vicitori.threading.highlighter.examples

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

class EdtMarkerAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val application = ApplicationManager.getApplication()
        application.assertIsDispatchThread()
        showNotification(project)
    }

    private fun showNotification(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Main Group")
            .createNotification("EDT assertion passed - running on Event Dispatch Thread", NotificationType.INFORMATION)
            .notify(project)
    }
}
