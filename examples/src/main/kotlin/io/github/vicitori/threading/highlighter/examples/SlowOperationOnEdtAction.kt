package io.github.vicitori.threading.highlighter.examples

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
        Thread.sleep(100)
    }

    private fun showNotification(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Main Group")
            .createNotification("Slow operation completed on EDT", NotificationType.INFORMATION)
            .notify(project)
    }
}
