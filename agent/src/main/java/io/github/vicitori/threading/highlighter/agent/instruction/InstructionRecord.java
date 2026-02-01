package io.github.vicitori.threading.highlighter.agent.instruction;

public final class InstructionRecord {
    private final String className;
    private final String methodName;
    private final String fileName;
    private final int lineNumber;
    private long lastSeenTimestampMillis;

    public InstructionRecord(String className, String methodName, String fileName, int lineNumber, long timestampMillis) {
        this.className = className;
        this.methodName = methodName;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.lastSeenTimestampMillis = timestampMillis;
    }

    public long getLastSeenTimestampMillis() {
        return lastSeenTimestampMillis;
    }

    public void updateTimestamp(long timestampMillis) {
        this.lastSeenTimestampMillis = timestampMillis;
    }

    public String getKey() {
        return className + "#" + methodName + "@" + lineNumber;
    }

    public String toJsonLine() {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"className\":\"").append(escape(className)).append("\",");
        sb.append("\"methodName\":\"").append(escape(methodName)).append("\",");

        if (fileName == null) {
            sb.append("\"fileName\":null,");
        } else {
            sb.append("\"fileName\":\"").append(escape(fileName)).append("\",");
        }

        sb.append("\"lineNumber\":").append(lineNumber).append(",");
        sb.append("\"lastSeenTimestampEpochMillis\":").append(lastSeenTimestampMillis);
        sb.append('}');
        return sb.toString();
    }

    public static InstructionRecord fromStackTraceElement(StackTraceElement element, long timestampMillis) {
        return new InstructionRecord(element.getClassName(), element.getMethodName(), element.getFileName(), element.getLineNumber(), timestampMillis);
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
