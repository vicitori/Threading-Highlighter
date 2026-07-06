package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig

/**
 * Loads traces on project open and auto-reloads them when the agent writes to the
 * traces directory.
 *
 * Separation of concerns: this watcher only observes the file system and, after a
 * short debounce, asks [TraceManager] to reload. It never parses `.jsonl` itself.
 * Debounce coalesces the many events the agent produces while writing a file into
 * a single reload.
 */
private const val DEBOUNCE_MS = 300

class TraceFileWatcher : ProjectActivity {
    override suspend fun execute(project: Project) {
        val disposable = TraceWatcherDisposable.getInstance(project)
        val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)

        project.messageBus.connect(disposable).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.none { it.isInTracesDir() }) return
                    alarm.cancelAllRequests()
                    alarm.addRequest({ TraceManager.getInstance(project).reloadTraces() }, DEBOUNCE_MS)
                }
            }
        )

        // initial load: the service starts empty and VFS events only fire on later
        // changes, so without this an already-populated traces dir would stay hidden
        // until a manual reload
        TraceManager.getInstance(project).reloadTraces()
    }

    private fun VFileEvent.isInTracesDir(): Boolean =
        path.contains(ThreadingHighlighterConfig.TRACES_DIR_NAME)
}

/**
 * Project-scoped [Disposable] that ties the watcher's listener and alarm to the
 * project lifetime, so they are cleaned up when the project closes.
 */
@com.intellij.openapi.components.Service(com.intellij.openapi.components.Service.Level.PROJECT)
class TraceWatcherDisposable : Disposable {
    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): TraceWatcherDisposable =
            project.getService(TraceWatcherDisposable::class.java)
    }
}
