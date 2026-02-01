package io.github.vicitori.threading.highlighter.common.marker

object Markers {
    @JvmField
    val SLOW_OPERATION = MarkerInfo(
        classFqn = "com.intellij.util.SlowOperations",
        methodName = "assertSlowOperationsAreAllowed"
    )

    @JvmField
    val NON_EDT = MarkerInfo(
        classFqn = "com.intellij.openapi.application.impl.ApplicationImpl",
        methodName = "assertIsNonDispatchThread"
    )

    @JvmField
    val EDT = MarkerInfo(
        classFqn = "com.intellij.openapi.application.impl.ApplicationImpl",
        methodName = "assertIsDispatchThread"
    )
}
