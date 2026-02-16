package io.github.vicitori.threading.highlighter.agent;

import io.github.vicitori.threading.highlighter.agent.marker.MarkerAdvice;
import io.github.vicitori.threading.highlighter.agent.trace.TraceWriter;
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo;
import io.github.vicitori.threading.highlighter.common.marker.Markers;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.util.List;

public final class ThreadingHighlighterAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        MarkerAdvice.setWriter(new TraceWriter());
        AgentBuilder agent = configureAgent();
        List<MarkerInfo> markers = Markers.getAll();

        for (MarkerInfo marker : markers) {
            instrumentMarker(agent, marker, inst);
        }
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
