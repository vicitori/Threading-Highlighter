package io.github.vicitori.threading.highlighter.agent.marker;

import io.github.vicitori.threading.highlighter.agent.common.AgentLog;
import io.github.vicitori.threading.highlighter.agent.trace.TraceWriter;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

/**
 * Byte Buddy advice that runs at the start of each instrumented assertion method.
 *
 * <p>This code is copied into other methods, so it must stay small and safe. The
 * {@link #IN_RECORD} flag stops endless recursion: recording a trace can itself
 * call an instrumented assertion, which would run this advice again.
 *
 * <p>The fields are {@code static} on purpose. Byte Buddy copies this code into
 * other methods, so there is no object or parameter to pass a {@link TraceWriter}
 * in. A public static field is the only way the copied code can reach it.
 */
public final class MarkerAdvice {
    // The single writer shared by every instrumented call in every thread.
    // It is set once at startup and only read after that.
    // It is volatile (not a lock) because one thread writes it while many other
    // threads read it. Without volatile a thread could keep seeing an old null.
    // There is one writer and no read-modify-write, so volatile is enough.
    public static volatile TraceWriter writer;

    // A guard with a separate value for each thread, so one thread that is recording
    // does not block another. The try/finally in onEnter always clears it.
    public static final ThreadLocal<Boolean> IN_RECORD = ThreadLocal.withInitial(() -> false);

    // Sets the writer. Call it only once, at startup.
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
