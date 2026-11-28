package com.smart.report.print;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * إعدادات الطباعة لكل نوع مستند في البرنامج، والطابعة لكل دور، محفوظة في {@link PrintSettingsStore}.
 *
 * <pre>{@code
 * PrintSettings settings = PrintSettings.of(
 *         PrintSettingsStore.preferences(Preferences.userRoot().node("my-shop/print")),
 *         List.of("normal", "thermal"),
 *         DocumentKind.of("sale-receipt", "إيصال البيع",
 *                 PrintProfile.of("80mm", OutputMode.PRINT, PrinterTarget.role("thermal"), 1), "80mm", "58mm"),
 *         DocumentKind.of("statement", "كشف حساب",
 *                 PrintProfile.of("A4", OutputMode.PREVIEW, PrinterTarget.role("normal"), 1), "A4", "A5"));
 *
 * PrintProfile profile = settings.profile("sale-receipt");      // وقت الطباعة، مش مرة لعمر الشاشة
 * Optional<String> printer = settings.printerName(profile);      // الدور بقى اسم طابعة؛ فاضي = الافتراضية
 * }</pre>
 *
 * <p><b>بيتقرا كل مرة:</b> {@link #profile} بيقرا المحفوظ في كل نداء، فالتغيير في شاشة الإعدادات بيوصل لشاشة
 * مفتوحة من قبله. المحفوظ اللي ما يتقريش (نسخة تانية من البرنامج، أو ورقة شالها البرنامج من النوع) ما بيوقفش
 * الطباعة أبدًا: الجزء ده بياخد الافتراضي ({@link PrintProfile#decode}).</p>
 *
 * <p>المفاتيح في المخزن: {@code profile.<نوع>} و {@code role.<دور>}. الصنف آمن بين الـ threads طالما المخزن آمن.</p>
 */
public final class PrintSettings {

    private static final String PROFILE = "profile.";
    private static final String ROLE = "role.";

    private final PrintSettingsStore store;
    private final List<String> roles;
    private final Map<String, DocumentKind> kinds;

    private PrintSettings(PrintSettingsStore store, List<String> roles, Map<String, DocumentKind> kinds) {
        this.store = store;
        this.roles = roles;
        this.kinds = kinds;
    }

    /**
     * @param roles أدوار الطابعات اللي البرنامج بيعرّفها ({@code "normal"}، {@code "thermal"})؛ ممكن تبقى فاضية
     * @param kinds أنواع المستندات، بالترتيب اللي يتعرض في شاشة الإعدادات
     * @throws IllegalArgumentException لو مفتاح نوع أو دور متكرر، أو إعداد افتراضي على دور مش من {@code roles}
     */
    public static PrintSettings of(PrintSettingsStore store, List<String> roles, DocumentKind... kinds) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(roles, "roles");
        for (String role : roles) {
            DocumentKind.checkKey(role, "role");
        }
        if (roles.stream().distinct().count() != roles.size()) {
            throw new IllegalArgumentException("Every role must be named once: " + roles);
        }
        Map<String, DocumentKind> byKey = new LinkedHashMap<>();
        for (DocumentKind kind : Objects.requireNonNull(kinds, "kinds")) {
            Objects.requireNonNull(kind, "kind");
            if (byKey.putIfAbsent(kind.key(), kind) != null) {
                throw new IllegalArgumentException("The document kind " + kind.key() + " is declared twice.");
            }
            checkRole(kind.defaults(), List.copyOf(roles), kind);
        }
        return new PrintSettings(store, List.copyOf(roles), java.util.Collections.unmodifiableMap(byKey));
    }

    private static void checkRole(PrintProfile profile, List<String> roles, DocumentKind kind) {
        if (profile.printer() instanceof PrinterTarget.Role role && !roles.contains(role.name())) {
            throw new IllegalArgumentException("The profile of " + kind.key() + " prints on the role " + role.name()
                    + ", which is not one of the roles " + roles + ".");
        }
    }

    /** الأنواع بالترتيب. */
    public List<DocumentKind> kinds() {
        return List.copyOf(kinds.values());
    }

    public List<String> roles() {
        return roles;
    }

    /** @throws IllegalArgumentException لو البرنامج ما عرّفش النوع ده */
    public DocumentKind kind(String key) {
        DocumentKind kind = kinds.get(key);
        if (kind == null) {
            throw new IllegalArgumentException("Unknown document kind: " + key + "; the kinds are " + kinds.keySet());
        }
        return kind;
    }

    /**
     * الإعداد الحالي للنوع، مقري من المخزن دلوقتي: المحفوظ، أو الافتراضي. دور ما بقاش البرنامج بيعرّفه بيرجع
     * لطابعة الافتراضي.
     */
    public PrintProfile profile(String kindKey) {
        DocumentKind kind = kind(kindKey);
        PrintProfile read = PrintProfile.decode(store.get(PROFILE + kindKey).orElse(null), kind);
        if (read.printer() instanceof PrinterTarget.Role role && !roles.contains(role.name())) {
            return read.withPrinter(kind.defaults().printer());
        }
        return read;
    }

    /**
     * يحفظ إعداد النوع.
     *
     * @throws IllegalArgumentException لو الورقة مش من أوراقه، أو الدور مش من الأدوار
     */
    public void save(String kindKey, PrintProfile profile) {
        DocumentKind kind = kind(kindKey);
        kind.check(profile);
        checkRole(profile, roles, kind);
        store.put(PROFILE + kindKey, profile.encode());
    }

    /** يمسح المحفوظ للنوع، فيرجع لإعداده الافتراضي. */
    public void reset(String kindKey) {
        kind(kindKey);
        store.remove(PROFILE + kindKey);
    }

    /** الطابعة المتحددة للدور، أو فاضي لو ما اتحددتش (يعني الافتراضية). */
    public Optional<String> rolePrinter(String role) {
        checkKnownRole(role);
        return store.get(ROLE + role).filter(name -> !name.isBlank());
    }

    /** يحدد طابعة الدور باسمها؛ {@code null} أو فاضي يرجّعه للطابعة الافتراضية. */
    public void rolePrinter(String role, String printer) {
        checkKnownRole(role);
        if (printer == null || printer.isBlank()) {
            store.remove(ROLE + role);
        } else {
            store.put(ROLE + role, printer);
        }
    }

    private void checkKnownRole(String role) {
        if (!roles.contains(role)) {
            throw new IllegalArgumentException("Unknown role: " + role + "; the roles are " + roles);
        }
    }

    /**
     * اسم الطابعة اللي الإعداد رايح لها وقت الطباعة: الاسم نفسه، أو طابعة الدور دلوقتي؛ فاضي = الطابعة الافتراضية
     * للجهاز (إعداد على الافتراضية، أو دور ما اتحددتلوش طابعة).
     */
    public Optional<String> printerName(PrintProfile profile) {
        return switch (Objects.requireNonNull(profile, "profile").printer()) {
            case PrinterTarget.SystemDefault ignored -> Optional.empty();
            case PrinterTarget.Named named -> Optional.of(named.printer());
            case PrinterTarget.Role role -> roles.contains(role.name()) ? rolePrinter(role.name()) : Optional.empty();
        };
    }
}
