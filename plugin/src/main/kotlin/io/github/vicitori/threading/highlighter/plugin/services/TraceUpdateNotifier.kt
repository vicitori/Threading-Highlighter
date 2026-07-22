package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.util.messages.Topic

/**
 * Notifies listeners (e.g. the tool window) that traces were reloaded and a new
 * snapshot is available. Published on the EDT after the snapshot is set.
 */
fun interface TraceUpdateListener {
    fun tracesUpdated()

    companion object {
        @JvmField
        val TOPIC: Topic<TraceUpdateListener> =
            Topic.create("Threading Highlighter traces updated", TraceUpdateListener::class.java)
    }
}
