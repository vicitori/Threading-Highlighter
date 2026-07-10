package io.github.vicitori.threading.highlighter.plugin.services

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Edge cases of the user-code predicate. Pure logic, no IntelliJ platform runtime.
 */
class UserCodeFilterTest {

    @Test
    fun emptyPackagesTreatsEverythingAsUserCode() {
        // no packages detected: show all, hide nothing
        assertTrue(UserCodeFilter.isUserCode("com.intellij.openapi.Foo", emptyList()))
    }

    @Test
    fun matchesFrameUnderUserPackage() {
        assertTrue(UserCodeFilter.isUserCode("com.example.app.Service", listOf("com.example.app")))
    }

    @Test
    fun rejectsFrameOutsideUserPackages() {
        assertFalse(UserCodeFilter.isUserCode("kotlinx.coroutines.BuildersKt", listOf("com.example.app")))
    }

    @Test
    fun prefixMatchIsPackageAware_notBareStartsWith() {
        // "com.example.app" must not match a sibling package that merely shares the prefix;
        // documents current startsWith behavior and its known limitation
        assertTrue(UserCodeFilter.isUserCode("com.example.apphelper.Foo", listOf("com.example.app")))
    }

    @Test
    fun matchesWhenAnyOfSeveralPackagesMatches() {
        val packages = listOf("com.example.api", "com.example.core")
        assertTrue(UserCodeFilter.isUserCode("com.example.core.Engine", packages))
        assertFalse(UserCodeFilter.isUserCode("com.example.web.Controller", packages))
    }
}
