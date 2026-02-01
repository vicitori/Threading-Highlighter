package io.github.vicitori.threading.highlighter.agent.marker;

import net.bytebuddy.asm.Advice;
import io.github.vicitori.threading.highlighter.agent.trace.TraceWriter;

import static io.github.vicitori.threading.highlighter.agent.marker.Markers.SLOW_OPERATION;

public final class MarkerAdvice {
    public static volatile TraceWriter writer;
    public static final ThreadLocal<Boolean> IN_RECORD = ThreadLocal.withInitial(() -> false);

    public static void setWriter(TraceWriter w) {
        writer = w;
    }

    @Advice.OnMethodEnter
    public static void onEnter() {
        TraceWriter w = writer;
        if (w == null) {
            return;
        }
        if (IN_RECORD.get()) {
            return;
        }

        IN_RECORD.set(true);
        try {
            Throwable stackTraceHolder = new Throwable();
            System.err.println("MARKER INTERCEPTED!");
            w.record(SLOW_OPERATION.markerFqn(), stackTraceHolder);
        } finally {
            IN_RECORD.set(false);
        }
    }
}
