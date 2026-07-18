package io.github.vicitori.threading.highlighter.common.config;

import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo;

import java.io.File;
import java.nio.file.Path;

/**
 * Tells where trace files live and how they are named. The agent (writer) and the
 * plugin (reader) both use it.
 *
 * <p>Plain Java, so both sides share it. Because the rules are in one place, the
 * writer and the reader always agree on the path and the file names.
 */
public final class ThreadingHighlighterConfig {

    public static final String PROJECT_DIR_PROPERTY = "threading.highlighter.project.dir";
    public static final String TRACES_DIR_NAME = ".ij-threading-highlighter";

    private static final String SAFE_FILENAME_PATTERN = "[^a-zA-Z0-9._-]";
    private static final int MAX_PARENT_LEVELS = 3;

    public static Path getTracesPath(String baseDir) {
        return Path.of(baseDir).resolve(TRACES_DIR_NAME);
    }

    /**
     * Resolves the traces directory the agent writes to.
     *
     * <p>The agent writes deterministically to {@code <project.dir>/TRACES_DIR_NAME},
     * where {@code project.dir} comes from {@link #PROJECT_DIR_PROPERTY}. To keep the
     * writer (agent) and reader (plugin) in sync, this honors the <b>same</b> property
     * first: when it is set, the path is deterministic and identical on both sides.
     * Only when it is absent do we fall back to a best-effort heuristic search.
     */
    public static Path findTracesPath(String baseDir) {
        // explicit shared contract: same property the agent uses, no guessing
        String explicitDir = System.getProperty(PROJECT_DIR_PROPERTY);
        if (explicitDir != null) {
            return getTracesPath(explicitDir);
        }

        Path basePath = Path.of(baseDir);

        Path currentDirTraces = basePath.resolve(TRACES_DIR_NAME);
        if (isDirectory(currentDirTraces)) {
            return currentDirTraces;
        }

        File[] subDirs = basePath.toFile().listFiles();
        if (subDirs != null) {
            for (File subDir : subDirs) {
                if (subDir.isDirectory()) {
                    Path subDirTraces = subDir.toPath().resolve(TRACES_DIR_NAME);
                    if (isDirectory(subDirTraces)) {
                        return subDirTraces;
                    }
                }
            }
        }

        // search in parent directories (up to MAX_PARENT_LEVELS levels)
        Path currentPath = basePath.getParent();
        int levelsUp = 0;
        while (currentPath != null && levelsUp < MAX_PARENT_LEVELS) {
            Path parentTraces = currentPath.resolve(TRACES_DIR_NAME);
            if (isDirectory(parentTraces)) {
                return parentTraces;
            }
            currentPath = currentPath.getParent();
            levelsUp++;
        }

        return currentDirTraces;
    }

    public static Path getTracesPathFromSystemProperty() {
        String projectDir = System.getProperty(PROJECT_DIR_PROPERTY);
        if (projectDir == null) {
            throw new IllegalStateException(
                    "System property '" + PROJECT_DIR_PROPERTY + "' is not set.\n"
                            + "Please configure it in your build.gradle.kts:\n"
                            + "systemProperty(\"" + PROJECT_DIR_PROPERTY + "\", \"${project.projectDir}\")"
            );
        }
        return getTracesPath(projectDir);
    }

    public static String getTraceFileName(String markerFqn) {
        return markerFqn.replaceAll(SAFE_FILENAME_PATTERN, "_") + ".jsonl";
    }

    public static String getTraceFileName(MarkerInfo marker) {
        return getTraceFileName(marker.markerFqn());
    }

    private static boolean isDirectory(Path path) {
        return path.toFile().isDirectory();
    }

    private ThreadingHighlighterConfig() {
    }
}
