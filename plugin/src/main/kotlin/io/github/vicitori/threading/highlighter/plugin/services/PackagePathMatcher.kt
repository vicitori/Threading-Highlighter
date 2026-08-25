package io.github.vicitori.threading.highlighter.plugin.services

/**
 * Matches a source file path against a class's package.
 *
 * `StackTraceElement.fileName` is a simple name, so files with the same name in
 * different packages collide. Both the annotator (which record belongs to this file)
 * and the navigator (which candidate file to open) need the same rule, kept here so
 * they cannot drift apart.
 */
object PackagePathMatcher {
    /** Package of a class FQN; nested/lambda classes use `$`, dropped first. */
    fun packageOf(className: String): String = className.substringBefore('$').substringBeforeLast('.', missingDelimiterValue = "")

    /**
     * True if [filePath] belongs to [packageName]. When [fileName] is given, the path
     * must end with `/<package>/<file>` (both package and file line up); otherwise it
     * only needs to contain the package segment.
     */
    fun matches(
        filePath: String,
        packageName: String,
        fileName: String?,
    ): Boolean {
        val normalized = filePath.replace('\\', '/')
        if (fileName == null) {
            if (packageName.isEmpty()) return true
            return normalized.contains("/${packageName.replace('.', '/')}/")
        }
        val anchor = if (packageName.isEmpty()) "/$fileName" else "/${packageName.replace('.', '/')}/$fileName"
        return normalized.endsWith(anchor)
    }
}
