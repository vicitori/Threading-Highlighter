package io.github.vicitori.threading.highlighter.agent;

import io.github.vicitori.threading.highlighter.agent.marker.MarkerAdvice;
import io.github.vicitori.threading.highlighter.agent.trace.TraceWriter;
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo;
import io.github.vicitori.threading.highlighter.common.marker.Markers;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class ThreadingHighlighterAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        MarkerAdvice.setWriter(new TraceWriter());
        AgentBuilder agent = configureAgent();
        List<MarkerInfo> markers = getAllMarkers();

        for (MarkerInfo marker : markers) {
            instrumentMarker(agent, marker, inst);
        }
    }

    private static List<MarkerInfo> getAllMarkers() {
        List<MarkerInfo> markers = new ArrayList<>();
        try {
            Field[] fields = Markers.class.getDeclaredFields();
            for (Field field : fields) {
                if (field.getType() == MarkerInfo.class) {
                    field.setAccessible(true);
                    MarkerInfo marker = (MarkerInfo) field.get(null);
                    markers.add(marker);
                }
            }
        } catch (Exception e) {
            System.err.println("[ThreadingHighlighterAgent] ERROR: Failed to discover markers");
            System.err.println("[ThreadingHighlighterAgent] Exception: " + e.getClass().getName() + ": " + e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                System.err.println("[ThreadingHighlighterAgent]   at " + element);
            }
        }
        return markers;
    }

    private static void instrumentMarker(AgentBuilder agent, MarkerInfo marker, Instrumentation inst) {
        agent.type(ElementMatchers.named(marker.classFqn))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(MarkerAdvice.class).on(ElementMatchers.named(marker.methodName))))
                .installOn(inst);
    }

    private static AgentBuilder configureAgent() {
        return new AgentBuilder.Default().with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .ignore(ElementMatchers.nameStartsWith("net.bytebuddy.")
                        .or(ElementMatchers.nameStartsWith("java."))
                        .or(ElementMatchers.nameStartsWith("javax."))
                        .or(ElementMatchers.nameStartsWith("sun."))
                        .or(ElementMatchers.nameStartsWith("com.sun."))
                        .or(ElementMatchers.nameStartsWith("jdk."))
                        .or(ElementMatchers.isSynthetic()));
    }
}
