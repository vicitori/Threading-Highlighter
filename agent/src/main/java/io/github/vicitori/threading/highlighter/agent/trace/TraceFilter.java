package io.github.vicitori.threading.highlighter.agent.trace;

public final class TraceFilter {
    private static final String USER_PACKAGE_PROPERTY = "threading.highlighter.user.package";

    private final String userPackagePrefix;

    public TraceFilter() {
        String packageName = System.getProperty(USER_PACKAGE_PROPERTY);
        this.userPackagePrefix = packageName.trim();
        System.err.println("[TraceFilter] Filtering enabled for package: " + userPackagePrefix);
    }

    public boolean containsUserCode(StackTraceElement[] stackTrace) {
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.startsWith(userPackagePrefix)) {
                return true;
            }
        }

        return false;
    }
}
