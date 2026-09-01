package io.github.vicitori.threading.highlighter.plugin.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Edge cases of [PackagePathMatcher], the rule that disambiguates same-named source
 * files in different packages. Pure logic, no IntelliJ platform runtime.
 *
 * The annotator and the navigator both rely on this rule, so a regression here would
 * silently put gutter icons on the wrong file; these tests pin the anchoring behavior.
 */
class PackagePathMatcherTest {
    // --- packageOf ----------------------------------------------------------

    @Test
    fun packageOfTakesEnclosingPackage() {
        assertEquals("com.example.app", PackagePathMatcher.packageOf("com.example.app.Service"))
    }

    @Test
    fun packageOfDropsNestedAndLambdaClassSuffix() {
        // nested/lambda classes use '$'; the package is what precedes the first '$'
        assertEquals("com.example.app", PackagePathMatcher.packageOf("com.example.app.Service\$Inner"))
        assertEquals("com.example.app", PackagePathMatcher.packageOf("com.example.app.Service\$1"))
    }

    @Test
    fun packageOfIsEmptyForDefaultPackage() {
        assertEquals("", PackagePathMatcher.packageOf("Service"))
    }

    // --- matches with a file name (strict anchor) --------------------------

    @Test
    fun matchesWhenPackageAndFileLineUpAtPathEnd() {
        assertTrue(PackagePathMatcher.matches("/proj/src/main/java/com/example/app/Foo.java", "com.example.app", "Foo.java"))
    }

    @Test
    fun rejectsSameFileNameInAnotherPackage() {
        // the collision this rule exists for: same simple file name, different package
        assertFalse(PackagePathMatcher.matches("/proj/src/main/java/com/other/pkg/Foo.java", "com.example.app", "Foo.java"))
    }

    @Test
    fun rejectsWhenFileNameDiffers() {
        assertFalse(PackagePathMatcher.matches("/proj/src/main/java/com/example/app/Bar.java", "com.example.app", "Foo.java"))
    }

    @Test
    fun matchesEmptyPackageByFileNameOnly() {
        assertTrue(PackagePathMatcher.matches("/proj/src/Foo.java", "", "Foo.java"))
        assertFalse(PackagePathMatcher.matches("/proj/src/pkg/Foo.java", "", "Bar.java"))
    }

    @Test
    fun normalizesWindowsPathSeparators() {
        assertTrue(
            PackagePathMatcher.matches("C:\\proj\\src\\com\\example\\app\\Foo.java", "com.example.app", "Foo.java"),
        )
    }

    // --- matches without a file name (loose contains) ----------------------

    @Test
    fun withoutFileNameRequiresPackageSegmentPresent() {
        assertTrue(PackagePathMatcher.matches("/proj/src/com/example/app/Foo.java", "com.example.app", null))
        assertFalse(PackagePathMatcher.matches("/proj/src/com/other/Foo.java", "com.example.app", null))
    }

    @Test
    fun withoutFileNameEmptyPackageAlwaysMatches() {
        // no package and no file name: nothing to disambiguate, keep the record
        assertTrue(PackagePathMatcher.matches("/anything/at/all.java", "", null))
    }
}
