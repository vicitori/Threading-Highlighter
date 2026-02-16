package io.github.vicitori.threading.highlighter.common.marker

object Markers {
    @JvmField
    val SLOW_OPERATION = MarkerInfo(
        classFqn = "com.intellij.util.SlowOperations",
        methodName = "assertSlowOperationsAreAllowed",
        displayName = "Slow Operation",
        description = "Slow operations are allowed here. This indicates code that may perform I/O or heavy computation."
    )

    @JvmField
    val NON_EDT = MarkerInfo(
        classFqn = "com.intellij.openapi.application.impl.ApplicationImpl",
        methodName = "assertIsNonDispatchThread",
        displayName = "Non-EDT Thread",
        description = "This code must NOT run on the EDT. Background/pooled thread required."
    )

    @JvmField
    val EDT = MarkerInfo(
        classFqn = "com.intellij.openapi.application.impl.ApplicationImpl",
        methodName = "assertIsDispatchThread",
        displayName = "EDT Thread",
        description = "This code must run on the EDT (Event Dispatch Thread). UI operations are allowed."
    )

    @JvmStatic
    fun getAll(): List<MarkerInfo> = listOf(SLOW_OPERATION, NON_EDT, EDT)
}
