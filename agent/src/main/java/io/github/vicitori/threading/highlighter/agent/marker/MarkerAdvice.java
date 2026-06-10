package io.github.vicitori.threading.highlighter.agent.marker;

import io.github.vicitori.threading.highlighter.agent.common.AgentLog;
import io.github.vicitori.threading.highlighter.agent.trace.TraceWriter;
import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

/**
 * Byte Buddy advice that runs at the start of each instrumented assertion method.
 *
 * <p>This code is copied into host methods, so it must stay small and safe. The
 * {@link #IN_RECORD} flag stops endless recursion: recording a trace can itself
 * call an instrumented assertion, which would trigger this advice again.
 */
public final class MarkerAdvice {
    public static volatile TraceWriter writer;
    public static final ThreadLocal<Boolean> IN_RECORD = ThreadLocal.withInitial(() -> false);

    public static void setWriter(TraceWriter w) {
        writer = w;
    }

    @SuppressWarnings("unused") // Called by Byte Buddy instrumentation
    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Origin Method method) {
        TraceWriter w = writer;
        if (w == null) {
            return;
        }
        if (IN_RECORD.get()) {
            return;
        }

        IN_RECORD.set(true);
        try {
            String markerFqn = method.getDeclaringClass().getName() + "#" + method.getName();
            w.record(markerFqn);
        } catch (Throwable t) {
            // Never propagate agent failures into the instrumented host method
            AgentLog.error("Failed to record trace", t);
        } finally {
            IN_RECORD.set(false);
        }
    }
}
