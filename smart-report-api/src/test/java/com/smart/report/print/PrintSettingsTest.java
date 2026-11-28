package com.smart.report.print;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintSettingsTest {

    private static final PrintProfile RECEIPT =
            PrintProfile.of("80mm", OutputMode.PRINT, PrinterTarget.role("thermal"), 1);
    private static final PrintProfile STATEMENT =
            PrintProfile.of("A4", OutputMode.PREVIEW, PrinterTarget.role("normal"), 1);

    private static final DocumentKind RECEIPTS = DocumentKind.of("sale-receipt", "إيصال البيع", RECEIPT,
            "80mm", "58mm", "A5");
    private static final DocumentKind STATEMENTS = DocumentKind.of("statement", "كشف حساب", STATEMENT, "A4", "A5");

    private static PrintSettings settings(PrintSettingsStore store) {
        return PrintSettings.of(store, List.of("normal", "thermal"), RECEIPTS, STATEMENTS);
    }

    @Test
    void defaultsUntilSomethingIsSaved() {
        PrintSettings settings = settings(PrintSettingsStore.inMemory());
        assertEquals(RECEIPT, settings.profile("sale-receipt"));
        assertEquals(STATEMENT, settings.profile("statement"));
        assertEquals(List.of(RECEIPTS, STATEMENTS), settings.kinds());
        assertEquals(Optional.empty(), settings.printerName(RECEIPT), "دور من غير طابعة = الافتراضية");
    }

    /** المحفوظ بيتقرا كل مرة: التغيير بيوصل لأي حد ماسك نفس الإعدادات. */
    @Test
    void savedProfilesAreReadAtEveryCall() {
        PrintSettingsStore.InMemory store = PrintSettingsStore.inMemory();
        PrintSettings screen = settings(store);
        PrintSettings settingsPane = settings(store);
        PrintProfile changed = RECEIPT.withPaper("58mm").withCopies(2);
        settingsPane.save("sale-receipt", changed);
        assertEquals(changed, screen.profile("sale-receipt"));
        assertEquals(Map.of("profile.sale-receipt", "paper=58mm;mode=print;printer=role:thermal;copies=2"),
                store.snapshot());

        settingsPane.reset("sale-receipt");
        assertEquals(RECEIPT, screen.profile("sale-receipt"));
        assertTrue(store.snapshot().isEmpty());
    }

    /** الدور بيتحول لاسم وقت الطباعة: تغيير طابعة الإيصالات مرة بينقل كل الأوراق اللي عليه. */
    @Test
    void aRoleIsResolvedAtPrintTime() {
        PrintSettings settings = settings(PrintSettingsStore.inMemory());
        settings.save("statement", STATEMENT.withPrinter(PrinterTarget.role("thermal")).withPaper("A5"));
        settings.rolePrinter("thermal", "XP-80C");
        assertEquals(Optional.of("XP-80C"), settings.printerName(settings.profile("sale-receipt")));
        assertEquals(Optional.of("XP-80C"), settings.printerName(settings.profile("statement")));

        settings.rolePrinter("thermal", "POS-58");
        assertEquals(Optional.of("POS-58"), settings.printerName(settings.profile("sale-receipt")));
        assertEquals(Optional.of("POS-58"), settings.printerName(settings.profile("statement")));

        settings.rolePrinter("thermal", null);
        assertEquals(Optional.empty(), settings.rolePrinter("thermal"));
        assertEquals(Optional.of("HP LaserJet"),
                settings.printerName(STATEMENT.withPrinter(PrinterTarget.named("HP LaserJet"))));
        assertEquals(Optional.empty(), settings.printerName(STATEMENT.withPrinter(PrinterTarget.systemDefault())));
    }

    @Test
    void codecRoundTrip() {
        assertEquals(STATEMENT.withMode(OutputMode.ASK).withCopies(99),
                PrintProfile.decode(STATEMENT.withMode(OutputMode.ASK).withCopies(99).encode(), STATEMENTS));
        for (PrintProfile profile : List.of(RECEIPT, RECEIPT.withPaper("A5"),
                RECEIPT.withMode(OutputMode.SAVE_PDF).withPrinter(PrinterTarget.systemDefault()),
                RECEIPT.withPrinter(PrinterTarget.named("\\\\server\\HP; 2nd floor = left")),
                RECEIPT.withPrinter(PrinterTarget.named("طابعة الكاشير")))) {
            assertEquals(profile, PrintProfile.decode(profile.encode(), RECEIPTS), profile.encode());
        }
    }

    /** كل جزء ما يتقريش بياخد الافتراضي لوحده، والباقي بيفضل. */
    @Test
    void codecReadsTolerantly() {
        assertEquals(RECEIPT, PrintProfile.decode(null, RECEIPTS));
        assertEquals(RECEIPT, PrintProfile.decode("", RECEIPTS));
        assertEquals(RECEIPT, PrintProfile.decode("garbage", RECEIPTS));
        assertEquals(RECEIPT, PrintProfile.decode(";;;=;", RECEIPTS));
        assertEquals(RECEIPT.withCopies(3), PrintProfile.decode("copies=3", RECEIPTS));
        assertEquals(RECEIPT.withCopies(3), PrintProfile.decode("paper=A3;copies=3", RECEIPTS), "ورقة مش من ورق النوع");
        assertEquals(RECEIPT.withPaper("A5"), PrintProfile.decode("paper=A5;mode=fax;copies=500", RECEIPTS));
        assertEquals(RECEIPT.withPaper("A5"), PrintProfile.decode("paper=A5;copies=two;printer=usb:3", RECEIPTS));
        assertEquals(RECEIPT.withMode(OutputMode.ASK), PrintProfile.decode(" MODE = Ask ; color=red", RECEIPTS));
        assertEquals(RECEIPT, PrintProfile.decode("printer=name:", RECEIPTS), "اسم فاضي");
        assertEquals(RECEIPT, PrintProfile.decode("printer=role:bad role!", RECEIPTS));
        assertEquals(RECEIPT, PrintProfile.decode("printer=%zz", RECEIPTS), "ترميز بايظ");
        assertEquals(RECEIPT.withPaper("58mm"), PrintProfile.decode("paper=58mm;paper=A5", RECEIPTS), "أول قيمة");
    }

    /** نسخة أقدم أو أحدث من البرنامج كتبت حاجة: الطباعة ما بتقفش أبدًا. */
    @Test
    void whatAnotherVersionWroteNeverStopsAPrint() {
        PrintSettingsStore.InMemory store = PrintSettingsStore.inMemory();
        store.put("profile.sale-receipt", "paper=A5;mode=print;printer=role:kitchen;copies=2");
        store.put("profile.statement", "v2{\"paper\":\"A3\"}");
        PrintSettings settings = settings(store);
        assertEquals(RECEIPT.withPaper("A5").withCopies(2), settings.profile("sale-receipt"), "دور اتشال");
        assertEquals(STATEMENT, settings.profile("statement"));
    }

    @Test
    void nonsenseIsRefusedWhenItIsBuilt() {
        assertThrows(IllegalArgumentException.class, () -> PrintProfile.of(" ", OutputMode.PRINT,
                PrinterTarget.systemDefault(), 1));
        assertThrows(IllegalArgumentException.class, () -> RECEIPT.withCopies(0));
        assertThrows(IllegalArgumentException.class, () -> RECEIPT.withCopies(100));
        assertThrows(NullPointerException.class, () -> RECEIPT.withMode(null));
        assertThrows(IllegalArgumentException.class, () -> PrinterTarget.named(" "));
        assertThrows(IllegalArgumentException.class, () -> PrinterTarget.role("بتاعة الكاشير"));
        assertThrows(IllegalArgumentException.class, () -> DocumentKind.of("receipt", "x", RECEIPT, "A4"),
                "ورقة الافتراضي مش من الأوراق");
        assertThrows(IllegalArgumentException.class, () -> DocumentKind.of("receipt", "x", RECEIPT));
        assertThrows(IllegalArgumentException.class, () -> DocumentKind.of("receipt", "x", RECEIPT, "80mm", "80mm"));
        assertThrows(IllegalArgumentException.class, () -> DocumentKind.of("إيصال", "x", RECEIPT, "80mm"));
        assertThrows(IllegalArgumentException.class, () -> DocumentKind.of("receipt", " ", RECEIPT, "80mm"));
        assertThrows(IllegalArgumentException.class,
                () -> PrintSettings.of(PrintSettingsStore.inMemory(), List.of("normal"), RECEIPTS), "دور مش معرّف");
        assertThrows(IllegalArgumentException.class,
                () -> PrintSettings.of(PrintSettingsStore.inMemory(), List.of("normal", "thermal"), RECEIPTS, RECEIPTS));
        assertThrows(IllegalArgumentException.class,
                () -> PrintSettings.of(PrintSettingsStore.inMemory(), List.of("thermal", "thermal"), RECEIPTS));

        PrintSettings settings = settings(PrintSettingsStore.inMemory());
        IllegalArgumentException paper = assertThrows(IllegalArgumentException.class,
                () -> settings.save("statement", STATEMENT.withPaper("80mm")));
        assertTrue(paper.getMessage().contains("80mm"), paper.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> settings.save("statement", STATEMENT.withPrinter(PrinterTarget.role("kitchen"))));
        assertThrows(IllegalArgumentException.class, () -> settings.profile("invoice"));
        assertThrows(IllegalArgumentException.class, () -> settings.rolePrinter("kitchen"));
        assertThrows(IllegalArgumentException.class, () -> settings.rolePrinter("kitchen", "X"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "sale-receipt", "purchase_invoice.v2", "A4"})
    void validKeys(String key) {
        assertEquals(key, DocumentKind.of(key, "x", RECEIPT, "80mm").key());
    }

    @Test
    void systemDefaultIsOneValue() {
        assertSame(PrinterTarget.systemDefault(), PrinterTarget.systemDefault());
        assertEquals(PrinterTarget.role("a"), PrinterTarget.role("a"));
    }

    /** نفس الشغل على {@link Preferences}، في عقدة مؤقتة بتتمسح. */
    @Test
    void preferencesStore() throws Exception {
        Preferences node = Preferences.userRoot().node("com/smart/report/test/" + UUID.randomUUID());
        try {
            PrintSettings settings = settings(PrintSettingsStore.preferences(node));
            settings.save("statement", STATEMENT.withPaper("A5"));
            settings.rolePrinter("normal", "HP LaserJet");
            assertEquals("paper=A5;mode=preview;printer=role:normal;copies=1", node.get("profile.statement", null));
            assertEquals("HP LaserJet", node.get("role.normal", null));
            assertEquals(Optional.of("HP LaserJet"), settings(PrintSettingsStore.preferences(node))
                    .printerName(settings.profile("statement")));
            settings.reset("statement");
            assertEquals(STATEMENT, settings.profile("statement"));
        } finally {
            node.removeNode();
        }
    }
}
