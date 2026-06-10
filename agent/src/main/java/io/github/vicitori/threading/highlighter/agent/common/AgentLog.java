package io.github.vicitori.threading.highlighter.agent.common;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single logging entry point for the agent.
 *
 * <p>The agent runs inside a host application (IntelliJ IDE), so it must not write
 * to the host's {@code stdout} unconditionally. Logging goes through
 * {@link java.util.logging} (no extra dependency) with levels, so the host or the
 * user can raise or lower verbosity without recompiling the agent:
 * <ul>
 *   <li>{@link #debug} → {@link Level#FINE}: off by default (e.g. per-flush counts)</li>
 *   <li>{@link #info} → {@link Level#INFO}: one-time lifecycle events</li>
 *   <li>{@link #warn} → {@link Level#WARNING}: recoverable misconfiguration</li>
 *   <li>{@link #error} → {@link Level#SEVERE}: failures, always visible</li>
 * </ul>
 */
public final class AgentLog {

    public static final String LOGGER_NAME = "io.github.vicitori.threading.highlighter";

    private static final Logger LOG = Logger.getLogger(LOGGER_NAME);

    private AgentLog() {
    }

    public static void debug(String message) {
        LOG.log(Level.FINE, message);
    }

    public static void info(String message) {
        LOG.log(Level.INFO, message);
    }

    public static void warn(String message) {
        LOG.log(Level.WARNING, message);
    }

    public static void error(String message) {
        LOG.log(Level.SEVERE, message);
    }

    public static void error(String message, Throwable thrown) {
        LOG.log(Level.SEVERE, message, thrown);
    }
}
