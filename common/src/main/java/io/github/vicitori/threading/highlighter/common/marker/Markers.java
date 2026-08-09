package io.github.vicitori.threading.highlighter.common.marker;

import java.util.List;

/**
 * The list of all known IntelliJ threading assertion markers.
 *
 * <p>One shared list for both the agent (writer) and the plugin (reader), so they
 * always use the same markers.
 *
 * <p>The IntelliJ threading model has two independent axes, and the markers cover
 * both:
 * <ul>
 *   <li><b>Thread affinity</b> — on which thread code may run: {@link #EDT},
 *       {@link #NON_EDT}, and {@link #SLOW_OPERATION} (must be off the EDT).</li>
 *   <li><b>Lock affinity</b> — which read/write lock must be held: {@link #READ_ACCESS}
 *       and {@link #WRITE_ACCESS}.</li>
 * </ul>
 *
 * <p>These are the assertions IntelliJ plugin code hits most often in practice.
 * The list is deliberately fixed rather than user-configurable: the agent copies
 * advice into these exact methods at load time, and an arbitrary target could
 * recurse into itself or instrument non-assertion code. Extending coverage is a
 * matter of adding an entry here (the agent, filter, trace format and plugin are
 * all marker-agnostic).
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

    public static final MarkerInfo READ_ACCESS = new MarkerInfo(
            "com.intellij.openapi.application.impl.ApplicationImpl",
            "assertReadAccessAllowed",
            "Read Access",
            "This code requires read access and must run inside a read action (see Application.runReadAction())."
    );

    public static final MarkerInfo WRITE_ACCESS = new MarkerInfo(
            "com.intellij.openapi.application.impl.ApplicationImpl",
            "assertWriteAccessAllowed",
            "Write Access",
            "This code requires write access and must run inside a write action on the EDT (see Application.runWriteAction())."
    );

    private static final List<MarkerInfo> ALL =
            List.of(SLOW_OPERATION, NON_EDT, EDT, READ_ACCESS, WRITE_ACCESS);

    public static List<MarkerInfo> getAll() {
        return ALL;
    }

    private Markers() {
    }
}
