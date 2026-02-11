package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.vicitori.threading.highlighter.plugin.services.MarkerStateService

class ToggleMarkersAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val stateService = MarkerStateService.getInstance(project)
        val enabled = stateService.areMarkersEnabled()
        e.presentation.text = if (enabled) "Hide Threading Markers" else "Show Threading Markers"
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val stateService = MarkerStateService.getInstance(project)
        val nowEnabled = stateService.toggleMarkers()
        
        refreshEditors(project)
        
        val message = if (nowEnabled) {
            "Threading markers are now visible"
        } else {
            "Threading markers are now hidden"
        }
        
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Threading Highlighter")
            .createNotification("Threading markers toggled", message, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun refreshEditors(project: com.intellij.openapi.project.Project) {
        val daemon = DaemonCodeAnalyzer.getInstance(project)
        daemon.restart()
    }
}
