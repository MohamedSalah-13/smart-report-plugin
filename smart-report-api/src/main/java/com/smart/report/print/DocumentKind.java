package com.smart.report.print;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * نوع مستند بيعرّفه البرنامج: إيصال بيع، فاتورة شراء، كشف حساب، سند... بمفتاح ثابت، واسم يتعرض في شاشة
 * الإعدادات، والأوراق اللي ينفع يتطبع عليها، وإعداده الافتراضي.
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
     * @param key      حروف إنجليزي وأرقام و {@code . _ -}، لحد 64 حرف: بيتحفظ بيه الإعداد، فما يتغيرش بين النسخ
     * @param label    الاسم في شاشة الإعدادات، بلغة البرنامج
     * @param defaults الإعداد لما مفيش محفوظ، أو لما المحفوظ ما يتقريش؛ ورقته لازم تكون من {@code papers}
     * @param papers   أسماء الأوراق المسموحة، بالترتيب اللي يتعرض بيه، من غير تكرار
     * @throws IllegalArgumentException لو المفتاح بحروف تانية، أو الاسم فاضي، أو مفيش أوراق، أو ورقة الافتراضي مش منهم
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

    /** الأوراق المسموحة بالترتيب. */
    public List<String> papers() {
        return papers;
    }

    public PrintProfile defaults() {
        return defaults;
    }

    /**
     * يرفض الإعداد اللي ما ينفعش للنوع ده: ورقة مش من أوراقه.
     *
     * @throws IllegalArgumentException باسم الورقة والنوع
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
