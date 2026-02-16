package io.github.vicitori.threading.highlighter.common.trace

import kotlinx.serialization.Serializable

@Serializable
data class TraceRecord(
    val className: String,
    val methodName: String,
    val fileName: String?,
    val lineNumber: Int,
    val lastSeenTimestampEpochMillis: Long
) {
    @JvmName("getKey")
    fun getKey(): String = "$className#$methodName@$lineNumber"
}
