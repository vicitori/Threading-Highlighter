package io.github.vicitori.threading.highlighter.agent.common;

import java.nio.file.Path;

/**
 * Agent-side configuration utilities.
 * Values must stay in sync with the plugin-side {@code ThreadingHighlighterConfig}.
 */
public final class AgentConfig {

    public static final String PROJECT_DIR_PROPERTY = "threading.highlighter.project.dir";
    public static final String TRACES_DIR_NAME = ".ij-threading-highlighter";

    private static final String SAFE_FILENAME_PATTERN = "[^a-zA-Z0-9._-]";

    public static Path getTracesPath(String baseDir) {
        return Path.of(baseDir).resolve(TRACES_DIR_NAME);
    }

    public static Path getTracesPathFromSystemProperty() {
        String projectDir = System.getProperty(PROJECT_DIR_PROPERTY);
        if (projectDir == null) {
            throw new IllegalStateException(
                    "System property '" + PROJECT_DIR_PROPERTY + "' is not set. " +
                    "Please configure it in your build.gradle.kts: " +
                    "systemProperty(\"" + PROJECT_DIR_PROPERTY + "\", \"${project.projectDir}\")"
            );
        }
        return getTracesPath(projectDir);
    }

    public static String getTraceFileName(String markerFqn) {
        return markerFqn.replaceAll(SAFE_FILENAME_PATTERN, "_") + ".jsonl";
    }

    private AgentConfig() {
    }
}
