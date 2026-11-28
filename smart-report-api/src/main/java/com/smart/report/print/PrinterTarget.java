package com.smart.report.print;

import java.util.Objects;

/**
 * The printer the settings point to: a role the program names, a printer known by its name, or the machine's
 * default printer.
 *
 * <p>A role ({@code "normal"}, {@code "thermal"}) is resolved to a printer name at print time from
 * {@link PrintSettings}: when the receipt printer changes once, every paper on that role goes to the new one.</p>
 */
public sealed interface PrinterTarget {

    /** The machine's default printer. */
    static PrinterTarget systemDefault() {
        return SystemDefault.INSTANCE;
    }

    /** A role the program names, such as {@code "thermal"}. */
    static PrinterTarget role(String role) {
        return new Role(role);
    }

    /** A printer installed on the machine, by its name. */
    static PrinterTarget named(String printer) {
        return new Named(printer);
    }

    /** The machine's default printer. */
    final class SystemDefault implements PrinterTarget {
        static final SystemDefault INSTANCE = new SystemDefault();

        private SystemDefault() {}

        @Override
        public String toString() {
            return "SystemDefault";
        }
    }

    /**
     * A role the program names.
     *
     * @param name letters, digits and {@code . _ -} only, like the keys of {@link DocumentKind}
     */
    record Role(String name) implements PrinterTarget {
        public Role {
            DocumentKind.checkKey(name, "role");
        }
    }

    /** A printer by its name, as the operating system shows it. */
    record Named(String printer) implements PrinterTarget {
        public Named {
            Objects.requireNonNull(printer, "printer");
            if (printer.isBlank()) {
                throw new IllegalArgumentException("A printer name is required.");
            }
        }
    }
}
