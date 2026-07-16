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
 *
 * <p>Why global static state: Byte Buddy inlines this advice into foreign methods,
 * so there is no instance, constructor or parameter through which a TraceWriter
 * could be handed in. A public static field is the only channel the inlined code
 * can reach, which is why the fields below are global by necessity, not by design.
 */
public final class MarkerAdvice {
    // Single trace sink shared by every instrumented call in every thread.
    // Invariant: written once, read many. Assigned exactly once by the agent's
    // idempotent install() (AtomicBoolean-guarded, so neither premain nor a later
    // agentmain attach can create a second writer); only ever read afterwards.
    // volatile (not a lock) is required: the write is on the agent thread while
    // reads happen on every host thread hitting an assertion, so without it those
    // threads could keep seeing a stale null. No concurrent writer and no compound
    // update, so volatile is sufficient.
    public static volatile TraceWriter writer;

    // Per-thread re-entrancy guard: each thread owns its own copy, so one thread
    // being mid-recording never blocks another. The try/finally in onEnter always
    // clears it, even on exception.
    public static final ThreadLocal<Boolean> IN_RECORD = ThreadLocal.withInitial(() -> false);

    // Installs the trace sink. Must be called exactly once, from the agent's
    // idempotent install(); see the writer invariant above.
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
