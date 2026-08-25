package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides whether a stack frame belongs to the analyzed plugin's own code.
 *
 * <p>This is an <b>allow list</b> (whitelist): a class is user code only when it
 * falls under one of the configured base packages. There is deliberately no
 * complementary block list of framework packages. A block list can never be
 * complete — the JVM hosts the IDE core plus dozens of bundled plugins and
 * libraries ({@code git4idea}, {@code org.jetbrains.*}, {@code one.util.*}, …) —
 * so it always leaks noise. Naming the packages we <em>want</em> is both smaller
 * and exact.
 *
 * <p>Matching is package-boundary aware: {@code com.example} includes
 * {@code com.example.Foo} and {@code com.example.sub.Bar} but not
 * {@code com.exampleOther.Baz}. Prefixes are parsed once and matching is a hot-path
 * check (called for every frame of every marker hit), so it stays allocation-free.
 */
final class IncludePackageFilter {

    private static final char PACKAGE_SEPARATOR = '.';

    // Normalized, de-duplicated prefixes without a trailing dot. Immutable after construction.
    private final List<String> includedPackages;

    private IncludePackageFilter(List<String> includedPackages) {
        this.includedPackages = includedPackages;
    }

    /**
     * Parses the raw value of {@link ThreadingHighlighterConfig#INCLUDE_PACKAGES_PROPERTY}.
     * A {@code null} or blank value yields an {@link #isEmpty() empty} filter that matches
     * nothing, which the caller reports as a misconfiguration.
     */
    static IncludePackageFilter fromProperty(String rawValue) {
        if (rawValue == null) {
            return new IncludePackageFilter(Collections.emptyList());
        }
        // LinkedHashSet: drop duplicates but keep the user's order for stable logging
        Set<String> normalized = new LinkedHashSet<>();
        for (String token : rawValue.split(ThreadingHighlighterConfig.INCLUDE_PACKAGES_SEPARATOR)) {
            String prefix = normalize(token);
            if (!prefix.isEmpty()) {
                normalized.add(prefix);
            }
        }
        return new IncludePackageFilter(List.copyOf(new ArrayList<>(normalized)));
    }

    /** True when no packages were configured, so the filter matches nothing. */
    boolean isEmpty() {
        return includedPackages.isEmpty();
    }

    /** The configured prefixes, for diagnostics. */
    List<String> includedPackages() {
        return includedPackages;
    }

    /** True when {@code className} falls under one of the configured packages. */
    boolean includes(String className) {
        for (String prefix : includedPackages) {
            if (matchesPackage(className, prefix)) {
                return true;
            }
        }
        return false;
    }

    // A prefix matches only at a package boundary: the class name must be the prefix
    // itself or continue with a '.', so "com.foo" never matches "com.foobar".
    private static boolean matchesPackage(String className, String prefix) {
        if (!className.startsWith(prefix)) {
            return false;
        }
        return className.length() == prefix.length() || className.charAt(prefix.length()) == PACKAGE_SEPARATOR;
    }

    // Trims whitespace and any leading/trailing dots so "  com.foo. " becomes "com.foo".
    private static String normalize(String token) {
        String trimmed = token.trim();
        int start = 0;
        int end = trimmed.length();
        while (start < end && trimmed.charAt(start) == PACKAGE_SEPARATOR) {
            start++;
        }
        while (end > start && trimmed.charAt(end - 1) == PACKAGE_SEPARATOR) {
            end--;
        }
        return trimmed.substring(start, end);
    }
}
