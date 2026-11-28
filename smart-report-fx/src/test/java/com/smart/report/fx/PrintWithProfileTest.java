package com.smart.report.fx;

import com.smart.report.ReportException;
import com.smart.report.pdf.Paper;
import com.smart.report.pdf.PdfDocument;
import com.smart.report.pdf.PdfPages;
import com.smart.report.print.DocumentKind;
import com.smart.report.print.OutputMode;
import com.smart.report.print.PrintProfile;
import com.smart.report.print.PrintSettings;
import com.smart.report.print.PrintSettingsStore;
import com.smart.report.print.PrinterTarget;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** {@link PdfPreview#print(PrintSettings, String, Window)}: الأربع طرق من خلال {@link PreviewSystem} مزيف. */
class PrintWithProfileTest {

    private static boolean toolkit;

    private static final PrintProfile RECEIPT =
            PrintProfile.of("80mm", OutputMode.PRINT, PrinterTarget.role("thermal"), 2);
    private static final PrintProfile STATEMENT =
            PrintProfile.of("A5", OutputMode.PREVIEW, PrinterTarget.role("normal"), 3);

    @BeforeAll
    static void startJavaFx() {
        try {
            Platform.startup(() -> { });
            toolkit = true;
        } catch (IllegalStateException alreadyStarted) {
            toolkit = true;
        } catch (UnsupportedOperationException | Error noDisplay) {
            toolkit = false;
        }
        if (toolkit) {
            Platform.setImplicitExit(false);
        }
    }

    @AfterEach
    void closeWindows() throws Exception {
        if (toolkit) {
            onFx(() -> {
                for (Window window : List.copyOf(Window.getWindows())) {
                    window.hide();
                }
                return null;
            });
        }
    }

    record Job(byte[] pdf, String printer, Paper paper, int copies) {}

    static class Fake implements PreviewSystem {
        final List<String> names;
        final String preferred;
        final List<Job> jobs = new CopyOnWriteArrayList<>();
        final List<Paper> built = new CopyOnWriteArrayList<>();
        volatile File saveTo;
        volatile OutputMode answer;
        volatile int asked;

        Fake(String preferred, String... names) {
            this.preferred = preferred;
            this.names = List.of(names);
        }

        @Override
        public List<String> printerNames() {
            return names;
        }

        @Override
        public Optional<String> defaultPrinter() {
            return Optional.ofNullable(preferred);
        }

        @Override
        public void print(byte[] pdf, String printer, Paper paper, int copies) {
            jobs.add(new Job(pdf, printer, paper, copies));
        }

        @Override
        public File chooseSaveFile(FileChooser chooser, Window owner) {
            return saveTo;
        }

        @Override
        public OutputMode ask(Window owner, PrintTexts texts) {
            asked++;
            return answer;
        }
    }

    private static PrintSettings settings() {
        return PrintSettings.of(PrintSettingsStore.inMemory(), List.of("normal", "thermal"),
                DocumentKind.of("sale-receipt", "إيصال البيع", RECEIPT, "80mm", "58mm", "A5"),
                DocumentKind.of("statement", "كشف حساب", STATEMENT, "A4", "A5"));
    }

    private PdfPreview preview(Fake system) {
        Function<Paper, PdfDocument> build = paper -> {
            system.built.add(paper);
            return PdfDocument.rtl(paper).title("كشف").text("سطر");
        };
        PdfPreview preview = PdfPreview.of(build).rightToLeft(true).fileName("كشف 12/2026");
        preview.system = system;
        return preview;
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Exception failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get(15, TimeUnit.SECONDS);
    }

    private static void await(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(onFx(condition))) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for: " + what);
    }

    private static PrintResult print(PdfPreview preview, PrintSettings settings, String kind) throws Exception {
        return onFx(() -> preview.print(settings, kind, null)).get(20, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private static <T> T node(Stage stage, String id) {
        return (T) stage.getScene().lookup("#" + id);
    }

    @Test
    void printSendsTheCopiesToTheRolesPrinterOnTheProfilesPaper() throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettings settings = settings();
        settings.rolePrinter("thermal", "XP-80C");
        Fake system = new Fake("HP LaserJet", "HP LaserJet", "XP-80C");
        PrintResult result = print(preview(system), settings, "sale-receipt");
        PrintResult.Printed printed = assertInstanceOf(PrintResult.Printed.class, result);
        assertEquals(new PrintResult.Printed("XP-80C", Paper.ROLL_80, 2), printed);
        assertEquals(1, system.jobs.size());
        assertEquals("XP-80C", system.jobs.get(0).printer());
        assertEquals(2, system.jobs.get(0).copies());
        assertEquals(List.of(Paper.ROLL_80), system.built, "المستند اتبنى على ورقة الإعداد");
        assertTrue(Window.getWindows().isEmpty(), "ما فيش نافذة");
    }

    /** دور من غير طابعة: الافتراضية. */
    @Test
    void aRoleWithoutAPrinterUsesTheDefault() throws Exception {
        assumeTrue(toolkit, "no display");
        Fake system = new Fake("HP LaserJet", "HP LaserJet", "XP-80C");
        assertEquals(new PrintResult.Printed("HP LaserJet", Paper.ROLL_80, 2),
                print(preview(system), settings(), "sale-receipt"));
    }

    /** طابعة الإعداد مش على الجهاز: النافذة بتفتح بسطر بيقول كده، والمستند ما بيروحش لطابعة تانية. */
    @Test
    void aMissingPrinterOpensThePreviewWithANotice() throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettings settings = settings();
        settings.rolePrinter("thermal", "XP-80C");
        Fake system = new Fake("HP LaserJet", "HP LaserJet");
        Stage stage = assertInstanceOf(PrintResult.Shown.class, print(preview(system), settings, "sale-receipt")).window();
        String notice = onFx(() -> PrintWithProfileTest.<Label>node(stage, "notice").getText());
        assertEquals(String.format(PrintTexts.ARABIC.missingPrinter(), "XP-80C"), notice);
        Thread.sleep(300);
        assertTrue(system.jobs.isEmpty(), "ما اتبعتش لطابعة تانية");
        await("the printer list", () -> !PrintWithProfileTest.<ComboBox<String>>node(stage, "printer").getItems().isEmpty());
        assertEquals("HP LaserJet", onFx(() -> PrintWithProfileTest.<ComboBox<String>>node(stage, "printer").getValue()),
                "الاختيار للمستخدم من النافذة");
        assertEquals(2, onFx(() -> PrintWithProfileTest.<Spinner<Integer>>node(stage, "copies").getValue()));
    }

    @Test
    void noPrinterAtAllOpensThePreviewWithANotice() throws Exception {
        assumeTrue(toolkit, "no display");
        Fake system = new Fake(null);
        Stage stage = assertInstanceOf(PrintResult.Shown.class,
                print(preview(system), settings(), "sale-receipt")).window();
        assertEquals(PrintTexts.ARABIC.noPrinter(), onFx(() -> PrintWithProfileTest.<Label>node(stage, "notice").getText()));
    }

    /** المعاينة بتفتح على ورقة الإعداد وطابعته ونسخه، وأوراق النوع بس. */
    @Test
    void previewStartsWithTheProfilesPaperPrinterAndCopies() throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettings settings = settings();
        settings.rolePrinter("normal", "Canon");
        Fake system = new Fake("HP LaserJet", "HP LaserJet", "Canon");
        Stage stage = assertInstanceOf(PrintResult.Shown.class, print(preview(system), settings, "statement")).window();
        assertNull(onFx(() -> stage.getScene().lookup("#notice")), "من غير سطر تنبيه");
        assertEquals(Paper.A5, onFx(() -> PrintWithProfileTest.<ComboBox<Paper>>node(stage, "paper").getValue()));
        assertEquals(List.of(Paper.A4, Paper.A5),
                onFx(() -> List.copyOf(PrintWithProfileTest.<ComboBox<Paper>>node(stage, "paper").getItems())));
        assertEquals(3, onFx(() -> PrintWithProfileTest.<Spinner<Integer>>node(stage, "copies").getValue()));
        await("the printer", () -> "Canon".equals(PrintWithProfileTest.<ComboBox<String>>node(stage, "printer").getValue()));
        assertTrue(system.jobs.isEmpty());
    }

    @Test
    void saveWritesThePdfWithoutAWindow(@TempDir Path folder) throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettings settings = settings();
        settings.save("statement", STATEMENT.withMode(OutputMode.SAVE_PDF));
        Fake system = new Fake("HP LaserJet", "HP LaserJet");
        system.saveTo = folder.resolve("statement.pdf").toFile();
        PrintResult.Saved saved = assertInstanceOf(PrintResult.Saved.class, print(preview(system), settings, "statement"));
        assertEquals(system.saveTo.toPath(), saved.file());
        try (PdfPages pages = PdfPages.open(Files.readAllBytes(saved.file()))) {
            assertEquals(1, pages.count());
            assertEquals(Paper.A5.width(), pages.width(0), 0.5f);
        }
        assertTrue(system.jobs.isEmpty());
        assertTrue(Window.getWindows().isEmpty());

        system.saveTo = null;
        assertInstanceOf(PrintResult.Cancelled.class, print(preview(system), settings, "statement"));
    }

    @Test
    void askFollowsTheAnswer(@TempDir Path folder) throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettings settings = settings();
        settings.save("statement", STATEMENT.withMode(OutputMode.ASK));
        Fake system = new Fake("HP LaserJet", "HP LaserJet");

        system.answer = OutputMode.PRINT;
        assertEquals(new PrintResult.Printed("HP LaserJet", Paper.A5, 3), print(preview(system), settings, "statement"));
        system.answer = OutputMode.SAVE_PDF;
        system.saveTo = folder.resolve("asked.pdf").toFile();
        assertInstanceOf(PrintResult.Saved.class, print(preview(system), settings, "statement"));
        system.answer = OutputMode.PREVIEW;
        assertInstanceOf(PrintResult.Shown.class, print(preview(system), settings, "statement"));
        system.answer = null;
        assertInstanceOf(PrintResult.Cancelled.class, print(preview(system), settings, "statement"));
        assertEquals(4, system.asked);
        assertEquals(1, system.jobs.size());
    }

    /** التغيير لمرة واحدة ما بيتحفظش. */
    @Test
    void aOneTimeOverrideSavesNothing() throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettings settings = settings();
        Fake system = new Fake("HP LaserJet", "HP LaserJet");
        PdfPreview preview = preview(system);
        PrintResult result = onFx(() -> preview.print(settings, "sale-receipt",
                profile -> profile.withMode(OutputMode.PREVIEW).withPaper("A5"), null)).get(20, TimeUnit.SECONDS);
        Stage stage = assertInstanceOf(PrintResult.Shown.class, result).window();
        assertEquals(Paper.A5, onFx(() -> PrintWithProfileTest.<ComboBox<Paper>>node(stage, "paper").getValue()));
        assertEquals(RECEIPT, settings.profile("sale-receipt"));

        java.util.concurrent.ExecutionException refused = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> onFx(() -> preview.print(settings, "sale-receipt", profile -> profile.withPaper("A4"), null)));
        assertInstanceOf(IllegalArgumentException.class, refused.getCause(), "ورقة النوع ما بياخدهاش");
    }

    /** الإعداد بيتقرا وقت الطباعة: تغيير بعد ما الشاشة اتفتحت بيوصل. */
    @Test
    void theProfileIsReadAtPrintTime() throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettings settings = settings();
        Fake system = new Fake("HP LaserJet", "HP LaserJet", "XP-80C");
        PdfPreview screen = preview(system);
        assertEquals(new PrintResult.Printed("HP LaserJet", Paper.ROLL_80, 2), print(screen, settings, "sale-receipt"));
        settings.save("sale-receipt", RECEIPT.withPaper("58mm").withCopies(1)
                .withPrinter(PrinterTarget.named("XP-80C")));
        assertEquals(new PrintResult.Printed("XP-80C", Paper.ROLL_58, 1), print(screen, settings, "sale-receipt"));
    }

    /** المستند الجاهز بيفضل على ورقته. */
    @Test
    void aReadyDocumentKeepsItsPaper() throws Exception {
        assumeTrue(toolkit, "no display");
        Fake system = new Fake("HP LaserJet", "HP LaserJet");
        PdfPreview preview = PdfPreview.of(PdfDocument.rtl(Paper.A4).title("جاهز"));
        preview.system = system;
        PrintSettings settings = settings();
        settings.save("statement", STATEMENT.withMode(OutputMode.PRINT));
        assertEquals(new PrintResult.Printed("HP LaserJet", Paper.A4, 3), print(preview, settings, "statement"));
    }

    @Test
    void aFailedPrintIsReported() throws Exception {
        assumeTrue(toolkit, "no display");
        Fake system = new Fake("HP LaserJet", "HP LaserJet");
        PdfPreview preview = PdfPreview.of(paper -> {
            throw new IllegalStateException("no data");
        });
        preview.system = system;
        PrintResult.Failed failed = assertInstanceOf(PrintResult.Failed.class,
                print(preview, settings(), "sale-receipt"));
        assertInstanceOf(IllegalStateException.class, failed.reason());
        assertTrue(Window.getWindows().isEmpty());
    }

    @Test
    void windowOptions() {
        PdfPreview preview = PdfPreview.of(PdfDocument.rtl(Paper.A4));
        assertThrows(IllegalArgumentException.class, () -> preview.copies(0));
        assertThrows(IllegalArgumentException.class, () -> preview.copies(100));
        assertEquals(5, preview.copies(5).copies);
        assertNull(preview.notice(" ").notice);
        assertNull(preview.printer("").printer);
        assertEquals("X", preview.printer("X").printer);
    }

    @Test
    void reportExceptionIsAFailureToo() throws Exception {
        assumeTrue(toolkit, "no display");
        Fake system = new Fake("HP LaserJet", "HP LaserJet") {
            @Override
            public void print(byte[] pdf, String printer, Paper paper, int copies) {
                throw new IllegalStateException(new ReportException("offline"));
            }
        };
        assertInstanceOf(PrintResult.Failed.class, print(preview(system), settings(), "sale-receipt"));
    }
}
