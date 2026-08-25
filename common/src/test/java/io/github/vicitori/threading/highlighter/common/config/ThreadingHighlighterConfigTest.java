package io.github.vicitori.threading.highlighter.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.vicitori.threading.highlighter.common.marker.Markers;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Covers the filename sanitization and traces-path rules shared by the agent and
 * plugin. A broken mapping here silently corrupts file names or lets two markers
 * collide into one file, which the compiler cannot catch.
 */
class ThreadingHighlighterConfigTest {

    @Test
    void sanitizesHashInRealMarkerFqn() {
        // '#' from markerFqn() is not filename-safe; dots stay, extension is added
        String fileName = ThreadingHighlighterConfig.getTraceFileName(Markers.SLOW_OPERATION.markerFqn());

        assertEquals("com.intellij.util.SlowOperations_assertSlowOperationsAreAllowed.jsonl", fileName);
    }

    @Test
    void replacesNestedClassAndLambdaSymbols() {
        // '$' (nested/lambda classes) is unsafe on some filesystems and must be replaced
        String fileName = ThreadingHighlighterConfig.getTraceFileName("com.example.Foo$1$$Lambda#invoke");

        assertEquals("com.example.Foo_1__Lambda_invoke.jsonl", fileName);
    }

    @Test
    void keepsAlreadySafeCharacters() {
        String fileName = ThreadingHighlighterConfig.getTraceFileName("a.b_c-d.9");

        assertEquals("a.b_c-d.9.jsonl", fileName);
    }

    @Test
    void markersSharingClassGetDistinctFiles() {
        // EDT and NON_EDT are different methods of the same class: files must not collide
        String edt = ThreadingHighlighterConfig.getTraceFileName(Markers.EDT.markerFqn());
        String nonEdt = ThreadingHighlighterConfig.getTraceFileName(Markers.NON_EDT.markerFqn());

        assertNotEquals(edt, nonEdt);
    }

    @Test
    void tracesPathIsBaseDirPlusTracesDir() {
        Path path = ThreadingHighlighterConfig.getTracesPath("/home/dev/project");

        assertEquals(Path.of("/home/dev/project", ThreadingHighlighterConfig.TRACES_DIR_NAME), path);
    }

    @Test
    void missingProjectDirPropertyFailsFast() {
        String previous = System.getProperty(ThreadingHighlighterConfig.PROJECT_DIR_PROPERTY);
        System.clearProperty(ThreadingHighlighterConfig.PROJECT_DIR_PROPERTY);
        try {
            assertThrows(IllegalStateException.class, ThreadingHighlighterConfig::getTracesPathFromSystemProperty);
        } finally {
            if (previous != null) {
                System.setProperty(ThreadingHighlighterConfig.PROJECT_DIR_PROPERTY, previous);
            }
        }
    }
}
