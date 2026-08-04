package com.example.threadingdemo

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

class NonEdtMarkerAction : AnAction("Non-EDT Marker Only") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val application = ApplicationManager.getApplication()

        showNotification(project, "Starting background task...")
        application.executeOnPooledThread {
            application.assertIsNonDispatchThread()
            performBackgroundWork()
            application.invokeLater {
                showNotification(project, "Background work completed (was on non-EDT thread)")
            }
        }
    }

    private fun performBackgroundWork() {
        // simulated work on a pooled thread (safe to block here, not the EDT)
        Thread.sleep(500)
    }

    private fun showNotification(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Threading Highlighter")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }
}
