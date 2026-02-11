package io.github.vicitori.threading.highlighter.plugin.models

data class TraceRecord(
    val className: String,
    val methodName: String,
    val fileName: String?,
    val lineNumber: Int,
    val lastSeenTimestampEpochMillis: Long
)
