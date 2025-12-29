package io.github.vicitori.threading.highlighter.agent.marker;

public record MarkerInfo(
        String classFqn,
        String methodName
) {
    public String markerFqn() {
        return classFqn + "#" + methodName;
    }
}
