package com.smart.report.print;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A document kind that the program defines: a sales receipt, a purchase invoice, an account statement, a voucher...
 * It has a fixed key, a label shown on the settings screen, the papers it can be printed on, and its default
 * settings.
 *
 * <pre>{@code
 * DocumentKind receipt = DocumentKind.of("sale-receipt", "إيصال البيع",
 *         PrintProfile.of("80mm", OutputMode.PRINT, PrinterTarget.role("thermal"), 1),
 *         "80mm", "58mm", "A5");
 * }</pre>
 */
public final class DocumentKind {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final String key;
    private final String label;
    private final List<String> papers;
    private final PrintProfile defaults;

    private DocumentKind(String key, String label, PrintProfile defaults, List<String> papers) {
        this.key = key;
        this.label = label;
        this.defaults = defaults;
        this.papers = papers;
    }

    /**
     * @param key      English letters, digits and {@code . _ -}, up to 64 characters. The settings are saved under
     *                 it, so do not change it between versions
     * @param label    the label on the settings screen, in the program's language
     * @param defaults the settings used when nothing is saved, or when the saved value cannot be read; its paper
     *                 must be one of {@code papers}
     * @param papers   the allowed paper names, in display order, without duplicates
     * @throws IllegalArgumentException if the key has other characters, the label is blank, there are no papers,
     *                                  or the default paper is not one of them
     */
    public static DocumentKind of(String key, String label, PrintProfile defaults, String... papers) {
        checkKey(key, "document kind key");
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("A label is required for the document kind " + key + ".");
        }
        Objects.requireNonNull(defaults, "defaults");
        if (papers == null || papers.length == 0) {
            throw new IllegalArgumentException("The document kind " + key + " needs at least one paper.");
        }
        List<String> list = List.of(papers).stream().map(String::strip).toList();
        if (list.stream().anyMatch(String::isEmpty) || list.stream().distinct().count() != list.size()) {
            throw new IllegalArgumentException("The papers of " + key + " must be distinct names: " + list);
        }
        DocumentKind kind = new DocumentKind(key, label, defaults, list);
        kind.check(defaults);
        return kind;
    }

    static void checkKey(String key, String what) {
        Objects.requireNonNull(key, what);
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("A " + what + " holds English letters, digits and . _ - only (up to 64): \""
                    + key + "\"");
        }
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** The allowed papers, in order. */
    public List<String> papers() {
        return papers;
    }

    public PrintProfile defaults() {
        return defaults;
    }

    /**
     * Rejects settings that do not fit this kind: a paper that is not one of its papers.
     *
     * @throws IllegalArgumentException naming the paper and the kind
     */
    public void check(PrintProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (!papers.contains(profile.paper())) {
            throw new IllegalArgumentException("The document kind " + key + " does not take the paper "
                    + profile.paper() + "; it takes " + papers + ".");
        }
    }

    @Override
    public String toString() {
        return "DocumentKind[" + key + "]";
    }
}
