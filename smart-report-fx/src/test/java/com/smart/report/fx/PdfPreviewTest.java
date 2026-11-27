package com.smart.report.fx;

import com.smart.report.pdf.Paper;
import com.smart.report.pdf.PdfDocument;
import com.smart.report.pdf.PdfTable;
import javafx.application.Platform;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** النافذة على JavaFX حقيقي. على جهاز بلا شاشة الاختبارات بتتخطى بدل ما تفشل. */
class PdfPreviewTest {

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

    /** بيستنى لحد ما الشرط يتحقق على thread الواجهة: المستند بيتبني ويترسم على thread جانبي. */
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

    private static String pageText(Stage stage) {
        return PdfPreviewTest.<Label>node(stage, "page-label").getText();
    }

    private static int pageCount(Stage stage) {
        String text = pageText(stage);
        return text.isEmpty() ? 0 : Integer.parseInt(text.substring(text.lastIndexOf(' ') + 1));
    }

    @Test
    void opensOnTheFirstPageAndTurnsThePages() throws Exception {
        assumeTrue(toolkit, "no display");
        Stage stage = onFx(() -> PdfPreview.of(PdfPreviewTest::report).rightToLeft(false).show(null));

        await("the first page", () -> pageText(stage).startsWith("Page 1 of ")
                && PdfPreviewTest.<ImageView>node(stage, "page-image").getImage() != null);
        int pages = onFx(() -> pageCount(stage));
        assertTrue(pages >= 2, "تسعين صف أكتر من صفحة: " + pages);
        assertTrue(onFx(() -> PdfPreviewTest.<Button>node(stage, "previous").isDisabled()));
        assertFalse(onFx(() -> PdfPreviewTest.<Button>node(stage, "save").isDisabled()), "الحفظ متاح بعد التحميل");

        onFx(() -> {
            PdfPreviewTest.<Button>node(stage, "next").fire();
            return null;
        });
        await("the second page", () -> pageText(stage).startsWith("Page 2 of "));
        assertFalse(onFx(() -> PdfPreviewTest.<Button>node(stage, "previous").isDisabled()));
    }

    /** المقاس بيتغيّر من النافذة والمستند بيتبني من جديد: A5 أصغر فصفحاته أكتر. */
    @Test
    void changingThePaperRebuildsTheDocument() throws Exception {
        assumeTrue(toolkit, "no display");
        Stage stage = onFx(() -> PdfPreview.of(PdfPreviewTest::report).rightToLeft(false).paper(Paper.A4).show(null));
        await("the A4 document", () -> pageCount(stage) > 0);
        int onA4 = onFx(() -> pageCount(stage));

        onFx(() -> {
            PdfPreviewTest.<ComboBox<Paper>>node(stage, "paper").setValue(Paper.A5);
            return null;
        });
        await("the A5 document", () -> pageCount(stage) > onA4);

        // الصورة بنسبة الورقة الجديدة.
        await("the A5 page", () -> {
            ImageView view = node(stage, "page-image");
            return view.getImage() != null
                    && Math.abs(view.getFitHeight() / view.getFitWidth() - Paper.A5.height() / Paper.A5.width()) < 0.01;
        });
    }

    @Test
    void readyDocumentIsShownOnItsOwnPaperWithNoPaperChoice() throws Exception {
        assumeTrue(toolkit, "no display");
        Stage stage = onFx(() -> PdfPreview.of(report(Paper.A5)).show(null));

        await("the document", () -> pageCount(stage) > 0);
        assertNull(onFx(() -> stage.getScene().lookup("#paper")), "مقاس واحد = بلا اختيار");
    }

