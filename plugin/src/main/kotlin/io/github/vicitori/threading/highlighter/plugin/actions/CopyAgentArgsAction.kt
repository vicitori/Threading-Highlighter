package io.github.vicitori.threading.highlighter.plugin.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig
import io.github.vicitori.threading.highlighter.plugin.services.AgentLocator
import io.github.vicitori.threading.highlighter.plugin.ui.AgentSetupDialog

/**
 * Guides the user through enabling the bundled agent: opens a dialog with ready-to-use
 * `-javaagent` VM options (pointing at the jar bundled in the plugin) and paste steps,
 * so there is no manual jar hunting. This is the "-javaagent, but no manual jar search"
 * delivery path.
 */
class CopyAgentArgsAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val agentJar = AgentLocator.findAgentJar()
        if (agentJar == null) {
            NotificationGroupManager.getInstance().getNotificationGroup("Threading Highlighter")
                .createNotification(
                    "Threading Highlighter agent not found",
                    "The bundled agent.jar is missing from the plugin installation.",
                    NotificationType.ERROR
                ).notify(project)
            return
        }

        val projectDir = project.basePath ?: "\$PROJECT_DIR\$"
        val vmArgs = "-javaagent:$agentJar " +
                "-D${ThreadingHighlighterConfig.PROJECT_DIR_PROPERTY}=$projectDir"

        AgentSetupDialog(project, vmArgs).show()
    }
}
