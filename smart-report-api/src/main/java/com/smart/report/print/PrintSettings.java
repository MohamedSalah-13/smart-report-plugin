package com.smart.report.print;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The print settings for each document kind in the program, and the printer for each role, saved in
 * {@link PrintSettingsStore}.
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
 * PrintProfile profile = settings.profile("sale-receipt");      // at print time, not once per screen lifetime
 * Optional<String> printer = settings.printerName(profile);      // the role is now a printer name; empty = default
 * }</pre>
 *
 * <p><b>Read every time:</b> {@link #profile} reads the saved value on every call, so a change on the settings
 * screen reaches a screen that was opened before it. A saved value that cannot be read (from another version of the
 * program, or a paper the program removed from the kind) never stops printing: that part takes the default
 * ({@link PrintProfile#decode}).</p>
 *
 * <p>The keys in the store: {@code profile.<kind>} and {@code role.<role>}. The class is thread-safe as long as the
 * store is.</p>
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
     * @param roles the printer roles the program defines ({@code "normal"}, {@code "thermal"}); may be empty
     * @param kinds the document kinds, in the order shown on the settings screen
     * @throws IllegalArgumentException if a kind key or a role is repeated, or a default profile uses a role that is
     *                                  not in {@code roles}
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

    /** The kinds, in order. */
    public List<DocumentKind> kinds() {
        return List.copyOf(kinds.values());
    }

    public List<String> roles() {
        return roles;
    }

    /** @throws IllegalArgumentException if the program did not define this kind */
    public DocumentKind kind(String key) {
        DocumentKind kind = kinds.get(key);
        if (kind == null) {
            throw new IllegalArgumentException("Unknown document kind: " + key + "; the kinds are " + kinds.keySet());
        }
        return kind;
    }

    /**
     * The current profile of the kind, read from the store now: the saved one, or the default. A role the program
     * no longer defines falls back to the default profile's printer.
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
     * Saves the kind's profile.
     *
     * @throws IllegalArgumentException if the paper is not one of its papers, or the role is not one of the roles
     */
    public void save(String kindKey, PrintProfile profile) {
        DocumentKind kind = kind(kindKey);
        kind.check(profile);
        checkRole(profile, roles, kind);
        store.put(PROFILE + kindKey, profile.encode());
    }

    /** Removes the kind's saved profile, so it goes back to its default. */
    public void reset(String kindKey) {
        kind(kindKey);
        store.remove(PROFILE + kindKey);
    }

    /** The printer set for the role, or empty if none is set (meaning the default printer). */
    public Optional<String> rolePrinter(String role) {
        checkKnownRole(role);
        return store.get(ROLE + role).filter(name -> !name.isBlank());
    }

    /** Sets the role's printer by name; {@code null} or blank sets it back to the default printer. */
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
     * The name of the printer the profile goes to at print time: the name itself, or the role's current printer;
     * empty = the machine's default printer (a profile on the default printer, or a role with no printer set).
     */
    public Optional<String> printerName(PrintProfile profile) {
        return switch (Objects.requireNonNull(profile, "profile").printer()) {
            case PrinterTarget.SystemDefault ignored -> Optional.empty();
            case PrinterTarget.Named named -> Optional.of(named.printer());
            case PrinterTarget.Role role -> roles.contains(role.name()) ? rolePrinter(role.name()) : Optional.empty();
        };
    }
}
