package io.github.vicitori.threading.highlighter.agent.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the allow-list semantics: only declared packages match, matching is
 * package-boundary aware, and messy input (blank, dotted, duplicated) is normalized.
 * These are the rules that keep foreign frames (bundled plugins, libraries) out of
 * traces, so a regression here silently reintroduces the noise the whitelist removes.
 */
class IncludePackageFilterTest {

    @Test
    void nullValueMatchesNothingAndIsEmpty() {
        IncludePackageFilter filter = IncludePackageFilter.fromProperty(null);

        assertTrue(filter.isEmpty());
        assertFalse(filter.includes("com.example.Foo"));
    }

    @Test
    void blankValueMatchesNothingAndIsEmpty() {
        IncludePackageFilter filter = IncludePackageFilter.fromProperty("   ,  , ");

        assertTrue(filter.isEmpty());
        assertFalse(filter.includes("com.example.Foo"));
    }

    @Test
    void includesClassesUnderDeclaredPackage() {
        IncludePackageFilter filter = IncludePackageFilter.fromProperty("com.example.myplugin");

        assertTrue(filter.includes("com.example.myplugin.Foo"));
        assertTrue(filter.includes("com.example.myplugin.sub.Bar"));
    }

    @Test
    void matchesExactPackageNameItself() {
        // A frame whose class sits directly in the declared package (edge of the boundary)
        IncludePackageFilter filter = IncludePackageFilter.fromProperty("com.example");

        assertTrue(filter.includes("com.example.Foo"));
    }

    @Test
    void respectsPackageBoundaryToAvoidPrefixCollisions() {
        IncludePackageFilter filter = IncludePackageFilter.fromProperty("com.foo");

        // "com.foobar" shares the text prefix but is a different package: must not match
        assertFalse(filter.includes("com.foobar.Baz"));
    }

    @Test
    void excludesClassesOutsideDeclaredPackages() {
        IncludePackageFilter filter = IncludePackageFilter.fromProperty("com.example.myplugin");

        assertFalse(filter.includes("git4idea.repo.GitRepositoryImpl"));
        assertFalse(filter.includes("org.jetbrains.kotlin.Foo"));
        assertFalse(filter.includes("kotlinx.coroutines.DispatchedTask"));
    }

    @Test
    void supportsMultipleCommaSeparatedPackages() {
        IncludePackageFilter filter = IncludePackageFilter.fromProperty("com.example.one, org.sample.two");

        assertTrue(filter.includes("com.example.one.Foo"));
        assertTrue(filter.includes("org.sample.two.Bar"));
        assertFalse(filter.includes("net.other.Baz"));
    }

    @Test
    void normalizesWhitespaceAndSurroundingDots() {
        IncludePackageFilter filter = IncludePackageFilter.fromProperty("  .com.example.  ");

        assertEquals(List.of("com.example"), filter.includedPackages());
        assertTrue(filter.includes("com.example.Foo"));
    }

    @Test
    void deduplicatesRepeatedPackagesKeepingOrder() {
        IncludePackageFilter filter = IncludePackageFilter.fromProperty("com.a, com.b, com.a");

        assertEquals(List.of("com.a", "com.b"), filter.includedPackages());
    }
}
