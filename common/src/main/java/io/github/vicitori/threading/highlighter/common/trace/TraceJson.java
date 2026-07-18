package io.github.vicitori.threading.highlighter.common.trace;

/**
 * Reads and writes one {@link TraceRecord} as a JSON line. Both sides use it.
 *
 * <p>The agent (writer) calls {@link #encode} and the plugin (reader) calls
 * {@link #decode}, so the format is defined in one place and cannot drift apart.
 *
 * <p>Written by hand, without a JSON library, so the agent needs no extra runtime
 * dependencies. The format is a flat JSON object with these fields:
 * {@code className, methodName, fileName, lineNumber, lastSeenTimestampEpochMillis}.
 * {@code fileName} can be {@code null}.
 */
public final class TraceJson {

    public static String encode(TraceRecord record) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"className\":\"").append(escape(record.getClassName())).append("\",");
        sb.append("\"methodName\":\"").append(escape(record.getMethodName())).append("\",");

        if (record.getFileName() == null) {
            sb.append("\"fileName\":null,");
        } else {
            sb.append("\"fileName\":\"").append(escape(record.getFileName())).append("\",");
        }

        sb.append("\"lineNumber\":").append(record.getLineNumber()).append(",");
        sb.append("\"lastSeenTimestampEpochMillis\":").append(record.getLastSeenTimestampEpochMillis());
        sb.append('}');
        return sb.toString();
    }

    /**
     * Reads one JSON line into a {@link TraceRecord}.
     *
     * @throws IllegalArgumentException if the line is not a valid trace object
     */
    public static TraceRecord decode(String line) {
        Parser parser = new Parser(line);
        parser.expect('{');

        String className = null;
        String methodName = null;
        String fileName = null;
        Integer lineNumber = null;
        Long timestamp = null;

        boolean first = true;
        while (true) {
            parser.skipWhitespace();
            if (parser.peek() == '}') {
                parser.next();
                break;
            }
            if (!first) {
                parser.expect(',');
                parser.skipWhitespace();
            }
            first = false;

            String field = parser.readString();
            parser.skipWhitespace();
            parser.expect(':');
            parser.skipWhitespace();

            switch (field) {
                case "className" -> className = parser.readString();
                case "methodName" -> methodName = parser.readString();
                case "fileName" -> fileName = parser.readStringOrNull();
                case "lineNumber" -> lineNumber = (int) parser.readLong();
                case "lastSeenTimestampEpochMillis" -> timestamp = parser.readLong();
                default -> parser.skipValue(); // ignore unknown fields so older/newer files still load
            }
        }

        if (className == null || methodName == null || lineNumber == null || timestamp == null) {
            throw new IllegalArgumentException("Missing required fields in trace line: " + line);
        }
        return new TraceRecord(className, methodName, fileName, lineNumber, timestamp);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    // RFC 8259: control characters U+0000-U+001F need a unicode escape
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    private TraceJson() {
    }

    /** Minimal recursive-descent reader for the flat trace-line object. */
    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        char peek() {
            if (pos >= s.length()) {
                throw new IllegalArgumentException("Unexpected end of input: " + s);
            }
            return s.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char c) {
            skipWhitespace();
            char actual = next();
            if (actual != c) {
                throw new IllegalArgumentException("Expected '" + c + "' but found '" + actual + "' in: " + s);
            }
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        /** Reads a JSON string; the value must be a string (not null). */
        String readString() {
            skipWhitespace();
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Bad escape '\\" + esc + "' in: " + s);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        /** Reads either a JSON string or the literal {@code null}. */
        String readStringOrNull() {
            skipWhitespace();
            if (peek() == 'n') {
                expectLiteral("null");
                return null;
            }
            return readString();
        }

        long readLong() {
            skipWhitespace();
            int start = pos;
            if (peek() == '-') {
                next();
            }
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                pos++;
            }
            if (pos == start) {
                throw new IllegalArgumentException("Expected number in: " + s);
            }
            return Long.parseLong(s.substring(start, pos));
        }

        /** Skips a string, number or {@code null} value of an unknown field. */
        void skipValue() {
            skipWhitespace();
            char c = peek();
            if (c == '"') {
                readString();
            } else if (c == 'n') {
                expectLiteral("null");
            } else {
                readLong();
            }
        }

        private void expectLiteral(String literal) {
            if (!s.regionMatches(pos, literal, 0, literal.length())) {
                throw new IllegalArgumentException("Expected '" + literal + "' in: " + s);
            }
            pos += literal.length();
        }
    }
}
