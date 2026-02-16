package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class MarkerStateService {
    @Volatile
    private var markersEnabled = false

    companion object {
        fun getInstance(project: Project): MarkerStateService = project.service()
    }

    fun areMarkersEnabled(): Boolean = markersEnabled
    fun enableMarkers() {
        markersEnabled = true
    }
}
