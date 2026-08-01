package io.github.vicitori.threading.highlighter.plugin.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JTextArea

/**
 * Scrollable, read-only info dialog built on IntelliJ [DialogWrapper].
 *
 * Replaces `JOptionPane.showMessageDialog(null, ...)`: it is parented to the project
 * window (correct placement, modality, theming). Renders either plain monospaced text
 * or HTML (for tables and readable layout), depending on [isHtml].
 */
class TextInfoDialog(
    project: Project,
    dialogTitle: String,
    private val content: String,
    private val isHtml: Boolean = false
) : DialogWrapper(project) {

    init {
        title = dialogTitle
        init()
    }

    override fun createCenterPanel(): JComponent {
        val view = if (isHtml) htmlPane() else textArea()
        return JBScrollPane(view).apply {
            preferredSize = Dimension(JBUI.scale(WIDTH), JBUI.scale(HEIGHT))
        }
    }

    private fun htmlPane(): JEditorPane = JEditorPane().apply {
        editorKit = HTMLEditorKitBuilder().build()
        text = content
        isEditable = false
        caretPosition = 0
    }

    private fun textArea(): JTextArea {
        val scheme = EditorColorsManager.getInstance().globalScheme
        return JTextArea(content).apply {
            font = Font(scheme.editorFontName, Font.PLAIN, scheme.editorFontSize)
            isEditable = false
            lineWrap = false
        }
    }

    override fun createActions() = arrayOf(okAction)

    private companion object {
        const val WIDTH = 700
        const val HEIGHT = 450
    }
}
