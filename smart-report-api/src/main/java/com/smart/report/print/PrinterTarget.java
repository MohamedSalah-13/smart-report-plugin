package com.smart.report.print;

import java.util.Objects;

/**
 * الطابعة اللي الإعداد رايح لها: دور البرنامج بيسمّيه، أو طابعة متعرّفة باسمها، أو الطابعة الافتراضية للجهاز.
 *
 * <p>الدور ({@code "normal"}، {@code "thermal"}) بيتحوّل لاسم طابعة وقت الطباعة من {@link PrintSettings}: لما طابعة
 * الإيصالات تتغير مرة، كل الأوراق اللي على الدور ده بتروح للجديدة.</p>
 */
public sealed interface PrinterTarget {

    /** الطابعة الافتراضية للجهاز. */
    static PrinterTarget systemDefault() {
        return SystemDefault.INSTANCE;
    }

    /** دور بيسمّيه البرنامج، زي {@code "thermal"}. */
    static PrinterTarget role(String role) {
        return new Role(role);
    }

    /** طابعة متعرّفة على الجهاز باسمها. */
    static PrinterTarget named(String printer) {
        return new Named(printer);
    }

    /** الطابعة الافتراضية للجهاز. */
    final class SystemDefault implements PrinterTarget {
        static final SystemDefault INSTANCE = new SystemDefault();

        private SystemDefault() {}

        @Override
        public String toString() {
            return "SystemDefault";
        }
    }

    /**
     * دور بيسمّيه البرنامج.
     *
     * @param name حروف وأرقام و {@code . _ -} بس، زي مفاتيح {@link DocumentKind}
     */
    record Role(String name) implements PrinterTarget {
        public Role {
            DocumentKind.checkKey(name, "role");
        }
    }

    /** طابعة باسمها زي ما النظام بيعرضه. */
    record Named(String printer) implements PrinterTarget {
        public Named {
            Objects.requireNonNull(printer, "printer");
            if (printer.isBlank()) {
                throw new IllegalArgumentException("A printer name is required.");
            }
        }
    }
}
