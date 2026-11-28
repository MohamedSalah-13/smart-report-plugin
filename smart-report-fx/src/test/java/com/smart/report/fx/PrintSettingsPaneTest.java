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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** شاشة إعدادات الطباعة على JavaFX حقيقي، وطابعات مزيفة. على جهاز بلا شاشة الاختبارات بتتخطى. */
class PrintSettingsPaneTest {

    private static boolean toolkit;

    private static final PrintProfile RECEIPT =
            PrintProfile.of("80mm", OutputMode.PRINT, PrinterTarget.role("thermal"), 1);
    private static final PrintProfile STATEMENT =
            PrintProfile.of("A4", OutputMode.PREVIEW, PrinterTarget.role("normal"), 1);

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

    record Job(byte[] pdf, String printer, Paper paper, int copies) {}

    static final class FakePrinters implements PreviewSystem {
        final List<String> names;
        final List<Job> jobs = new CopyOnWriteArrayList<>();
        volatile ReportException failure;

        FakePrinters(String... names) {
            this.names = List.of(names);
        }

        @Override
        public List<String> printerNames() {
            return names;
        }

        @Override
        public Optional<String> defaultPrinter() {
            return names.isEmpty() ? Optional.empty() : Optional.of(names.get(0));
        }

        @Override
        public void print(byte[] pdf, String printer, Paper paper, int copies) throws ReportException {
            if (failure != null) {
                throw failure;
            }
            jobs.add(new Job(pdf, printer, paper, copies));
        }

        @Override
        public File chooseSaveFile(FileChooser chooser, Window owner) {
            return null;
        }
    }

    private static PrintSettings settings(PrintSettingsStore store) {
        return PrintSettings.of(store, List.of("normal", "thermal"),
                DocumentKind.of("sale-receipt", "إيصال البيع", RECEIPT, "80mm", "58mm", "A5"),
                DocumentKind.of("statement", "كشف حساب", STATEMENT, "A4", "A5"));
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

    @SuppressWarnings("unchecked")
    private static <T> T node(Parent root, String id) {
        return (T) root.lookup("#" + id);
    }

    private static Parent pane(PrintSettings settings, FakePrinters printers) throws Exception {
        return onFx(() -> {
            PrintSettingsPane pane = PrintSettingsPane.of(settings).rightToLeft(true)
                    .roleLabel("thermal", "طابعة الإيصالات");
            pane.system = printers;
            Parent root = pane.build();
            new Scene(root);
            return root;
        });
    }

    private static void awaitPrinters(Parent root) throws Exception {
        await("the printer list", () -> PrintSettingsPaneTest.<ComboBox<PrinterTarget>>node(root, "printer-statement")
                .getItems().contains(PrinterTarget.named("HP LaserJet")));
    }

    /** الصفوف بتبدأ بالمحفوظ، أو بالافتراضي. */
    @Test
    void rowsStartFromTheSettings() throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettings settings = settings(PrintSettingsStore.inMemory());
        settings.save("statement", STATEMENT.withPaper("A5").withCopies(2));
        Parent root = pane(settings, new FakePrinters("HP LaserJet", "XP-80C"));
        onFx(() -> {
            assertEquals("80mm", PrintSettingsPaneTest.<ComboBox<String>>node(root, "paper-sale-receipt").getValue());
            assertEquals(List.of("80mm", "58mm", "A5"),
                    PrintSettingsPaneTest.<ComboBox<String>>node(root, "paper-sale-receipt").getItems());
            assertEquals(OutputMode.PRINT, PrintSettingsPaneTest.<ComboBox<OutputMode>>node(root, "mode-sale-receipt").getValue());
            assertEquals("A5", PrintSettingsPaneTest.<ComboBox<String>>node(root, "paper-statement").getValue());
            assertEquals(2, PrintSettingsPaneTest.<Spinner<Integer>>node(root, "copies-statement").getValue());
            ComboBox<PrinterTarget> printer = node(root, "printer-sale-receipt");
            assertEquals(PrinterTarget.role("thermal"), printer.getValue());
            assertEquals("دور: طابعة الإيصالات", printer.getConverter().toString(printer.getValue()));
            return null;
        });
        awaitPrinters(root);
        onFx(() -> {
            ComboBox<PrinterTarget> printer = node(root, "printer-sale-receipt");
            assertEquals(List.of(PrinterTarget.systemDefault(), PrinterTarget.role("normal"), PrinterTarget.role("thermal"),
                    PrinterTarget.named("HP LaserJet"), PrinterTarget.named("XP-80C")), printer.getItems());
            assertEquals(PrinterTarget.role("thermal"), printer.getValue(), "قايمة الطابعات ما بتغيرش الاختيار");
            return null;
        });
    }

    /** أي تغيير في الصف بيتحفظ على طول، من غير كود حفظ في البرنامج. */
    @Test
    void everyChangeIsSaved() throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettingsStore.InMemory store = PrintSettingsStore.inMemory();
        PrintSettings settings = settings(store);
        Parent root = pane(settings, new FakePrinters("HP LaserJet", "XP-80C"));
        awaitPrinters(root);
        assertTrue(store.snapshot().isEmpty(), "فتح الشاشة ما بيحفظش حاجة");
        onFx(() -> {
            PrintSettingsPaneTest.<ComboBox<String>>node(root, "paper-sale-receipt").setValue("58mm");
            PrintSettingsPaneTest.<ComboBox<OutputMode>>node(root, "mode-sale-receipt").setValue(OutputMode.ASK);
            PrintSettingsPaneTest.<ComboBox<PrinterTarget>>node(root, "printer-sale-receipt")
                    .setValue(PrinterTarget.named("XP-80C"));
            PrintSettingsPaneTest.<Spinner<Integer>>node(root, "copies-sale-receipt").getValueFactory().setValue(3);
            return null;
        });
        assertEquals(PrintProfile.of("58mm", OutputMode.ASK, PrinterTarget.named("XP-80C"), 3),
                settings.profile("sale-receipt"));
        assertEquals(STATEMENT, settings.profile("statement"));

