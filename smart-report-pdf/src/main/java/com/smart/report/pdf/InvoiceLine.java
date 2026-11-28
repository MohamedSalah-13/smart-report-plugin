package com.smart.report.pdf;

import java.util.Objects;

/**
 * A line in an invoice or receipt ({@link Invoice}). All values are ready-made text, formatted in the program's
 * language.
 *
 * <pre>{@code
 * InvoiceLine.of("مسمار 10 مم", "12", "2.50", "30.00").withCode("NL-10").withUnit("علبة")
 * }</pre>
 */
public final class InvoiceLine {

    private final String name;
    private final String quantity;
    private final String price;
    private final String total;
    private final String code;
    private final String unit;

    private InvoiceLine(String name, String quantity, String price, String total, String code, String unit) {
        this.name = Objects.requireNonNull(name, "name");
        this.quantity = quantity == null ? "" : quantity;
        this.price = price == null ? "" : price;
        this.total = total == null ? "" : total;
        this.code = code == null ? "" : code;
        this.unit = unit == null ? "" : unit;
    }

    /** The item, the quantity, the unit price, and the line total. */
    public static InvoiceLine of(String name, String quantity, String price, String total) {
        return new InvoiceLine(name, quantity, price, total, null, null);
    }

    /** The item code: shown in its own column in the detailed layout. */
    public InvoiceLine withCode(String value) {
        return new InvoiceLine(name, quantity, price, total, value, unit);
    }

    /** The unit (piece, carton, kilo). */
    public InvoiceLine withUnit(String value) {
        return new InvoiceLine(name, quantity, price, total, code, value);
    }

    public String name() {
        return name;
    }

    public String quantity() {
        return quantity;
    }

    public String price() {
        return price;
    }

    public String total() {
        return total;
    }

    public String code() {
        return code;
    }

    public String unit() {
        return unit;
    }
}
