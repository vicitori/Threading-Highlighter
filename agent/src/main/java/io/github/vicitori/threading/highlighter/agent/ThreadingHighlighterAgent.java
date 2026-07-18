package io.github.vicitori.threading.highlighter.agent;

import io.github.vicitori.threading.highlighter.agent.marker.MarkerAdvice;
import io.github.vicitori.threading.highlighter.agent.trace.TraceWriter;
import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo;
import io.github.vicitori.threading.highlighter.common.marker.Markers;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import io.github.vicitori.threading.highlighter.agent.common.AgentLog;

import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent entry point. It installs Byte Buddy advice on the known threading assertion
 * methods (see {@link Markers}).
 *
 * <p>Both entry points share the same installation: {@code premain} runs when the
 * agent is passed via {@code -javaagent} at startup, and {@code agentmain} runs when
 * the agent is attached to an already running JVM through the Attach API. Retransform
 * is enabled, so markers that are already loaded get instrumented in both cases.
 */
public final class ThreadingHighlighterAgent {

    // premain and agentmain can both fire (e.g. -javaagent at startup, then an attach):
    // install only once so we never create a second TraceWriter, scheduler and shutdown hook
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    public static void premain(String agentArgs, Instrumentation inst) {
        install(inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        install(inst);
    }

    private static void install(Instrumentation inst) {
        if (!INSTALLED.compareAndSet(false, true)) {
            AgentLog.info("Agent already installed, skipping duplicate initialization");
            return;
        }

        MarkerAdvice.setWriter(new TraceWriter());
        AgentBuilder agent = configureAgent();
        List<MarkerInfo> markers = Markers.getAll();

        for (MarkerInfo marker : markers) {
            instrumentMarker(agent, marker, inst);
        }
    }

    private static void instrumentMarker(AgentBuilder agent, MarkerInfo marker, Instrumentation inst) {
        agent.type(ElementMatchers.named(marker.getClassFqn()))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(MarkerAdvice.class).on(ElementMatchers.named(marker.getMethodName()))))
                .installOn(inst);
    }

    private static AgentBuilder configureAgent() {
        return new AgentBuilder.Default().with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                // skip the JDK and the agent's own classes, or the advice could recurse
                .ignore(ElementMatchers.nameStartsWith("net.bytebuddy.")
                        .or(ElementMatchers.nameStartsWith("io.github.vicitori.shaded."))
                        .or(ElementMatchers.nameStartsWith("java."))
                        .or(ElementMatchers.nameStartsWith("javax."))
                        .or(ElementMatchers.nameStartsWith("sun."))
                        .or(ElementMatchers.nameStartsWith("com.sun."))
                        .or(ElementMatchers.nameStartsWith("jdk."))
                        .or(ElementMatchers.isSynthetic()));
    }
}
