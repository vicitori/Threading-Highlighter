package io.github.vicitori.threading.highlighter.agent.trace;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Guards the invariants of the capture step under the allow-list model: only frames
 * under the configured packages survive, an unset package list captures nothing, and
 * the depth limit is honored. Assertions check properties that hold regardless of the
 * exact runtime stack, so the tests stay deterministic.
 */
class StackCaptureTest {

    // This test class lives here, so its own frame is captured when we allow this package.
    private static final String THIS_PACKAGE = "io.github.vicitori.threading.highlighter.agent.trace";

    private String originalMaxDepth;
    private String originalIncludePackages;

    @BeforeEach
    void rememberProperties() {
        originalMaxDepth = System.getProperty(StackCapture.MAX_DEPTH_PROPERTY);
        originalIncludePackages = System.getProperty(ThreadingHighlighterConfig.INCLUDE_PACKAGES_PROPERTY);
    }

    @AfterEach
    void restoreProperties() {
        restore(StackCapture.MAX_DEPTH_PROPERTY, originalMaxDepth);
        restore(ThreadingHighlighterConfig.INCLUDE_PACKAGES_PROPERTY, originalIncludePackages);
    }

    @Test
    void captureKeepsOnlyFramesUnderConfiguredPackages() {
        System.setProperty(ThreadingHighlighterConfig.INCLUDE_PACKAGES_PROPERTY, THIS_PACKAGE);

        List<StackTraceElement> frames = new StackCapture().capture();

        assertFalse(frames.isEmpty(), "expected this test's own frame to be captured");
        assertTrue(
                frames.stream().allMatch(f -> f.getClassName().startsWith(THIS_PACKAGE)),
                () -> "foreign frame leaked past the allow list: " + frames);
    }

    @Test
    void captureReturnsNothingWhenNoPackagesConfigured() {
        System.clearProperty(ThreadingHighlighterConfig.INCLUDE_PACKAGES_PROPERTY);

        List<StackTraceElement> frames = new StackCapture().capture();

        assertTrue(frames.isEmpty(), () -> "expected empty capture without configured packages, got " + frames);
    }

    @Test
    void captureNeverExceedsConfiguredDepth() {
        System.setProperty(ThreadingHighlighterConfig.INCLUDE_PACKAGES_PROPERTY, THIS_PACKAGE);
        System.setProperty(StackCapture.MAX_DEPTH_PROPERTY, "1");

        List<StackTraceElement> frames = new StackCapture().capture();

        assertTrue(frames.size() <= 1, () -> "expected <= 1 frame, got " + frames.size());
    }

    @Test
    void negativeDepthFallsBackToDefaultInsteadOfCrashing() {
        System.setProperty(ThreadingHighlighterConfig.INCLUDE_PACKAGES_PROPERTY, THIS_PACKAGE);
        System.setProperty(StackCapture.MAX_DEPTH_PROPERTY, "-1");

        // premain must not crash the host JVM: a bad value must not blow up capture()
        assertDoesNotThrow(() -> new StackCapture().capture());
    }

    private static void restore(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
