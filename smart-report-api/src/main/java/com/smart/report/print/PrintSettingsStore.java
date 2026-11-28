package com.smart.report.print;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * المكان اللي {@link PrintSettings} بيحفظ فيه: نص بمفتاح. البرنامج اللي عنده إعداداته في قاعدة بيانات أو ملف
 * بيكتب نسخته (تلات دوال)، والاختبار بياخد {@link #inMemory()}.
 */
public interface PrintSettingsStore {

    /** القيمة المحفوظة بالمفتاح، أو فاضي. */
    Optional<String> get(String key);

    void put(String key, String value);

    void remove(String key);

    /**
     * عقدة {@link Preferences}: على Windows في الـ registry تحت المستخدم، وعلى لينكس في ملف في البيت. أي خطأ في
     * الحفظ بيتسجّل ويتجاهل: الطباعة ما توقفش عشان إعداد ما اتحفظش.
     */
    static PrintSettingsStore preferences(Preferences node) {
        Objects.requireNonNull(node, "node");
        return new PrintSettingsStore() {
            @Override
            public Optional<String> get(String key) {
                try {
                    return Optional.ofNullable(node.get(key, null));
                } catch (IllegalStateException removed) {
                    return Optional.empty();
                }
            }

            @Override
            public void put(String key, String value) {
                node.put(key, value);
                flush();
            }

            @Override
            public void remove(String key) {
                node.remove(key);
                flush();
            }

            private void flush() {
                try {
                    node.flush();
                } catch (BackingStoreException e) {
                    org.slf4j.LoggerFactory.getLogger(PrintSettingsStore.class)
                            .warn("The print settings could not be written to {}", node.absolutePath(), e);
                }
            }
        };
    }

    /** في الذاكرة بس، للاختبارات أو لبرنامج بيحفظ بنفسه بعدين ({@link InMemory#snapshot}). */
    static InMemory inMemory() {
        return new InMemory();
    }

    /** {@link #inMemory()}: آمن بين الـ threads. */
    final class InMemory implements PrintSettingsStore {
        private final Map<String, String> values = new ConcurrentHashMap<>();

        private InMemory() {}

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void put(String key, String value) {
            values.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }

        /** نسخة من كل المحفوظ. */
        public Map<String, String> snapshot() {
            return Map.copyOf(values);
        }
    }
}
