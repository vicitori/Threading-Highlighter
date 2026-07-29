package io.github.vicitori.threading.highlighter.plugin.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent

/**
 * Guides the user through enabling the agent: shows the ready-to-use VM options with
 * a Copy button and the exact steps to paste them, instead of dumping a string into a
 * balloon. The VM options point at the agent bundled inside the plugin.
 */
class AgentSetupDialog(
    project: Project,
    private val vmArgs: String
) : DialogWrapper(project) {

    init {
        title = "Enable Threading Highlighter Agent"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            label("Add these VM options to your run configuration, then restart it:")
        }
        row {
            val area = JBTextArea(vmArgs).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                rows = 3
            }
            cell(area).align(com.intellij.ui.dsl.builder.AlignX.FILL)
        }
        row {
            button("Copy to Clipboard") {
                CopyPasteManager.getInstance().setContents(StringSelection(vmArgs))
            }
        }
        row {
            cell(JBLabel(STEPS_HTML).apply { foreground = JBUI.CurrentTheme.Label.disabledForeground() })
        }
    }

    override fun createActions() = arrayOf(okAction)

    companion object {
        private val STEPS_HTML = """
            <html><ol style='margin-left:14px'>
              <li>Run &rarr; Edit Configurations…</li>
              <li>Open your run configuration (or add a JVM/Application one)</li>
              <li>Paste the options into the <b>VM options</b> field</li>
              <li>Run again &mdash; markers appear after the app exercises them</li>
            </ol></html>
        """.trimIndent()
    }
}
