package io.github.vicitori.threading.highlighter.agent;

import io.github.vicitori.threading.highlighter.agent.marker.MarkerAdvice;
import io.github.vicitori.threading.highlighter.agent.trace.TraceFilter;
import io.github.vicitori.threading.highlighter.agent.trace.TraceWriter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;

import static io.github.vicitori.threading.highlighter.agent.marker.Markers.SLOW_OPERATION;

public final class ThreadingHighlighterAgent {

    private static final String TARGET_CLASS_FQN = SLOW_OPERATION.classFqn();

    public static void premain(String agentArgs, Instrumentation inst) {
        System.err.println("[ThreadingHighlighterAgent] installed");
        TraceWriter writer = new TraceWriter();
        TraceFilter filter = new TraceFilter();
        MarkerAdvice.setWriter(writer);
        MarkerAdvice.setFilter(filter);

        System.err.println("[ThreadingHighlighterAgent] trace file: " + writer.getTracePath());

        configureAgent()
                .type(ElementMatchers.named(TARGET_CLASS_FQN))
                .transform(
                        (builder,
                         typeDescription,
                         classLoader,
                         module,
                         protectionDomain
                        ) ->
                                builder.visit(Advice.to(MarkerAdvice.class)
                                        .on(ElementMatchers.named(SLOW_OPERATION.methodName())))
                )
                .installOn(inst);
    }

    private static AgentBuilder configureAgent() {
        return new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
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
