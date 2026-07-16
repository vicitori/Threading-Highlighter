package io.github.vicitori.threading.highlighter.plugin.services

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Finds the agent jar bundled inside this plugin's installation.
 *
 * The build copies `agent.jar` into the plugin's `lib/` directory, so it ships with
 * the plugin from Marketplace. This resolves its absolute path at runtime instead of
 * asking the user to download the jar and guess where it lives.
 */
object AgentLocator {
    private const val PLUGIN_ID = "io.github.vicitori.threading.highlighter"
    private const val AGENT_JAR_NAME = "agent.jar"

    /** Absolute path to the bundled agent jar, or null if it cannot be found. */
    fun findAgentJar(): Path? {
        val descriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID)) ?: return null
        val agentPath = descriptor.pluginPath.resolve("lib").resolve(AGENT_JAR_NAME)
        return if (agentPath.exists()) agentPath else null
    }
}
