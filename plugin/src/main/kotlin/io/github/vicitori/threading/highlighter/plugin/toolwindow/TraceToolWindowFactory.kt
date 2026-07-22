package io.github.vicitori.threading.highlighter.plugin.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
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
 * Tool window that lists all loaded traces as a `file -> line -> marker` tree,
 * replacing the modal summary dialog. Double-clicking a leaf navigates to the code.
 * Refreshes itself whenever traces are reloaded via [TraceUpdateListener].
 */
class TraceToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val root = DefaultMutableTreeNode("Threading traces")
        val model = DefaultTreeModel(root)
        val tree = JBTreeReadOnly(model)

        rebuild(project, root, model)

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                (node.userObject as? TraceLeaf)?.let { TraceNavigator.navigateTo(project, it.trace) }
            }
        })

        project.messageBus.connect(toolWindow.disposable)
            .subscribe(TraceUpdateListener.TOPIC, TraceUpdateListener {
                rebuild(project, root, model)
                expandAll(tree, root)
            })

        val content = ContentFactory.getInstance().createContent(JBScrollPane(tree), null, false)
        toolWindow.contentManager.addContent(content)
        expandAll(tree, root)
    }

    private fun rebuild(project: Project, root: DefaultMutableTreeNode, model: DefaultTreeModel) {
        root.removeAllChildren()

        // group snapshot into file -> (line, marker, trace), sorted for stable display
        val byFile = sortedMapOf<String, MutableList<Triple<Int, MarkerInfo, TraceRecord>>>()
        TraceManager.getInstance(project).currentSnapshot.forEachLocation { fileName, line, marker, trace ->
            byFile.getOrPut(fileName) { mutableListOf() }.add(Triple(line, marker, trace))
        }

        if (byFile.isEmpty()) {
            root.add(DefaultMutableTreeNode("No traces loaded — run the app with the agent, then Reload."))
        } else {
            for ((file, entries) in byFile) {
                val fileNode = DefaultMutableTreeNode(file)
                for ((line, marker, trace) in entries.sortedBy { it.first }) {
                    fileNode.add(DefaultMutableTreeNode(TraceLeaf(line, marker, trace)))
                }
                root.add(fileNode)
            }
        }
        model.reload()
    }

    private fun expandAll(tree: JTree, root: DefaultMutableTreeNode) {
        for (i in 0 until root.childCount) {
            tree.expandPath(TreePath(arrayOf<Any>(root, root.getChildAt(i))))
        }
    }

    /** Leaf payload: a single marker occurrence with the source location to open. */
    private class TraceLeaf(val line: Int, val marker: MarkerInfo, val trace: TraceRecord) {
        override fun toString() = "line $line — ${marker.displayName} (${trace.className})"
    }

    /** Read-only tree that shows the trace model. */
    private class JBTreeReadOnly(model: DefaultTreeModel) : com.intellij.ui.treeStructure.Tree(model) {
        init {
            isRootVisible = false
            showsRootHandles = true
        }
    }
}
