package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.agent.common.TraceRecord;

/**
 * Encodes a {@link TraceRecord} into a single JSON line by hand.
 *
 * <p>The agent writes JSON without a library on purpose, so it stays free of extra
 * dependencies (see A1). This is the write half of the trace-line contract; the
 * plugin owns the matching {@code decode} half. Field names here must stay in sync
 * with what the plugin reads.
 */
public final class TraceLineCodec {

    public static String encode(TraceRecord record) {
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
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    // RFC 8259: control characters U+0000-U+001F need a unicode escape
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }
}
