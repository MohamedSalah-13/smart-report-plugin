package com.smart.report.fx;

import com.smart.report.pdf.Paper;
import com.smart.report.pdf.PdfDocument;
import com.smart.report.pdf.PdfTable;
import com.smart.report.pdf.PrintOptions;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** مدى الصفحات والوجهين في نافذة المعاينة. */
class PrintRangeWindowTest {

    private static boolean toolkit;

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

    record Job(String printer, Paper paper, PrintOptions options) {}

    static final class Fake implements PreviewSystem {
        final List<Job> jobs = new CopyOnWriteArrayList<>();

        @Override
        public List<String> printerNames() {
            return List.of("HP LaserJet");
        }

        @Override
        public Optional<String> defaultPrinter() {
            return Optional.of("HP LaserJet");
        }

        @Override
        public void print(byte[] pdf, String printer, Paper paper, int copies) {
            throw new AssertionError("the window prints with its options");
        }

        @Override
        public void print(byte[] pdf, String printer, Paper paper, PrintOptions options) {
            jobs.add(new Job(printer, paper, options));
        }

        @Override
        public File chooseSaveFile(FileChooser chooser, Window owner) {
            return null;
        }
    }

    private static PdfDocument report(Paper paper) {
        PdfTable table = PdfTable.create().column("Day", 1).column("Amount", 1);
        for (int day = 1; day <= 90; day++) {
            table.row("row" + day, "5");
        }
        return PdfDocument.ltr(paper).title("Report").table(table);
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
    private static <T> T node(Stage stage, String id) {
        return (T) stage.getScene().lookup("#" + id);
    }

    private static Stage open(PdfPreview preview) throws Exception {
        Stage stage = onFx(() -> preview.show(null));
        await("the print button", () -> !PrintRangeWindowTest.<Button>node(stage, "print").isDisabled());
        return stage;
    }

    @Test
    void theRangeAndBothSidesGoToThePrinter() throws Exception {
        assumeTrue(toolkit, "no display");
        Fake system = new Fake();
        PdfPreview preview = PdfPreview.of(report(Paper.A4)).rightToLeft(false);
        preview.system = system;
        Stage stage = open(preview);
        onFx(() -> {
            PrintRangeWindowTest.<TextField>node(stage, "pages").setText("2-3");
            PrintRangeWindowTest.<CheckBox>node(stage, "two-sided").setSelected(true);
            PrintRangeWindowTest.<Button>node(stage, "print").fire();
            return null;
        });
        await("the job", () -> system.jobs.size() == 1);
        assertEquals(new Job("HP LaserJet", Paper.A4,
                PrintOptions.copies(1).pages(2, 3).duplex(PrintOptions.Duplex.LONG_EDGE)), system.jobs.get(0));

        // فاضي = كل الصفحات، ومن غير الوجهين = افتراضي الطابعة.
        await("the print button again", () -> !PrintRangeWindowTest.<Button>node(stage, "print").isDisabled());
        onFx(() -> {
            PrintRangeWindowTest.<TextField>node(stage, "pages").setText("");
            PrintRangeWindowTest.<CheckBox>node(stage, "two-sided").setSelected(false);
            PrintRangeWindowTest.<Button>node(stage, "print").fire();
            return null;
        });
        await("the second job", () -> system.jobs.size() == 2);
        assertEquals(PrintOptions.DEFAULT, system.jobs.get(1).options());
    }

    @Test
    void aWrongRangeIsNotPrinted() throws Exception {
        assumeTrue(toolkit, "no display");
        Fake system = new Fake();
        PdfPreview preview = PdfPreview.of(report(Paper.A4)).rightToLeft(true);
        preview.system = system;
        Stage stage = open(preview);
        onFx(() -> {
            PrintRangeWindowTest.<TextField>node(stage, "pages").setText("99");
            PrintRangeWindowTest.<Button>node(stage, "print").fire();
            return null;
        });
        String status = onFx(() -> PrintRangeWindowTest.<Label>node(stage, "status").getText());
        assertTrue(status.startsWith("اكتب رقم صفحة"), status);
        Thread.sleep(200);
        assertTrue(system.jobs.isEmpty());
    }

    /** الإيصال على لفة صفحة واحدة: مفيش مدى ولا وجهين. */
    @Test
    void aRollHasNoRangeOrSides() throws Exception {
        assumeTrue(toolkit, "no display");
        Fake system = new Fake();
        PdfPreview preview = PdfPreview.of(PdfDocument.rtl(Paper.ROLL_80).text("إيصال"));
        preview.system = system;
        Stage stage = open(preview);
        assertNull(onFx(() -> stage.getScene().lookup("#pages")));
        assertNull(onFx(() -> stage.getScene().lookup("#two-sided")));
        onFx(() -> {
            PrintRangeWindowTest.<Button>node(stage, "print").fire();
            return null;
        });
        await("the job", () -> system.jobs.size() == 1);
        assertEquals(PrintOptions.DEFAULT, system.jobs.get(0).options());
    }
}
