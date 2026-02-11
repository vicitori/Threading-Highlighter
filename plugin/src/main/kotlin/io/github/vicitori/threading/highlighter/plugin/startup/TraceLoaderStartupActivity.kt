package io.github.vicitori.threading.highlighter.plugin.startup

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.github.vicitori.threading.highlighter.plugin.services.TraceManager

class TraceLoaderStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val traceManager = TraceManager.getInstance(project)
        traceManager.reloadTraces()

        ApplicationManager.getApplication().invokeLater {
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
}
