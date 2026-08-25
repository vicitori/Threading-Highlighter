package io.github.vicitori.threading.highlighter.plugin.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import io.github.vicitori.threading.highlighter.plugin.icons.PluginIcons
import io.github.vicitori.threading.highlighter.plugin.models.TraceEntry
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager
import io.github.vicitori.threading.highlighter.plugin.services.TraceNavigator
import io.github.vicitori.threading.highlighter.plugin.services.TraceUpdateListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * Tool window that lists loaded traces as a `file -> entry` tree, replacing the modal
 * summary. Double-clicking a leaf navigates to the code. The tree model is built off
 * the EDT (the snapshot can be large) and published back on the EDT; it refreshes on
 * [TraceUpdateListener].
 */
class TraceToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val root = DefaultMutableTreeNode()
        val model = DefaultTreeModel(root)
        val tree =
            Tree(model).apply {
                isRootVisible = false
                showsRootHandles = true
                cellRenderer = TraceTreeCellRenderer()
            }

        tree.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount != 2) return
                    val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                    (node.userObject as? TraceEntry)?.let { TraceNavigator.navigateTo(project, it.trace) }
                }
            },
        )

        project.messageBus
            .connect(toolWindow.disposable)
            .subscribe(TraceUpdateListener.TOPIC, TraceUpdateListener { refresh(project, tree, root, model) })

        val content = ContentFactory.getInstance().createContent(JBScrollPane(tree), null, false)
        toolWindow.contentManager.addContent(content)
        refresh(project, tree, root, model)
    }

    /** Builds the tree nodes off the EDT, then swaps them in and expands on the EDT. */
    private fun refresh(
        project: Project,
        tree: JTree,
        root: DefaultMutableTreeNode,
        model: DefaultTreeModel,
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val summary = TraceManager.getInstance(project).buildSummary()
            val newRoot = DefaultMutableTreeNode()
            if (summary.isEmpty) {
                newRoot.add(DefaultMutableTreeNode(EmptyNode))
            } else {
                for ((file, entries) in summary.byFile) {
                    val fileNode = DefaultMutableTreeNode(file)
                    entries.forEach { fileNode.add(DefaultMutableTreeNode(it)) }
                    newRoot.add(fileNode)
                }
            }
            ApplicationManager.getApplication().invokeLater {
                root.removeAllChildren()
                while (newRoot.childCount > 0) root.add(newRoot.firstChild as DefaultMutableTreeNode)
                model.reload()
                expandTopLevel(tree, root)
            }
        }
    }

    private fun expandTopLevel(
        tree: JTree,
        root: DefaultMutableTreeNode,
    ) {
        for (i in 0 until root.childCount) {
            tree.expandPath(TreePath(arrayOf<Any>(root, root.getChildAt(i))))
        }
    }

    /** Marker for the "nothing loaded" placeholder row. */
    private object EmptyNode

    /** Renders file nodes, trace entries and the empty placeholder distinctly. */
    private class TraceTreeCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val userObject = (value as? DefaultMutableTreeNode)?.userObject
            when (userObject) {
                is EmptyNode ->
                    append(
                        "No traces loaded — run the app with the agent, then Reload.",
                        SimpleTextAttributes.GRAYED_ATTRIBUTES,
                    )
                is TraceEntry -> {
                    icon = PluginIcons.ThreadingMarker
                    append("line ${userObject.line}  ")
                    append(userObject.marker.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("  ${userObject.trace.className}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is String -> append(userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }
        }
    }
}
