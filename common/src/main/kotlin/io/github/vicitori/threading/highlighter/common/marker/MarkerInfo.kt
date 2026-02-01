package io.github.vicitori.threading.highlighter.common.marker

data class MarkerInfo(
    @JvmField val classFqn: String,
    @JvmField val methodName: String
) {
    fun markerFqn(): String = "$classFqn#$methodName"
}
