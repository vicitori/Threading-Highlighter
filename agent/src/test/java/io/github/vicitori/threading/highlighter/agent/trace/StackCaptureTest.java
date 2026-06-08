package io.github.vicitori.threading.highlighter.agent.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the two invariants of the capture step: framework/JDK frames are dropped,
 * and the depth limit is honored. Assertions check properties that hold regardless
 * of the exact runtime stack, so the tests stay deterministic.
 */
class StackCaptureTest {

    private String originalMaxDepth;

    @BeforeEach
    void rememberProperty() {
        originalMaxDepth = System.getProperty(StackCapture.MAX_DEPTH_PROPERTY);
    }

    @AfterEach
    void restoreProperty() {
        if (originalMaxDepth == null) {
            System.clearProperty(StackCapture.MAX_DEPTH_PROPERTY);
        } else {
            System.setProperty(StackCapture.MAX_DEPTH_PROPERTY, originalMaxDepth);
        }
    }

    @Test
    void captureExcludesJdkAndPlatformFrames() {
        List<StackTraceElement> frames = new StackCapture().capture();

        assertTrue(frames.stream().noneMatch(f -> isFrameworkFrame(f.getClassName())),
                () -> "framework frame leaked: " + frames);
    }

    @Test
    void captureNeverExceedsConfiguredDepth() {
        System.setProperty(StackCapture.MAX_DEPTH_PROPERTY, "2");

        List<StackTraceElement> frames = new StackCapture().capture();

        assertTrue(frames.size() <= 2, () -> "expected <= 2 frames, got " + frames.size());
    }

    @Test
    void negativeDepthFallsBackToDefaultInsteadOfCrashing() {
        System.setProperty(StackCapture.MAX_DEPTH_PROPERTY, "-1");

        // premain must not crash the host JVM: a bad value must not blow up capture()
        assertDoesNotThrow(() -> new StackCapture().capture());
    }

    private static boolean isFrameworkFrame(String className) {
        return className.startsWith("java.")
                || className.startsWith("jdk.")
                || className.startsWith("com.intellij.")
                || className.startsWith("io.github.vicitori.threading.highlighter.");
    }
}
