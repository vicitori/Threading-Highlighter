package io.github.vicitori.threading.highlighter.common.marker;

import java.util.Objects;

/**
 * Describes one threading assertion method to instrument.
 *
 * <p>Plain Java (no Kotlin) so the agent and the plugin can share the same class.
 * It has normal getters, so Kotlin code can use property syntax
 * ({@code marker.displayName}).
 */
public final class MarkerInfo {
    private final String classFqn;
    private final String methodName;
    private final String displayName;
    private final String description;

    public MarkerInfo(String classFqn, String methodName, String displayName, String description) {
        this.classFqn = classFqn;
        this.methodName = methodName;
        this.displayName = displayName;
        this.description = description;
    }

    public String getClassFqn() {
        return classFqn;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String markerFqn() {
        return classFqn + "#" + methodName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MarkerInfo other)) return false;
        return Objects.equals(classFqn, other.classFqn)
                && Objects.equals(methodName, other.methodName)
                && Objects.equals(displayName, other.displayName)
                && Objects.equals(description, other.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classFqn, methodName, displayName, description);
    }

    @Override
    public String toString() {
        return "MarkerInfo{" + markerFqn() + "}";
    }
}
