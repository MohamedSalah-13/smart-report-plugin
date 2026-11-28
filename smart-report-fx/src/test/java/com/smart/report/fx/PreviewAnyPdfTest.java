package com.smart.report.fx;

import com.smart.report.pdf.Paper;
import com.smart.report.pdf.PdfDocument;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** المعاينة لأي PDF، مش بس اللي المكتبة بنته، وأيقونة النافذة الأم. */
class PreviewAnyPdfTest {

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

    record Job(byte[] pdf, String printer, Paper paper, int copies) {}

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
            jobs.add(new Job(pdf, printer, paper, copies));
        }

        @Override
        public File chooseSaveFile(FileChooser chooser, Window owner) {
            return null;
        }
    }

    /** PDF مش من المكتبة: صفحتين 100 × 150 مم مكتوبين بـ PDFBox على طول. */
    private static byte[] foreignPdf() throws Exception {
        float mm = 72f / 25.4f;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(new PDRectangle(100 * mm, 150 * mm)));
            document.addPage(new PDPage(new PDRectangle(100 * mm, 150 * mm)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
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

    @Test
    void theWindowOpensOnReadyPdfBytes() throws Exception {
        assumeTrue(toolkit, "no display");
        byte[] pdf = foreignPdf();
        Fake system = new Fake();
        PdfPreview preview = PdfPreview.ofPdf(pdf).rightToLeft(false);
        preview.system = system;
        Stage stage = onFx(() -> preview.show(null));
        await("the pages", () -> PreviewAnyPdfTest.<Label>node(stage, "page-label").getText().endsWith("of 2"));
        assertNull(onFx(() -> stage.getScene().lookup("#paper")), "ورقة واحدة: من غير اختيار مقاس");
        await("the print button", () -> !PreviewAnyPdfTest.<Button>node(stage, "print").isDisabled());
        onFx(() -> {
            PreviewAnyPdfTest.<Button>node(stage, "print").fire();
            return null;
        });
        await("the job", () -> system.jobs.size() == 1);
        assertArrayEquals(pdf, system.jobs.get(0).pdf(), "البايتات زي ما هي");
        assertEquals(100 * 72f / 25.4f, system.jobs.get(0).paper().width(), 0.5f);
    }

    @Test
    void theWellKnownPapersAreRecognised() throws Exception {
        assertSame(Paper.A4, PdfPreview.paperOf(PdfDocument.rtl(Paper.A4).text("x").toBytes()));
        assertEquals(Paper.A5.landscape(), PdfPreview.paperOf(PdfDocument.rtl(Paper.A5.landscape()).text("x").toBytes()));
        Paper custom = PdfPreview.paperOf(foreignPdf());
        assertEquals(150 * 72f / 25.4f, custom.height(), 0.5f);
        assertThrows(IllegalArgumentException.class, () -> PdfPreview.ofPdf(new byte[]{1, 2, 3}));
    }

    /** PDF بيتبني لكل ورقة: المستخدم بيغيّر المقاس والبايتات بتتبني تاني. */
    @Test
    void aPdfBuiltPerPaper() throws Exception {
        assumeTrue(toolkit, "no display");
        List<Paper> built = new CopyOnWriteArrayList<>();
        Fake system = new Fake();
        PdfPreview preview = PdfPreview.ofPdf(paper -> {
            built.add(paper);
            return PdfDocument.ltr(paper).text("from elsewhere").toBytes();
        }).rightToLeft(false);
        preview.system = system;
        Stage stage = onFx(() -> preview.show(null));
        await("the first build", () -> built.contains(Paper.A4));
        onFx(() -> {
            PreviewAnyPdfTest.<javafx.scene.control.ComboBox<Paper>>node(stage, "paper").setValue(Paper.A5);
            return null;
        });
        await("the second build", () -> built.contains(Paper.A5));
    }

    @Test
    void theWindowTakesItsOwnersIcons() throws Exception {
        assumeTrue(toolkit, "no display");
        Stage owner = onFx(() -> {
            Stage stage = new Stage();
            stage.getIcons().add(new WritableImage(16, 16));
            stage.setScene(new Scene(new StackPane(), 100, 100));
            stage.show();
            return stage;
        });
        PdfPreview preview = PdfPreview.of(PdfDocument.rtl(Paper.A5).text("x"));
        preview.system = new Fake();
        Stage stage = onFx(() -> preview.show(owner));
        assertEquals(onFx(() -> List.copyOf(owner.getIcons())), onFx(() -> List.copyOf(stage.getIcons())));
    }
}
