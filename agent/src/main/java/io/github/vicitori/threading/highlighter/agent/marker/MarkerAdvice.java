package io.github.vicitori.threading.highlighter.agent.marker;

import io.github.vicitori.threading.highlighter.agent.instruction.InstructionWriter;
import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

public final class MarkerAdvice {
    public static volatile InstructionWriter writer;
    public static final ThreadLocal<Boolean> IN_RECORD = ThreadLocal.withInitial(() -> false);

    public static void setWriter(InstructionWriter w) {
        writer = w;
    }

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Origin Method method) {
        InstructionWriter w = writer;
        if (w == null) {
            return;
        }
        if (IN_RECORD.get()) {
            return;
        }

        IN_RECORD.set(true);
        try {
            Throwable stackTraceHolder = new Throwable();
            String markerFqn = method.getDeclaringClass().getName() + "#" + method.getName();
            System.err.println("[MarkerAdvice] MARKER INTERCEPTED: " + markerFqn);
            w.record(markerFqn, stackTraceHolder);
        } finally {
            IN_RECORD.set(false);
        }
    }
}