        onFx(() -> {
            PrintSettingsPaneTest.<ComboBox<String>>node(root, "role-thermal").setValue("XP-80C");
            return null;
        });
        assertEquals(Optional.of("XP-80C"), settings.rolePrinter("thermal"));
        onFx(() -> {
            PrintSettingsPaneTest.<ComboBox<String>>node(root, "role-thermal").setValue("");
            return null;
        });
        assertEquals(Optional.empty(), settings.rolePrinter("thermal"));
    }

    @Test
    void resetPutsTheRowBackToItsDefault() throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettingsStore.InMemory store = PrintSettingsStore.inMemory();
        PrintSettings settings = settings(store);
        settings.save("sale-receipt", RECEIPT.withPaper("A5").withCopies(4));
        settings.save("statement", STATEMENT.withCopies(2));
        Parent root = pane(settings, new FakePrinters("HP LaserJet"));
        onFx(() -> {
            PrintSettingsPaneTest.<Button>node(root, "reset-sale-receipt").fire();
            assertEquals("80mm", PrintSettingsPaneTest.<ComboBox<String>>node(root, "paper-sale-receipt").getValue());
            assertEquals(1, PrintSettingsPaneTest.<Spinner<Integer>>node(root, "copies-sale-receipt").getValue());
            return null;
        });
        assertEquals(RECEIPT, settings.profile("sale-receipt"));
        assertFalse(store.snapshot().containsKey("profile.sale-receipt"), "الافتراضي مش محفوظ: بيتبع البرنامج");
        assertEquals(STATEMENT.withCopies(2), settings.profile("statement"), "الصفوف التانية زي ما هي");
    }

    /** طباعة التجربة: ورقة واحدة على الطابعة والورقة المختارين، والدور بيتحول لاسم. */
    @Test
    void trialPrintSendsOnePageToTheChosenPrinter() throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettings settings = settings(PrintSettingsStore.inMemory());
        settings.rolePrinter("thermal", "XP-80C");
        settings.save("sale-receipt", RECEIPT.withPaper("58mm").withCopies(3));
        FakePrinters printers = new FakePrinters("HP LaserJet", "XP-80C");
        Parent root = pane(settings, printers);
        onFx(() -> {
            PrintSettingsPaneTest.<Button>node(root, "trial-sale-receipt").fire();
            return null;
        });
        await("the trial page", () -> printers.jobs.size() == 1);
        Job job = printers.jobs.get(0);
        assertEquals("XP-80C", job.printer());
        assertEquals(Paper.ROLL_58, job.paper());
        assertEquals(1, job.copies(), "التجربة نسخة واحدة");
        try (PdfPages pages = PdfPages.open(job.pdf())) {
            assertEquals(1, pages.count());
        }
        await("the status", () -> PrintSettingsPaneTest.<Label>node(root, "status").getText().contains("XP-80C"));

        // دور من غير طابعة: الافتراضية.
        onFx(() -> {
            PrintSettingsPaneTest.<Button>node(root, "trial-statement").fire();
            return null;
        });
        await("the second trial page", () -> printers.jobs.size() == 2);
        assertEquals("HP LaserJet", printers.jobs.get(1).printer());
        assertEquals(Paper.A4, printers.jobs.get(1).paper());
    }

    @Test
    void aSampleDocumentIsUsedForTheTrial() throws Exception {
        assumeTrue(toolkit, "no display");
        PrintSettings settings = settings(PrintSettingsStore.inMemory());
        FakePrinters printers = new FakePrinters("HP LaserJet");
        Parent root = onFx(() -> {
            PrintSettingsPane pane = PrintSettingsPane.of(settings).rightToLeft(false)
                    .sample("statement", paper -> PdfDocument.ltr(paper).title("Sample statement").pageBreak()
                            .text("second page"));
            pane.system = printers;
            Parent built = pane.build();
            new Scene(built);
            return built;
        });
        onFx(() -> {
            PrintSettingsPaneTest.<Button>node(root, "trial-statement").fire();
            return null;
        });
        await("the sample", () -> printers.jobs.size() == 1);
        try (PdfPages pages = PdfPages.open(printers.jobs.get(0).pdf())) {
            assertEquals(2, pages.count());
        }
    }

    @Test
    void aFailedTrialSaysSo() throws Exception {
        assumeTrue(toolkit, "no display");
        FakePrinters printers = new FakePrinters("HP LaserJet");
        printers.failure = new ReportException("offline");
        Parent root = pane(settings(PrintSettingsStore.inMemory()), printers);
        onFx(() -> {
            PrintSettingsPaneTest.<Button>node(root, "trial-statement").fire();
            return null;
        });
        await("the failure", () -> PrintSettingsPaneTest.<Label>node(root, "status").getText()
                .equals(PrintSettingsPane.Texts.ARABIC.failed()));
        assertFalse(onFx(() -> PrintSettingsPaneTest.<Button>node(root, "trial-statement").isDisabled()));
    }

    @Test
    void unknownNamesAreRefused() {
        PrintSettings settings = settings(PrintSettingsStore.inMemory());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PrintSettingsPane.of(settings).roleLabel("kitchen", "x"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PrintSettingsPane.of(settings).sample("invoice", PdfDocument::rtl));
    }
}
