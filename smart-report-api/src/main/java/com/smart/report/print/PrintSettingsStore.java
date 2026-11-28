package com.smart.report.print;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Where {@link PrintSettings} saves: text by key. A program that keeps its settings in a database or a file writes
 * its own implementation (three methods), and a test uses {@link #inMemory()}.
 */
public interface PrintSettingsStore {

    /** The value saved under the key, or empty. */
    Optional<String> get(String key);

    void put(String key, String value);

    void remove(String key);

    /**
     * A {@link Preferences} node: on Windows in the registry under the user, on Linux in a file in the home
     * directory. Any error while saving is logged and ignored: printing does not stop because a setting was not
     * saved.
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

    /** In memory only, for tests or for a program that saves by itself later ({@link InMemory#snapshot}). */
    static InMemory inMemory() {
        return new InMemory();
    }

    /** {@link #inMemory()}: thread-safe. */
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

        /** A copy of everything saved. */
        public Map<String, String> snapshot() {
            return Map.copyOf(values);
        }
    }
}
