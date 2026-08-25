package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.agent.common.AgentLog;
import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads a limited part of the current call stack with {@link StackWalker}.
 *
 * <p>StackWalker reads the stack step by step, so it is cheaper than
 * {@code new Throwable().getStackTrace()}, which builds the whole stack at once.
 * Frames are filtered through an {@link IncludePackageFilter} <em>before</em> the
 * depth limit, so the analyzed plugin's own frames are kept even when hundreds of
 * platform frames sit above them (IntelliJ stacks can have 200–500 frames).
 *
 * <p>The filter is an allow list keyed on the base packages the user declares via
 * {@link ThreadingHighlighterConfig#INCLUDE_PACKAGES_PROPERTY}. There is no block
 * list of framework packages: only declared code is captured (see
 * {@link IncludePackageFilter}).
 */
final class StackCapture {

    static final String MAX_DEPTH_PROPERTY = "threading.highlighter.max.stack.depth";

    // 128 = two times the JFR default of 64 frames, enough for deep IntelliJ stacks
    private static final int DEFAULT_MAX_DEPTH = 128;
    private static final int MIN_ALLOWED_DEPTH = 1;

    private final StackWalker walker = StackWalker.getInstance();
    private final int maxDepth;
    private final IncludePackageFilter includeFilter;

    StackCapture() {
        this.maxDepth = resolveMaxDepth();
        this.includeFilter = IncludePackageFilter.fromProperty(
                System.getProperty(ThreadingHighlighterConfig.INCLUDE_PACKAGES_PROPERTY));
        warnIfNoPackagesConfigured();
    }

    List<StackTraceElement> capture() {
        // No packages means the filter matches nothing: skip the walk entirely so the
        // hot path stays cheap when the tool is misconfigured (warning already logged).
        if (includeFilter.isEmpty()) {
            return List.of();
        }
        return walker.walk(this::captureFrames);
    }

    private List<StackTraceElement> captureFrames(Stream<StackWalker.StackFrame> frames) {
        return frames.skip(1) // skip capture() itself
                .map(StackWalker.StackFrame::toStackTraceElement)
                .filter(frame -> includeFilter.includes(frame.getClassName())) // allow list, see class javadoc
                .limit(maxDepth) // filter before limit: see class javadoc
                .collect(Collectors.toList());
    }

    private void warnIfNoPackagesConfigured() {
        if (includeFilter.isEmpty()) {
            AgentLog.warn("No base packages configured via '" + ThreadingHighlighterConfig.INCLUDE_PACKAGES_PROPERTY
                    + "', so no user frames will be captured. Set it to your plugin's base package(s), e.g. -D"
                    + ThreadingHighlighterConfig.INCLUDE_PACKAGES_PROPERTY + "=com.example.myplugin");
        } else {
            AgentLog.info("Capturing frames under: " + String.join(", ", includeFilter.includedPackages()));
        }
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
