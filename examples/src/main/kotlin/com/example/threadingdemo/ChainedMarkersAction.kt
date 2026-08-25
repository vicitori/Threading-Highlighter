package com.example.threadingdemo

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

class ChainedMarkersAction : AnAction("All Markers (Chained Example)") {
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

    private fun processDataFromBackground(project: Project) {
        showNotification(project, "[Background] Processing data from background thread")
        val result = performCalculation(100)
        showNotification(project, "[Background] Result: $result")
    }

    private fun complexOperation(project: Project) {
        showNotification(project, "[Complex] Starting complex operation")

        val calculationResult = performCalculation(256)
        showNotification(project, "[Complex] Calculation result: $calculationResult")
        slowEdtOperation(project)
        showNotification(project, "[Complex] Complex operation completed")
    }

    private fun performCalculation(value: Int): Int {
        ApplicationManager.getApplication().assertIsNonDispatchThread()
        // simulated work on a pooled thread (safe to block here, not the EDT)
        Thread.sleep(50)
        return value * 2
    }

    private fun slowEdtOperation(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().assertIsDispatchThread()
            // intentional EDT block for the demo; this is the anti-pattern the tool flags
            Thread.sleep(50)
            showNotification(project, "[EDT] Slow EDT operation completed")
        }
    }

    private fun showNotification(
        project: Project,
        message: String,
    ) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup("Threading Highlighter")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }
}
