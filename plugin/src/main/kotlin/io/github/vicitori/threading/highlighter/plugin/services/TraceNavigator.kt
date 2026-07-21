package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord

/**
 * Opens the source location of a trace record in the editor.
 *
 * Language-agnostic (Kotlin and Java both compile to a JVM class whose package
 * matches the source path): resolves the file by its simple name, disambiguates by
 * the class package, and jumps to the recorded line.
 */
object TraceNavigator {

    /** Navigates to the record's file and line. Returns true if a file was opened. */
    fun navigateTo(project: Project, trace: TraceRecord): Boolean {
        val fileName = trace.fileName ?: return false
        val target = findFile(project, fileName, trace.className) ?: return false
        // trace line numbers are 1-based; OpenFileDescriptor expects 0-based
        val line = (trace.lineNumber - 1).coerceAtLeast(0)
        OpenFileDescriptor(project, target, line, 0).navigate(true)
        return true
    }

    private fun findFile(project: Project, fileName: String, className: String): VirtualFile? {
        val candidates = ReadAction.compute<Collection<VirtualFile>, RuntimeException> {
            FilenameIndex.getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project))
        }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        // several files share the simple name: keep the one whose path matches the package
        val packagePath = className.substringBefore('$')
            .substringBeforeLast('.', missingDelimiterValue = "")
            .replace('.', '/')
        if (packagePath.isEmpty()) return candidates.first()
        return candidates.firstOrNull { it.path.replace('\\', '/').contains("/$packagePath/") }
            ?: candidates.first()
    }
}