    @Test
    void arabicDocumentOpensAnArabicWindow() throws Exception {
        assumeTrue(toolkit, "no display");
        PdfDocument arabic = PdfDocument.rtl(Paper.A5).title("تقرير").text("سطر");
        Stage stage = onFx(() -> PdfPreview.of(arabic).title("كشف").show(null));

        await("the document", () -> pageText(stage).contains("1"));
        assertEquals(NodeOrientation.RIGHT_TO_LEFT, onFx(() -> stage.getScene().getNodeOrientation()));
        assertEquals("صفحة 1 من 1", onFx(() -> pageText(stage)));
        assertEquals("كشف", onFx(stage::getTitle));
        assertEquals(PreviewTexts.arabic().print(), onFx(() -> PdfPreviewTest.<Button>node(stage, "print").getText()));
    }

    /** آخر مقاس اختاره المستخدم بيتفتح عليه المرة الجاية. */
    @Test
    void lastPaperIsRememberedUnderTheKey() throws Exception {
        assumeTrue(toolkit, "no display");
        String key = "test-" + System.nanoTime();
        try {
            Stage first = onFx(() -> PdfPreview.of(PdfPreviewTest::report).remember(key).show(null));
            await("the document", () -> pageCount(first) > 0);
            assertEquals(Paper.A4, onFx(() -> PdfPreviewTest.<ComboBox<Paper>>node(first, "paper").getValue()));
            onFx(() -> {
                PdfPreviewTest.<ComboBox<Paper>>node(first, "paper").setValue(Paper.A5);
                first.hide();
                return null;
            });

            Stage second = onFx(() -> PdfPreview.of(PdfPreviewTest::report).remember(key).show(null));
            assertEquals(Paper.A5, onFx(() -> PdfPreviewTest.<ComboBox<Paper>>node(second, "paper").getValue()));
        } finally {
            Preferences.userRoot().node("com/smart/report/preview/" + key).removeNode();
        }
    }

    /** مستند فشل بناؤه: النافذة بتقول كده وما بتتجمدش، والطباعة والحفظ مقفولين. */
    @Test
    void documentThatFailsToBuildIsReportedInTheWindow() throws Exception {
        assumeTrue(toolkit, "no display");
        Stage stage = onFx(() -> PdfPreview.of(paper -> {
            throw new IllegalStateException("no data");
        }).rightToLeft(false).show(null));

        await("the failure message", () -> PreviewTexts.english().buildFailed()
                .equals(PdfPreviewTest.<Label>node(stage, "status").getText()));
        assertTrue(onFx(() -> PdfPreviewTest.<Button>node(stage, "save").isDisabled()));
        assertTrue(onFx(() -> PdfPreviewTest.<Button>node(stage, "print").isDisabled()));
        assertNotNull(stage);
    }

    private static PdfDocument receipt(Paper paper, int items) {
        PdfTable table = PdfTable.create().column("Item", 3).column("Price", 2);
        for (int i = 1; i <= items; i++) {
            table.row("item" + i, "10.00");
        }
        return PdfDocument.ltr(paper).letterhead("Corner Shop").table(table).summaryTotal("Total", "99.00");
    }

    private static double shownWidth(Stage stage) {
        return PdfPreviewTest.<ImageView>node(stage, "page-image").getFitWidth();
    }

    /**
     * الإيصال الطويل بيتفتح مقروء: بعرض النافذة لحد ضعف مقاسه، مش شريط رفيع بطول الشاشة. واختيار
     * المستخدم للعرض بيفضل.
     */
    @Test
    void longReceiptOpensReadableAndTheChosenViewStays() throws Exception {
        assumeTrue(toolkit, "no display");
        float width = Paper.ROLL_80.width();
        Stage stage = onFx(() -> PdfPreview.of(receipt(Paper.ROLL_80, 120)).show(null));
        await("the receipt", () -> PdfPreviewTest.<ImageView>node(stage, "page-image").getImage() != null);

        double shown = onFx(() -> shownWidth(stage));
        double room = onFx(() -> stage.getScene().lookup(".scroll-pane") instanceof ScrollPane pane
                ? pane.getViewportBounds().getWidth() : 0.0);
        assertEquals(Math.min(2 * width, room - 2 * 18 - 2), shown, 1, "العرض " + shown + " من " + room);
        assertTrue(shown >= 1.5 * width, "مش شريط رفيع: " + shown);

        onFx(() -> {
            PdfPreviewTest.<Button>node(stage, "fit-page").fire();
            return null;
        });
        await("the whole receipt", () -> shownWidth(stage) < width);
        double whole = onFx(() -> shownWidth(stage));
        onFx(() -> {
            stage.setWidth(stage.getWidth() - 60);
            return null;
        });
        Thread.sleep(300);
        assertTrue(onFx(() -> shownWidth(stage)) <= whole + 0.5, "اختيار «الصفحة كاملة» بيفضل بعد التحجيم");
    }

