package com.smart.report.fx;

import com.smart.report.ReportException;
import com.smart.report.pdf.Paper;
import com.smart.report.pdf.PdfPages;
import com.smart.report.pdf.PdfPrinter;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;

/**
 * نافذة المعاينة نفسها. بناء المستند ورسم الصفحات بيحصلوا على thread واحد جانبي بالترتيب، فالواجهة
 * ما بتتجمدش ومفيش رسمتين بيشتغلوا على نفس المستند في نفس الوقت؛ والطباعة على thread لوحدها
 * عشان الطابعة البطيئة ما توقفش تقليب الصفحات.
 */
final class PreviewWindow {

    private static final Logger log = LoggerFactory.getLogger(PreviewWindow.class);

    private static final double PAGE_MARGIN = 18;
    private static final double ZOOM_STEP = 1.25;
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 4;
    /**
     * الإيصال بيتفتح بعرض النافذة لحد ضعف مقاسه: أكبر من كده خط الإيصال الضيّق بيبقى ضخم في نافذة
     * عريضة، و«الصفحة كاملة» بتخلّي الإيصال الطويل شريط رفيع ما يتقريش.
     */
    private static final double ROLL_SCALE = 2;
    /**
     * أكبر صورة بترسمها المعاينة للصفحة: ضلع 8192 بكسل (كروت شاشة كتير ما بتعرضش صورة أكبر في
     * قطعة واحدة) وحوالي 16 مليون بكسل (64 ميجا). الإيصال الأطول بيترسم بدقة أقل ويتكبّر لنفس مقاسه
     * على الشاشة؛ الطباعة والحفظ من الـ PDF نفسه فمش بيتأثروا.
     */
    static final int MAX_IMAGE_SIDE = 8192;
    static final double MAX_IMAGE_PIXELS = 8192.0 * 2048;
    /** Ctrl+P و Ctrl+S (⌘ على الماك): بيدوسوا زرّي الطباعة والحفظ، فالزر المقفول مفتاحه مقفول. */
    static final KeyCombination PRINT_KEY = new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN);
    static final KeyCombination SAVE_KEY = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);

    /** {@code ROLL}: عرض الورق اللفة الافتراضي، لحد ما المستخدم يختار عرض بنفسه. */
    private enum Fit { PAGE, WIDTH, ROLL, NONE }

    /** مستند اتبنى واتفتح: بايتاته للطباعة والحفظ، وصفحاته ومقاساتها للعرض. */
    private record Loaded(Paper paper, byte[] bytes, PdfPages pages, float[] widths, float[] heights) {
    }

    private final PdfPreview setup;
    private final PreviewTexts texts;
    private final Preferences remembered;
    private final Stage stage = new Stage();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pdf-preview");
        thread.setDaemon(true);
        return thread;
    });
    /** كل طلب رسم بياخد رقمًا؛ الرسمة اللي رقمها قديم بتتهمل. */
    private final AtomicLong ticket = new AtomicLong();
    /**
     * رقم لوحده لتحميل المستند. لو اشترك مع الرسم، أي إعادة رسم أثناء التحميل (تحجيم النافذة مثلًا)
     * كانت تلغي المستند اللي بيتحمّل وتسيب النافذة فاضية.
     */
    private final AtomicLong loadTicket = new AtomicLong();

    private final ComboBox<String> printer = new ComboBox<>();
    private final ComboBox<Paper> paper = new ComboBox<>();
    private final Spinner<Integer> copies = new Spinner<>(1, PdfPrinter.MAX_COPIES, 1);
    private final javafx.scene.control.TextField pages = new javafx.scene.control.TextField();
    private final javafx.scene.control.CheckBox twoSided = new javafx.scene.control.CheckBox();
    private final PrintTexts printTexts;
    private final Button print = new Button();
    private final Button save = new Button();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final Label pageLabel = new Label();
    private final Label status = new Label();
    private final ImageView view = new ImageView();
    private final StackPane holder = new StackPane(view);
    private final ScrollPane scroll = new ScrollPane(holder);
    private final PauseTransition redraw = new PauseTransition(Duration.millis(140));

    private Loaded loaded;
    private int pageIndex;
    private Fit fit = Fit.PAGE;
    /** المستخدم اختار تكبير أو ملء: بيفضل زي ما هو حتى لو غيّر الورق. */
    private boolean viewChosen;
    private double zoom = 1;
    private boolean printing;
    private boolean closed;
    /** القائمة اتسألت ورجعت فاضية: الرسالة دي بتفضل ظاهرة مهما اتحمّل المستند بعدها. */
    private boolean noPrinters;

    PreviewWindow(PdfPreview setup, Window owner) {
        this.setup = setup;
        this.texts = setup.resolvedTexts();
        this.printTexts = PrintTexts.of(setup.rightToLeft);
        this.remembered = setup.remembered();
        if (owner != null) {
            stage.initOwner(owner);
            if (owner instanceof Stage parent) {
                // أيقونة البرنامج في شريط المهام بدل أيقونة Java.
                stage.getIcons().setAll(parent.getIcons());
            }
        }
        stage.setTitle(setup.title == null || setup.title.isBlank() ? texts.title() : setup.title);
        stage.setScene(scene());
        stage.setOnHidden(event -> close());
    }

    Stage show() {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(880, screen.getWidth() * 0.92));
        stage.setHeight(Math.min(940, screen.getHeight() * 0.92));
        stage.show();
        loadPrinters();
        load(paper.getValue());
        return stage;
    }

    // ===================== الواجهة =====================

    private Scene scene() {
        printer.setId("printer");
        printer.setPrefWidth(250);
        paper.setId("paper");
        paper.getItems().setAll(setup.papers);
        paper.setConverter(new StringConverter<>() {
            @Override
            public String toString(Paper value) {
                return value == null ? "" : value.isLandscape() ? value.name() + " " + texts.landscape() : value.name();
            }

            @Override
            public Paper fromString(String text) {
                return null;
            }
        });
        paper.setValue(setup.startingPaper(remembered));
        paper.valueProperty().addListener((property, before, after) -> {
            if (after != null) {
                remember("paper", PdfPreview.paperKey(after));
                load(after);
            }
        });
        copies.setId("copies");
        copies.setPrefWidth(80);
        copies.getValueFactory().setValue(setup.copies);

        print.setId("print");
        print.setText(texts.print());
        print.setDefaultButton(true);
        print.setDisable(true);
        print.setOnAction(event -> print());
        print.setTooltip(new Tooltip(PRINT_KEY.getDisplayText()));
        save.setId("save");
        save.setText(texts.savePdf());
        save.setDisable(true);
        save.setOnAction(event -> save());
        save.setTooltip(new Tooltip(SAVE_KEY.getDisplayText()));

        HBox top = new HBox(8, labelled(texts.printer(), printer), printer);
        if (setup.papers.size() > 1) {
            top.getChildren().addAll(labelled(texts.paper(), paper), paper);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        top.getChildren().addAll(labelled(texts.copies(), copies), copies);
        if (!setup.papers.stream().allMatch(Paper::roll)) {
            // مدى الصفحات والوجهين للورق المقصوص بس: الإيصال صفحة واحدة على لفة.
            pages.setId("pages");
            pages.setPromptText(printTexts.allPages());
            pages.setPrefColumnCount(5);
            twoSided.setId("two-sided");
            twoSided.setText(printTexts.twoSided());
            top.getChildren().addAll(labelled(printTexts.pages(), pages), pages, twoSided);
        }
        top.getChildren().addAll(spacer, save, print);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10, 12, 10, 12));

        view.setId("page-image");
        view.setSmooth(true);
        view.setEffect(new DropShadow(12, Color.rgb(0, 0, 0, 0.35)));
        holder.setPadding(new Insets(PAGE_MARGIN));
        holder.setStyle("-fx-background-color: #7b8794;");
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background: #7b8794; -fx-background-color: #7b8794;");
        // الورقة صورة: ما تتقلبش ولا يتقلب تمريرها مع النافذة العربية.
        scroll.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        scroll.viewportBoundsProperty().addListener((property, before, after) -> {
            if (fit != Fit.NONE && loaded != null) {
                // الصورة الحالية بتتمدد فورًا، والرسم بالدقة الجديدة بعد ما التحجيم يهدى.
                resize();
                redraw.playFromStart();
            }
        });
        redraw.setOnFinished(event -> render());

        // السهم بيشاور ناحية الصفحة: في النافذة العربية «التالية» على اليسار. مثلثات مش أقواس: الأقواس
        // (‹ ›) حروف بتتقلب مع اتجاه النص فكانت تطلع معكوسة.
        previous.setText(setup.rightToLeft ? "▶" : "◀");
        next.setText(setup.rightToLeft ? "◀" : "▶");
        previous.setId("previous");
        previous.setTooltip(new Tooltip(texts.previous()));
        previous.setOnAction(event -> show(pageIndex - 1));
        next.setId("next");
        next.setTooltip(new Tooltip(texts.next()));
        next.setOnAction(event -> show(pageIndex + 1));
        pageLabel.setId("page-label");
        pageLabel.setMinWidth(Region.USE_PREF_SIZE);

        Button zoomOut = new Button("−");
        zoomOut.setId("zoom-out");
        zoomOut.setTooltip(new Tooltip(texts.zoomOut()));
        zoomOut.setOnAction(event -> zoom(1 / ZOOM_STEP));
        Button zoomIn = new Button("+");
        zoomIn.setId("zoom-in");
        zoomIn.setTooltip(new Tooltip(texts.zoomIn()));
        zoomIn.setOnAction(event -> zoom(ZOOM_STEP));
        Button fitPage = new Button(texts.fitPage());
        fitPage.setId("fit-page");
        fitPage.setOnAction(event -> fit(Fit.PAGE));
        Button fitWidth = new Button(texts.fitWidth());
        fitWidth.setId("fit-width");
        fitWidth.setOnAction(event -> fit(Fit.WIDTH));

        status.setId("status");
        status.setWrapText(true);
        HBox.setHgrow(status, Priority.ALWAYS);
        status.setMaxWidth(Double.MAX_VALUE);

        HBox pager = new HBox(6, previous, pageLabel, next);
        pager.setAlignment(Pos.CENTER);
        HBox bottom = new HBox(10, pager, zoomOut, zoomIn, fitPage, fitWidth, status);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(8, 12, 8, 12));

        VBox header = new VBox(top);
        if (setup.notice != null) {
            // سطر البرنامج فوق الصفحة: سبب فتح المعاينة، أو تنبيه للمستخدم قبل ما يطبع.
            Label notice = new Label(setup.notice);
            notice.setId("notice");
            notice.setWrapText(true);
            notice.setMaxWidth(Double.MAX_VALUE);
            notice.setPadding(new Insets(6, 12, 6, 12));
            notice.setStyle("-fx-background-color: #fff4ce; -fx-text-fill: #5c4400;");
            header.getChildren().add(notice);
        }
        BorderPane root = new BorderPane(scroll, header, null, new VBox(bottom), null);
        Scene scene = new Scene(root);
        scene.setNodeOrientation(setup.rightToLeft ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        scene.getStylesheets().addAll(setup.stylesheets);
        scene.setOnKeyPressed(event -> {
            if (PRINT_KEY.match(event)) {
                print.fire();   // Button.fire ما بيعملش حاجة والزر مقفول
                event.consume();
            } else if (SAVE_KEY.match(event)) {
                save.fire();
                event.consume();
            } else if (event.getCode() == KeyCode.PAGE_DOWN) {
                show(pageIndex + 1);
            } else if (event.getCode() == KeyCode.PAGE_UP) {
                show(pageIndex - 1);
            } else if (event.getCode() == KeyCode.ESCAPE) {
                stage.hide();
            }
        });
        updatePager();
        return scene;
    }

    private static Label labelled(String text, javafx.scene.control.Control field) {
        Label label = new Label(text);
        label.setLabelFor(field);
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private void remember(String key, String value) {
        if (remembered != null && value != null) {
            remembered.put(key, value);
        }
    }

    // ===================== التحميل والرسم =====================

    /**
     * قائمة الطابعات بتتسأل من النظام خارج thread الواجهة: مع طابعات الشبكة بتاخد ثواني. وعلى thread
     * لوحدها مش thread الرسم، وإلا الصفحة الأولى تستنى الطابعات قبل ما تظهر.
     */
    private void loadPrinters() {
        Thread thread = new Thread(() -> {
            List<String> names = setup.system.printerNames();
            String preferred = setup.system.defaultPrinter().orElse(null);
            Platform.runLater(() -> {
                if (closed) {
                    return;
                }
                printer.getItems().setAll(names);
                String last = remembered == null ? null : remembered.get("printer", null);
                printer.setValue(setup.printer != null && names.contains(setup.printer) ? setup.printer
                        : last != null && names.contains(last) ? last
                        : preferred != null && names.contains(preferred) ? preferred
                        : names.isEmpty() ? null : names.get(0));
                noPrinters = names.isEmpty();
                if (noPrinters && status.getText().isEmpty()) {
                    say(texts.noPrinter(), false);
                }
                updateActions();
            });
        }, "pdf-preview-printers");
        thread.setDaemon(true);
        thread.start();
    }

    private void load(Paper chosen) {
        long mine = loadTicket.incrementAndGet();
        say(texts.preparing(), false);
        worker.submit(() -> {
            Loaded fresh;
            try {
                byte[] bytes = setup.source.pdf(chosen);
                PdfPages pages = PdfPages.open(bytes);
                int count = pages.count();
                float[] widths = new float[count];
                float[] heights = new float[count];
                for (int i = 0; i < count; i++) {
                    widths[i] = pages.width(i);
                    heights[i] = pages.height(i);
                }
                fresh = new Loaded(chosen, bytes, pages, widths, heights);
            } catch (ReportException | RuntimeException e) {
                log.warn("The document could not be prepared for the preview", e);
                Platform.runLater(() -> {
                    if (mine == loadTicket.get()) {
                        say(texts.buildFailed(), true);
                    }
                });
                return;
            }
            Platform.runLater(() -> {
                if (closed || mine != loadTicket.get()) {
                    release(fresh);
                    return;
                }
                Loaded old = loaded;
                loaded = fresh;
                if (!viewChosen) {
                    fit = fresh.paper().roll() ? Fit.ROLL : Fit.PAGE;
                }
                pageIndex = Math.clamp(pageIndex, 0, Math.max(0, fresh.widths().length - 1));
                say(noPrinters ? texts.noPrinter() : "", false);
                updatePager();
                updateActions();
                render();
                release(old);
            });
        });
    }

    /** المستند القديم بيتقفل على نفس thread الرسم، بعد أي رسمة لسه شغالة عليه. */
    private void release(Loaded old) {
        if (old == null) {
            return;
        }
        Runnable closing = () -> {
            try {
                old.pages().close();
            } catch (ReportException e) {
                log.debug("A previewed document could not be closed", e);
            }
        };
        if (worker.isShutdown()) {
            closing.run();
        } else {
            worker.submit(closing);
        }
    }

    private void show(int index) {
        if (loaded == null || index < 0 || index >= loaded.widths().length || index == pageIndex) {
            return;
        }
        pageIndex = index;
        scroll.setVvalue(0);
        updatePager();
        render();
    }

    private void zoom(double factor) {
        if (loaded == null) {
            return;
        }
        zoom = Math.clamp(scale() * factor, MIN_ZOOM, MAX_ZOOM);
        fit = Fit.NONE;
        viewChosen = true;
        render();
    }

    private void fit(Fit mode) {
        fit = mode;
        viewChosen = true;
        render();
    }

    /** بكسلات الشاشة لكل نقطة من الورقة، حسب وضع العرض. */
    private double scale() {
        if (loaded == null || fit == Fit.NONE) {
            return zoom;
        }
        double room = scroll.getViewportBounds().getWidth() - 2 * PAGE_MARGIN - 2;
        double tall = scroll.getViewportBounds().getHeight() - 2 * PAGE_MARGIN - 2;
        if (room <= 0 || tall <= 0) {
            return zoom;
        }
        double byWidth = room / loaded.widths()[pageIndex];
        double chosen = switch (fit) {
            case WIDTH -> byWidth;
            case ROLL -> Math.min(byWidth, ROLL_SCALE);
            default -> Math.min(byWidth, tall / loaded.heights()[pageIndex]);
        };
        return Math.clamp(chosen, MIN_ZOOM, MAX_ZOOM);
    }

    /**
     * دقة رسم الصفحة (بكسل لكل نقطة): المطلوبة، لحد {@link PdfPages#MAX_SCALE} ولحد ما الصورة تفضل
     * جوّه {@link #MAX_IMAGE_SIDE} و{@link #MAX_IMAGE_PIXELS}.
     */
    static float renderScale(float width, float height, double wanted) {
        double bySide = MAX_IMAGE_SIDE / Math.max(width, height);
        double byArea = Math.sqrt(MAX_IMAGE_PIXELS / ((double) width * height));
        return (float) Math.min(Math.min(wanted, PdfPages.MAX_SCALE), Math.min(bySide, byArea));
    }

    private void resize() {
        double scale = scale();
        view.setFitWidth(loaded.widths()[pageIndex] * scale);
        view.setFitHeight(loaded.heights()[pageIndex] * scale);
    }

    private void render() {
        if (loaded == null || loaded.widths().length == 0) {
            return;
        }
        resize();
        long mine = ticket.incrementAndGet();
        Loaded current = loaded;
        int index = pageIndex;
        // على شاشة عالية الكثافة الصفحة بتترسم ببكسلات أكتر من مقاسها المعروض.
        float pixels = renderScale(current.widths()[index], current.heights()[index],
                scale() * Math.max(1, stage.getOutputScaleX()));
        worker.submit(() -> {
            if (mine != ticket.get()) {
                return;
            }
            try {
                Image image = toFx(current.pages().render(index, pixels));
                Platform.runLater(() -> {
                    if (mine == ticket.get()) {
                        view.setImage(image);
                    }
                });
            } catch (ReportException | RuntimeException e) {
                log.warn("Page {} could not be drawn for the preview", index + 1, e);
            }
        });
    }

    /** بكسلات AWT لصورة JavaFX مباشرة، من غير وحدة javafx.swing. */
    static Image toFx(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        WritableImage out = new WritableImage(width, height);
        out.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return out;
    }

    private void updatePager() {
        int count = loaded == null ? 0 : loaded.widths().length;
        pageLabel.setText(count == 0 ? "" : String.format(texts.pageOf(), pageIndex + 1, count));
        previous.setDisable(count == 0 || pageIndex == 0);
        next.setDisable(count == 0 || pageIndex >= count - 1);
    }

    private void updateActions() {
        save.setDisable(loaded == null);
        print.setDisable(loaded == null || printing || printer.getValue() == null);
    }

    private void say(String message, boolean error) {
        status.setText(message);
        status.setStyle(error ? "-fx-text-fill: #b42318;" : "");
    }

    // ===================== الطباعة والحفظ =====================

    private void print() {
        Loaded current = loaded;
        String target = printer.getValue();
        if (current == null || target == null || printing) {
            return;
        }
        int count = copies.getValue();
        com.smart.report.pdf.PrintOptions options;
        try {
            options = com.smart.report.pdf.PrintOptions.copies(count).pages(pages.getText(), current.widths().length)
                    .duplex(twoSided.isSelected() ? com.smart.report.pdf.PrintOptions.Duplex.LONG_EDGE : null);
        } catch (IllegalArgumentException wrong) {
            say(String.format(printTexts.badPages(), current.widths().length), true);
            pages.requestFocus();
            return;
        }
        printing = true;
        updateActions();
        // الطابعة والمقاس اللي اتطبع عليهم هم اللي printOrShow بتطبع عليهم المرة الجاية.
        remember("printer", target);
        remember("paper", PdfPreview.paperKey(current.paper()));
        Thread thread = new Thread(() -> {
            boolean sent;
            try {
                setup.system.print(current.bytes(), target, current.paper(), options);
                sent = true;
            } catch (ReportException | RuntimeException e) {
                log.warn("Printing on {} failed", target, e);
                sent = false;
            }
            boolean ok = sent;
            Platform.runLater(() -> {
                printing = false;
                updateActions();
                say(ok ? String.format(texts.sent(), target) : texts.printFailed(), !ok);
            });
        }, "pdf-preview-print");
        thread.setDaemon(true);
        thread.start();
    }

    private void save() {
        Loaded current = loaded;
        if (current == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(texts.saveTitle());
        chooser.setInitialFileName(setup.fileName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File target = setup.system.chooseSaveFile(chooser, stage);
        if (target == null) {
            return;
        }
        try {
            Files.write(target.toPath(), current.bytes());
            say(String.format(texts.saved(), target.getAbsolutePath()), false);
        } catch (IOException | RuntimeException e) {
            log.warn("Saving the previewed document to {} failed", target, e);
            say(texts.saveFailed(), true);
        }
    }

    private void close() {
        closed = true;
        ticket.incrementAndGet();
        loadTicket.incrementAndGet();
        redraw.stop();
        Loaded old = loaded;
        loaded = null;
        release(old);
        worker.shutdown();
    }
}
