package io.github.vicitori.threading.highlighter.agent.trace;

public record TraceEvent(
        String markerFqn,
        long timestampMillis,
        Throwable stackTraceHolder
) {
    private static final int MAX_STACK_DEPTH = 30;

    public StackTraceElement[] getStackTrace() {
        var fullTrace = stackTraceHolder.getStackTrace();
        if (fullTrace.length <= MAX_STACK_DEPTH) {
            return fullTrace;
        }

        StackTraceElement[] trimmed = new StackTraceElement[MAX_STACK_DEPTH];
        System.arraycopy(fullTrace, 0, trimmed, 0, MAX_STACK_DEPTH);
        return trimmed;
    }

    public String toJsonLine() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append('{');
        sb.append("\"markerFqn\":\"").append(escape(markerFqn())).append("\",");
        sb.append(",");
        sb.append("\"timestampEpochMillis\":").append(timestampMillis());
        sb.append(",");
        sb.append("\"frames\":[");

        var st = getStackTrace();
        for (int i = 0; i < st.length; i++) {
            StackTraceElement e = st[i];
            if (i > 0) sb.append(',');

            sb.append('{');
            sb.append("\"className\":\"").append(escape(e.getClassName())).append("\",");
            sb.append("\"methodName\":\"").append(escape(e.getMethodName())).append("\",");

            String file = e.getFileName();
            if (file == null) {
                sb.append("\"fileName\":null,");
            } else {
                sb.append("\"fileName\":\"").append(escape(file)).append("\",");
            }

            sb.append("\"lineNumber\":").append(e.getLineNumber());
            sb.append('}');
        }

        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }
}
