package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig
import io.github.vicitori.threading.highlighter.plugin.services.AgentLocator
import java.awt.datatransfer.StringSelection

/**
 * Copies a ready-to-use `-javaagent` VM argument (pointing at the bundled agent) to
 * the clipboard, so the user can paste it into a run configuration without hunting
 * for the jar. This is the "-javaagent, but no manual jar search" delivery path.
 */
class CopyAgentArgsAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val group = NotificationGroupManager.getInstance().getNotificationGroup("Threading Highlighter")

        val agentJar = AgentLocator.findAgentJar()
        if (agentJar == null) {
            group.createNotification(
                "Threading Highlighter agent not found",
                "The bundled agent.jar is missing from the plugin installation.",
                NotificationType.ERROR
            ).notify(project)
            return
        }

        val projectDir = project.basePath ?: "\$PROJECT_DIR\$"
        val vmArgs = "-javaagent:$agentJar " +
                "-D${ThreadingHighlighterConfig.PROJECT_DIR_PROPERTY}=$projectDir"

        CopyPasteManager.getInstance().setContents(StringSelection(vmArgs))
        group.createNotification(
            "Agent VM options copied",
            "Paste them into your run configuration's VM options, then restart:<br/><code>$vmArgs</code>",
            NotificationType.INFORMATION
        ).notify(project)
    }
}
