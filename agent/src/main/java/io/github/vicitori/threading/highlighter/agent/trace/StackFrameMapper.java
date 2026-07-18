package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.common.trace.TraceRecord;

/**
 * Turns a captured JVM stack frame into a {@link TraceRecord}.
 *
 * <p>This only builds the model. Writing a {@link TraceRecord} as a JSON line is
 * the job of {@link io.github.vicitori.threading.highlighter.common.trace.TraceJson}.
 */
public final class StackFrameMapper {

    public static TraceRecord toRecord(StackTraceElement element, long timestampMillis) {
        return new TraceRecord(
                element.getClassName(),
                element.getMethodName(),
                element.getFileName(),
                element.getLineNumber(),
                timestampMillis
        );
    }

    private StackFrameMapper() {
    }
}
