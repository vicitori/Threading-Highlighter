package io.github.vicitori.threading.highlighter.plugin.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager
import io.github.vicitori.threading.highlighter.plugin.services.TraceUpdateListener
import java.awt.event.MouseEvent

private const val WIDGET_ID = "ThreadingHighlighter.StatusBar"

/**
 * Shows a one-glance status of the tool in the status bar: how many marked files are
 * loaded, or a hint when nothing is loaded yet. Clicking triggers a reload.
 *
 * This makes an empty gutter explainable ("no traces loaded") instead of silent.
 */
class TraceStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = WIDGET_ID

    override fun getDisplayName() = "Threading Highlighter"

    override fun createWidget(project: Project): StatusBarWidget = TraceStatusBarWidget(project)
}

private class TraceStatusBarWidget(
    private val project: Project,
) : StatusBarWidget,
    StatusBarWidget.TextPresentation {
    private var statusBar: StatusBar? = null

    override fun ID() = WIDGET_ID

    override fun getPresentation() = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        // update the text whenever traces are reloaded
        project.messageBus
            .connect(this)
            .subscribe(TraceUpdateListener.TOPIC, TraceUpdateListener { statusBar.updateWidget(WIDGET_ID) })
    }

    override fun getText(): String {
        val snapshot = TraceManager.getInstance(project).currentSnapshot
        return if (snapshot.isEmpty) "Threading: no traces" else "Threading: ${snapshot.markerCount} markers"
    }

    override fun getAlignment() = 0f

    override fun getTooltipText() = "Threading Highlighter — click to reload traces from the agent output"

    override fun getClickConsumer() = Consumer<MouseEvent> {
        TraceManager.getInstance(project).reloadTraces()
    }

    override fun dispose() {
        statusBar = null
    }
}
