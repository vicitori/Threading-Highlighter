package com.example.threadingdemo

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.util.SlowOperations

class SlowOperationOnEdtAction : AnAction("Slow Operation Marker Only") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SlowOperations.assertSlowOperationsAreAllowed()
        performSlowOperation()
        showNotification(project)
    }

    private fun performSlowOperation() {
        // intentional slow operation for the demo; the assertion above flags it
        Thread.sleep(100)
    }

    private fun showNotification(project: Project) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup("Threading Highlighter")
            .createNotification("Slow operation completed on EDT", NotificationType.INFORMATION)
            .notify(project)
    }
}
