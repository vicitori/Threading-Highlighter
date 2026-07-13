package io.github.vicitori.threading.highlighter.agent.common;

import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards against drift between the two hand-maintained marker registries: the agent's
 * {@link Markers} (Java) and the plugin's {@code common} {@link io.github.vicitori.threading.highlighter.common.marker.Markers}
 * (Kotlin). They must be kept in sync by hand (the agent stays isolated from Kotlin at
 * runtime, so they cannot share one class), and a mismatch would silently break the
 * write/read contract. If this test fails, update both registries together.
 */
class MarkersParityTest {

    @Test
    void agentAndCommonMarkerRegistriesMatch() {
        List<io.github.vicitori.threading.highlighter.agent.common.MarkerInfo> agentMarkers = Markers.getAll();
        List<MarkerInfo> commonMarkers = io.github.vicitori.threading.highlighter.common.marker.Markers.getAll();

        assertEquals(commonMarkers.size(), agentMarkers.size(), "marker count differs between agent and common");

        for (int i = 0; i < agentMarkers.size(); i++) {
            var agent = agentMarkers.get(i);
            var common = commonMarkers.get(i);
            assertEquals(common.classFqn, agent.classFqn(), "classFqn differs at index " + i);
            assertEquals(common.methodName, agent.methodName(), "methodName differs at index " + i);
            assertEquals(common.displayName, agent.displayName(), "displayName differs at index " + i);
            assertEquals(common.description, agent.description(), "description differs at index " + i);
        }
    }
}
