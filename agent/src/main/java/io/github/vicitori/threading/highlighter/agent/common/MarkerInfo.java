package io.github.vicitori.threading.highlighter.agent.common;

/**
 * Describes a threading assertion marker method to instrument.
 */
public record MarkerInfo(
        String classFqn,
        String methodName,
        String displayName,
        String description
) {
    public String markerFqn() {
        return classFqn + "#" + methodName;
    }
}
