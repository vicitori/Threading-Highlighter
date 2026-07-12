package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Tracks whether gutter markers should be shown.
 *
 * Two independent flags, so a background reload never overrides the user's choice:
 * [dataAvailable] is set once traces load, [userHidden] is the explicit toggle.
 * Markers show only when data exists and the user has not hidden them.
 */
@Service(Service.Level.PROJECT)
class MarkerStateService {
    @Volatile
    private var dataAvailable = false

    @Volatile
    private var userHidden = false

    companion object {
        fun getInstance(project: Project): MarkerStateService = project.service()
    }

    fun areMarkersEnabled(): Boolean = dataAvailable && !userHidden

    fun markDataAvailable() {
        dataAvailable = true
    }

    /** Flips user visibility and returns the new "visible" state. */
    fun toggleVisibility(): Boolean {
        userHidden = !userHidden
        return !userHidden
    }

    fun isHiddenByUser(): Boolean = userHidden
}
