package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.agent.common.TraceRecord;

public final class TraceRecordBuilder {

    public static TraceRecord fromStackTraceElement(StackTraceElement element, long timestampMillis) {
        return new TraceRecord(
                element.getClassName(),
                element.getMethodName(),
                element.getFileName(),
                element.getLineNumber(),
                timestampMillis
        );
    }

    public static String toJsonLine(TraceRecord record) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"className\":\"").append(escape(record.className())).append("\",");
        sb.append("\"methodName\":\"").append(escape(record.methodName())).append("\",");

        if (record.fileName() == null) {
            sb.append("\"fileName\":null,");
        } else {
            sb.append("\"fileName\":\"").append(escape(record.fileName())).append("\",");
        }

        sb.append("\"lineNumber\":").append(record.lineNumber()).append(",");
        sb.append("\"lastSeenTimestampEpochMillis\":").append(record.lastSeenTimestampEpochMillis());
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
