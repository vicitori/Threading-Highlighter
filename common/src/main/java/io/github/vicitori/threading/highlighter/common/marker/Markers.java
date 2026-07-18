package io.github.vicitori.threading.highlighter.common.marker;

import java.util.List;

/**
 * The list of all known IntelliJ threading assertion markers.
 *
 * <p>One shared list for both the agent (writer) and the plugin (reader), so they
 * always use the same markers.
 */
public final class Markers {

    public static final MarkerInfo SLOW_OPERATION = new MarkerInfo(
            "com.intellij.util.SlowOperations",
            "assertSlowOperationsAreAllowed",
            "Slow Operation",
            "Slow operations are allowed here. This indicates code that may perform I/O or heavy computation."
    );

    public static final MarkerInfo NON_EDT = new MarkerInfo(
            "com.intellij.openapi.application.impl.ApplicationImpl",
            "assertIsNonDispatchThread",
            "Non-EDT Thread",
            "This code must NOT run on the EDT. Background/pooled thread required."
    );

    public static final MarkerInfo EDT = new MarkerInfo(
            "com.intellij.openapi.application.impl.ApplicationImpl",
            "assertIsDispatchThread",
            "EDT Thread",
            "This code must run on the EDT (Event Dispatch Thread). UI operations are allowed."
    );

    private static final List<MarkerInfo> ALL = List.of(SLOW_OPERATION, NON_EDT, EDT);

    public static List<MarkerInfo> getAll() {
        return ALL;
    }

    private Markers() {
    }
}
