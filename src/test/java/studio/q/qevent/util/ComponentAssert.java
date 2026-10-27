package studio.q.qevent.util;

final class ComponentAssert {
    private ComponentAssert() {
    }

    static void assertPlainText(String expected, String rawTemplate) {
        String actual = TextFormatter.plainText(rawTemplate);
        if (!expected.equals(actual)) {
            throw new AssertionError("expected plain text <" + expected + "> but was <" + actual + ">");
        }
    }
}