    /** من إيصال لـ A4: العرض الافتراضي بيرجع «الصفحة كاملة» طالما المستخدم ما اختارش. */
    @Test
    void changingFromARollToAPageShowsTheWholePage() throws Exception {
        assumeTrue(toolkit, "no display");
        Stage stage = onFx(() -> PdfPreview.of(paper -> receipt(paper, 30)).papers(Paper.ROLL_80, Paper.A4)
                .rightToLeft(false).show(null));
        await("the receipt", () -> PdfPreviewTest.<ImageView>node(stage, "page-image").getImage() != null
                && shownWidth(stage) > Paper.ROLL_80.width());

        onFx(() -> {
            PdfPreviewTest.<ComboBox<Paper>>node(stage, "paper").setValue(Paper.A4);
            return null;
        });
        await("the whole A4 page", () -> {
            ImageView view = node(stage, "page-image");
            ScrollPane pane = (ScrollPane) stage.getScene().lookup(".scroll-pane");
            return Math.abs(view.getFitHeight() / view.getFitWidth() - Paper.A4.height() / Paper.A4.width()) < 0.01
                    && view.getFitHeight() <= pane.getViewportBounds().getHeight();
        });
    }

    /** صورة الإيصال الطويل جدًا بتفضل جوّه الحد، والمعروض على الشاشة بنفس المقاس. */
    @Test
    void veryLongReceiptIsDrawnWithinTheImageLimit() throws Exception {
        assumeTrue(toolkit, "no display");
        PdfDocument document = receipt(Paper.ROLL_80, 500);
        Stage stage = onFx(() -> PdfPreview.of(document).show(null));
        await("the receipt", () -> PdfPreviewTest.<ImageView>node(stage, "page-image").getImage() != null);

        ImageView view = onFx(() -> node(stage, "page-image"));
        double imageHeight = onFx(() -> view.getImage().getHeight());
        double shownHeight = onFx(view::getFitHeight);
        assertTrue(imageHeight <= PreviewWindow.MAX_IMAGE_SIDE, "" + imageHeight);
        assertTrue(shownHeight > PreviewWindow.MAX_IMAGE_SIDE, "الإيصال أطول من الحد على الشاشة: " + shownHeight);
    }

    @Test
    void renderScaleKeepsThePageImageWithinTheLimits() {
        assertEquals(2f, PreviewWindow.renderScale(595f, 842f, 2), 1e-6, "A4 زي ما هي");
        assertEquals(4f, PreviewWindow.renderScale(595f, 842f, 9), 1e-6, "لحد أقصى دقة");

        float roll = PreviewWindow.renderScale(226.77f, 14400f, 4);
        assertTrue(14400f * roll <= PreviewWindow.MAX_IMAGE_SIDE + 0.5f, "" + roll);
        float square = PreviewWindow.renderScale(3000f, 3000f, 4);
        assertTrue(3000.0 * square * 3000.0 * square <= PreviewWindow.MAX_IMAGE_PIXELS * 1.0001, "" + square);
    }

    @Test
    void fileNameAlwaysEndsInPdf() {
        assertEquals("كشف 1751.pdf", PdfPreview.of(report(Paper.A4)).fileName("كشف 1751").fileName);
        assertEquals("a.PDF", PdfPreview.of(report(Paper.A4)).fileName("a.PDF").fileName);
        assertEquals("document.pdf", PdfPreview.of(report(Paper.A4)).fileName(" ").fileName);
    }
}
