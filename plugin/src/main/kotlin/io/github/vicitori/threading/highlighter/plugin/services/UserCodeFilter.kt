package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Decides which stack frames belong to the user's own code.
 *
 * User packages are guessed from the project source roots. If none are found,
 * [isUserCode] returns true for every frame (show all, hide nothing).
 */
object UserCodeFilter {
    fun getUserPackages(project: Project): List<String> {
        val detectedPackages = detectUserPackages(project)
        if (detectedPackages.isNotEmpty()) {
            return detectedPackages
        }
        return emptyList()
    }

    private fun detectUserPackages(project: Project): List<String> {
        val packages = mutableSetOf<String>()
        try {
            val projectRootManager = ProjectRootManager.getInstance(project)
            val sourceRoots = projectRootManager.contentSourceRoots

            for (sourceRoot in sourceRoots) {
                val path = sourceRoot.path
                if (!path.contains("/src/main/kotlin") && !path.contains("/src/main/java")) {
                    continue
                }

                sourceRoot.children.forEach { topLevelDir ->
                    if (topLevelDir.isDirectory) {
                        val packagePath = buildPackagePath(topLevelDir)
                        if (packagePath.isNotEmpty()) {
                            packages.add(packagePath)
                        }
                    }
                }
            }
            val sorted = packages.sorted()
            return sorted
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun buildPackagePath(dir: VirtualFile): String {
        val parts = mutableListOf<String>()
        collectPackageParts(dir, parts)
        return parts.joinToString(".")
    }

    private fun collectPackageParts(dir: VirtualFile, parts: MutableList<String>) {
        parts.add(dir.name)
        val subdirs = dir.children.filter { it.isDirectory }
        // follow single-child folders down to the real base package (e.g. io/github/app)
        if (subdirs.size == 1) {
            collectPackageParts(subdirs[0], parts)
        }
    }

    fun isUserCode(className: String, userPackages: List<String>): Boolean {
        // no packages detected: do not hide anything
        if (userPackages.isEmpty()) {
            return true
        }

        return userPackages.any { className.startsWith(it) }
    }
}
