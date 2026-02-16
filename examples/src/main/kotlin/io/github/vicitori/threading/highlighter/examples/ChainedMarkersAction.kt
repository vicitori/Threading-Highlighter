package io.github.vicitori.threading.highlighter.examples

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager

class ChainedMarkersAction : AnAction("Chained Markers Example") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ApplicationManager.getApplication().assertIsDispatchThread()
        showNotification(project, "[EDT] Action started on EDT thread")

        ApplicationManager.getApplication().executeOnPooledThread {
            processDataFromBackground(project)
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            complexOperation(project)
        }

        showNotification(project, "Started chained operations. Check gutter icons for markers!")
    }

    private fun processDataFromBackground(project: com.intellij.openapi.project.Project) {
        showNotification(project, "[Background] Processing data from background thread")
        val result = performCalculation(100)
        showNotification(project, "[Background] Result: $result")
    }

    private fun complexOperation(project: com.intellij.openapi.project.Project) {
        showNotification(project, "[Complex] Starting complex operation")

        val calculationResult = performCalculation(256)
        showNotification(project, "[Complex] Calculation result: $calculationResult")
        slowEdtOperation(project)
        showNotification(project, "[Complex] Complex operation completed")
    }

    private fun performCalculation(value: Int): Int {
        ApplicationManager.getApplication().assertIsNonDispatchThread()
        Thread.sleep(50)
        return value * 2
    }

    private fun slowEdtOperation(project: com.intellij.openapi.project.Project) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().assertIsDispatchThread()
            Thread.sleep(50)
            showNotification(project, "[EDT] Slow EDT operation completed")
        }
    }

    private fun showNotification(project: com.intellij.openapi.project.Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Threading Highlighter")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }
}
