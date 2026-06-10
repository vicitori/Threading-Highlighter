package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.agent.common.AgentLog;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads a limited part of the current call stack with {@link StackWalker}.
 *
 * <p>StackWalker reads the stack step by step, so it is cheaper than
 * {@code new Throwable().getStackTrace()}, which builds the whole stack at once.
 * Framework frames are removed <em>before</em> the depth limit, so user code is
 * kept even when many platform frames are above it (IntelliJ stacks can have
 * 200–500 frames).
 */
final class StackCapture {

    static final String MAX_DEPTH_PROPERTY = "threading.highlighter.max.stack.depth";

    // 128 = two times the JFR default of 64 frames, enough for deep IntelliJ stacks
    private static final int DEFAULT_MAX_DEPTH = 128;
    private static final int MIN_ALLOWED_DEPTH = 1;

    // Platform and JDK packages, their frames are never user code
    private static final String[] FRAMEWORK_PREFIXES = {
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "com.sun.",
            "kotlin.",
            "com.intellij.",
            "io.github.vicitori.threading.highlighter.", // the agent itself
    };

    private final StackWalker walker = StackWalker.getInstance();
    private final int maxDepth;

    StackCapture() {
        this.maxDepth = resolveMaxDepth();
    }

    List<StackTraceElement> capture() {
        return walker.walk(this::captureFrames);
    }

    private List<StackTraceElement> captureFrames(Stream<StackWalker.StackFrame> frames) {
        return frames
                .skip(1) // skip capture() itself
                .map(StackWalker.StackFrame::toStackTraceElement)
                .filter(StackCapture::isUserCode) // filter before limit: see class javadoc
                .limit(maxDepth)
                .collect(Collectors.toList());
    }

    private static boolean isUserCode(StackTraceElement frame) {
        String className = frame.getClassName();
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private static int resolveMaxDepth() {
        String property = System.getProperty(MAX_DEPTH_PROPERTY);
        if (property == null) {
            return DEFAULT_MAX_DEPTH;
        }
        try {
            int value = Integer.parseInt(property.trim());
            if (value >= MIN_ALLOWED_DEPTH) {
                return value;
            }
            // premain must not crash the host JVM: log a warning and use the default
            AgentLog.warn("Ignoring non-positive " + MAX_DEPTH_PROPERTY + ": " + value);
        } catch (NumberFormatException e) {
            AgentLog.warn("Ignoring invalid " + MAX_DEPTH_PROPERTY + ": " + property);
        }
        return DEFAULT_MAX_DEPTH;
    }
}
