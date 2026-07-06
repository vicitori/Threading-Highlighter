package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.agent.common.TraceRecord;

/**
 * Maps a captured JVM stack frame to the domain model {@link TraceRecord}.
 *
 * <p>This is the domain mapping step, kept separate from the wire format: turning a
 * {@link TraceRecord} into a line on disk is {@link TraceLineCodec}'s job.
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
