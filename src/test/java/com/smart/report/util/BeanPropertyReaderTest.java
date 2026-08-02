package com.smart.report.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanPropertyReaderTest {

    public interface Named {
        String getName();
    }

    /** صنف غير عام بيحقّق واجهة عامة: الحالة اللي كانت بتفشل. */
    static class HiddenNamed implements Named {
        @Override
        public String getName() {
            return "hidden";
        }
    }

    public static class Base {
        private final int id;
        private final boolean active;

        Base(int id, boolean active) {
            this.id = id;
            this.active = active;
        }

        public boolean isActive() {
            return active;
        }
    }

    /** الحقل id متعرّف على الأب ومفيش له getter. */
    public static class Child extends Base {
        Child(int id) {
            super(id, true);
        }
    }

    public record Point(int x, String label) {}

    public static class Exploding {
        public String getBoom() {
            throw new IllegalArgumentException("kaboom");
        }
    }

    @Test
    void readsStandardGetters() {
        assertEquals(7, BeanPropertyReader.read(new Base(7, true), "id"));
        assertEquals(true, BeanPropertyReader.read(new Base(7, true), "active"));
    }

    @Test
    void readsRecordComponents() {
        assertEquals(3, BeanPropertyReader.read(new Point(3, "a"), "x"));
        assertEquals("a", BeanPropertyReader.read(new Point(3, "a"), "label"));
    }

    @Test
    void readsPublicGetterDeclaredOnNonPublicClass() {
        assertEquals("hidden", BeanPropertyReader.read(new HiddenNamed(), "name"));
    }

    @Test
    void readsMapEntriesWhoseImplementationIsNotPublic() {
        var entry = java.util.Map.of("dept", 12).entrySet().iterator().next();
        assertEquals("dept", BeanPropertyReader.read(entry, "key"));
        assertEquals(12, BeanPropertyReader.read(entry, "value"));
    }

    @Test
    void readsFieldsInheritedFromASuperclass() {
        assertEquals(7, BeanPropertyReader.read(new Child(7), "id"));
    }

    @Test
    void reportsAGenuinelyMissingPropertyClearly() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> BeanPropertyReader.read(new Point(1, "a"), "salary"));
        assertTrue(thrown.getMessage().contains("salary"), thrown.getMessage());
        assertNotNullCause(thrown);
    }

    @Test
    void keepsTheOriginalCauseWhenAGetterThrows() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> BeanPropertyReader.read(new Exploding(), "boom"));
        assertNotNullCause(thrown);
        assertEquals("kaboom", thrown.getCause().getMessage());
        assertSame(IllegalArgumentException.class, thrown.getCause().getClass());
    }

    @Test
    void rejectsBlankFieldNamesInsteadOfIndexErrors() {
        assertThrows(IllegalArgumentException.class, () -> BeanPropertyReader.read(new Point(1, "a"), ""));
        assertThrows(IllegalArgumentException.class, () -> BeanPropertyReader.read(new Point(1, "a"), null));
    }

    @Test
    void nullBeanReadsAsNullAndEmptyString() {
        assertNull(BeanPropertyReader.read(null, "anything"));
        assertEquals("", BeanPropertyReader.readAsString(null, "anything"));
    }

    /** الـ accessor بيتخزّن، فالقراءة المتكررة بتدّي نفس النتيجة بدون إعادة البحث. */
    @Test
    void repeatedReadsStayCorrectWhenCached() {
        Point point = new Point(5, "x");
        for (int i = 0; i < 1_000; i++) {
            assertEquals(5, BeanPropertyReader.read(point, "x"));
        }
        assertThrows(IllegalStateException.class, () -> BeanPropertyReader.read(point, "missing"));
        assertThrows(IllegalStateException.class, () -> BeanPropertyReader.read(point, "missing"));
    }

    private static void assertNotNullCause(Throwable thrown) {
        assertTrue(thrown.getCause() != null, "السبب الأصلي لازم يتحفظ مش يتبلع: " + thrown.getMessage());
    }
}
