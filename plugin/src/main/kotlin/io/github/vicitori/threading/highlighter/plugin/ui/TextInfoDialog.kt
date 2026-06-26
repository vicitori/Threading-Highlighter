package io.github.vicitori.threading.highlighter.plugin.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JTextArea

/**
 * Simple scrollable, read-only text dialog built on IntelliJ [DialogWrapper].
 *
 * Replaces `JOptionPane.showMessageDialog(null, ...)`: it is parented to the
 * project window (correct placement, modality, theming) and uses the editor's
 * configured monospaced font instead of a hardcoded one.
 */
class TextInfoDialog(
    project: Project,
    dialogTitle: String,
    private val content: String
) : DialogWrapper(project) {

    init {
        title = dialogTitle
        init()
    }

    override fun createCenterPanel(): JComponent {
        val editorFontName = EditorColorsManager.getInstance().globalScheme.editorFontName
        val editorFontSize = EditorColorsManager.getInstance().globalScheme.editorFontSize

        val textArea = JTextArea(content).apply {
            font = Font(editorFontName, Font.PLAIN, editorFontSize)
            isEditable = false
            lineWrap = false
        }

        return JBScrollPane(textArea).apply {
            preferredSize = Dimension(JBUI.scale(700), JBUI.scale(450))
        }
    }

    override fun createActions() = arrayOf(okAction)
}
