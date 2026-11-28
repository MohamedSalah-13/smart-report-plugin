package com.smart.report.fx;

import com.smart.report.ReportException;
import com.smart.report.pdf.Paper;
import com.smart.report.pdf.PdfDocument;
import com.smart.report.print.DocumentKind;
import com.smart.report.print.OutputMode;
import com.smart.report.print.PrintProfile;
import com.smart.report.print.PrintSettings;
import com.smart.report.print.PrinterTarget;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A ready-made print settings screen: one row per document type ({@link DocumentKind}) with the paper, the output
 * mode, the printer, the number of copies, a button that resets it to the default, and a trial print button; under
 * them, the printer for each role. Every change is saved at once in {@link PrintSettings}, so the application
 * writes no saving code.
 *
 * <pre>{@code
 * Parent pane = PrintSettingsPane.of(settings)
 *         .rightToLeft(true)
 *         .roleLabel("thermal", "طابعة الإيصالات")
 *         .sample("sale-receipt", paper -> ReceiptPdf.sample(paper))   // optional: the trial page
 *         .build();
 * settingsTab.setContent(pane);
 * }</pre>
 *
 * <p>Without {@link #sample}, the trial page is a page with the type name, the paper, the printer and the time.
 * Printer names are queried on a background thread (network printers take seconds), and so is printing.
 * {@link #build()} is called from the UI thread.</p>
 */
public final class PrintSettingsPane {

    private static final Logger log = LoggerFactory.getLogger(PrintSettingsPane.class);

    private final PrintSettings settings;
    private final Map<String, String> roleLabels = new HashMap<>();
    private final Map<String, Function<Paper, PdfDocument>> samples = new HashMap<>();
    private boolean rightToLeft = "ar".equals(java.util.Locale.getDefault().getLanguage());
    /** The printers; tests replace it. */
    PreviewSystem system = PreviewSystem.REAL;

    private PrintSettingsPane(PrintSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public static PrintSettingsPane of(PrintSettings settings) {
        return new PrintSettingsPane(settings);
    }

    /** The screen direction and text language: Arabic (by default, if the computer's language is) or English. */
    public PrintSettingsPane rightToLeft(boolean value) {
        this.rightToLeft = value;
        return this;
    }

    /** The role's name on the screen instead of its key: {@code roleLabel("thermal", "طابعة الإيصالات")}. */
    public PrintSettingsPane roleLabel(String role, String label) {
        if (!settings.roles().contains(role)) {
            throw new IllegalArgumentException("Unknown role: " + role + "; the roles are " + settings.roles());
        }
        roleLabels.put(role, Objects.requireNonNull(label, "label"));
        return this;
    }

    /** The trial page for this type, built on the chosen paper. The function is called off the UI thread. */
    public PrintSettingsPane sample(String kindKey, Function<Paper, PdfDocument> document) {
        settings.kind(kindKey);
        samples.put(kindKey, Objects.requireNonNull(document, "document"));
        return this;
    }

    /** The screen. Each call builds a new one, read from the settings as they are now. */
    public Parent build() {
        return new View().root;
    }

    private Texts texts() {
        return rightToLeft ? Texts.ARABIC : Texts.ENGLISH;
    }

    String roleName(String role) {
        return roleLabels.getOrDefault(role, role);
    }

    /** The screen texts. */
    record Texts(String document, String paper, String output, String printer, String copies, String reset,
                 String trial, String roles, String systemDefault, String rolePrefix,
                 String print, String preview, String savePdf, String ask,
                 String sent, String failed, String trialTitle, String time) {

        static final Texts ARABIC = new Texts("المستند", "الورق", "الإخراج", "الطابعة", "النسخ",
                "الافتراضي", "طباعة تجربة", "الطابعات", "الطابعة الافتراضية", "دور: ",
                "طباعة مباشرة", "معاينة أولًا", "حفظ PDF", "اسأل كل مرة",
                "أُرسلت ورقة التجربة إلى %s", "تعذرت طباعة التجربة. تأكد من الطابعة وحاول مرة ثانية.",
                "طباعة تجربة", "الوقت");
        static final Texts ENGLISH = new Texts("Document", "Paper", "Output", "Printer", "Copies",
                "Default", "Trial print", "Printers", "System default printer", "Role: ",
                "Print directly", "Preview first", "Save a PDF", "Ask each time",
                "The trial page was sent to %s", "The trial print failed. Check the printer and try again.",
                "Trial print", "Time");

        String mode(OutputMode mode) {
            return switch (mode) {
                case PRINT -> print;
                case PREVIEW -> preview;
                case SAVE_PDF -> savePdf;
                case ASK -> ask;
            };
        }
    }

    /** The built screen, with its nodes. */
    private final class View {

        final VBox root = new VBox(12);
        final Label status = new Label();
        final Map<String, Row> rows = new LinkedHashMap<>();
        final Map<String, ComboBox<String>> rolePrinters = new LinkedHashMap<>();
        /** While the nodes are filled from the settings: that change is not from the user, so it is not saved. */
        boolean loading;

        View() {
            Texts texts = texts();
            root.setPadding(new Insets(12));
            root.setNodeOrientation(rightToLeft ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);

            GridPane kinds = new GridPane();
            kinds.setHgap(8);
            kinds.setVgap(6);
            kinds.setAlignment(Pos.TOP_LEFT);
            String[] headers = {texts.document(), texts.paper(), texts.output(), texts.printer(), texts.copies()};
            for (int i = 0; i < headers.length; i++) {
                Label header = new Label(headers[i]);
                header.setStyle("-fx-font-weight: bold;");
                kinds.add(header, i, 0);
            }
            int line = 1;
            for (DocumentKind kind : settings.kinds()) {
                Row row = new Row(kind);
                rows.put(kind.key(), row);
                kinds.addRow(line++, new Label(kind.label()), row.paper, row.mode, row.printer, row.copies,
                        row.reset, row.trial);
            }
            root.getChildren().add(kinds);

            if (!settings.roles().isEmpty()) {
                Label title = new Label(texts.roles());
                title.setStyle("-fx-font-weight: bold;");
                GridPane roles = new GridPane();
                roles.setHgap(8);
                roles.setVgap(6);
                int r = 0;
                for (String role : settings.roles()) {
                    ComboBox<String> printer = new ComboBox<>();
                    printer.setId("role-" + role);
                    printer.setConverter(names(texts));
                    printer.getItems().add("");
                    String current = settings.rolePrinter(role).orElse("");
                    if (!current.isEmpty()) {
                        printer.getItems().add(current);
                    }
                    printer.setValue(current);
                    printer.valueProperty().addListener((observable, old, chosen) -> {
                        if (!loading) {
                            settings.rolePrinter(role, chosen);
                        }
                    });
                    rolePrinters.put(role, printer);
                    roles.addRow(r++, new Label(roleName(role)), printer);
                }
                root.getChildren().addAll(title, roles);
            }
            status.setId("status");
            status.setWrapText(true);
            root.getChildren().add(status);
            loadPrinters();
        }

        private StringConverter<String> names(Texts texts) {
            return new StringConverter<>() {
                @Override
                public String toString(String name) {
                    return name == null || name.isEmpty() ? texts.systemDefault() : name;
                }

                @Override
                public String fromString(String text) {
                    return text;
                }
            };
        }

        /**
         * Reads the installed printer names on a background thread, then adds them to every list without changing
         * the selection.
         */
        private void loadPrinters() {
            Thread thread = new Thread(() -> {
                List<String> names;
                try {
                    names = system.printerNames();
                } catch (RuntimeException e) {
                    log.warn("Listing the printers failed", e);
                    names = List.of();
                }
                List<String> found = names;
                Platform.runLater(() -> {
                    loading = true;
                    try {
                        for (Row row : rows.values()) {
                            row.addPrinters(found);
                        }
                        for (ComboBox<String> printer : rolePrinters.values()) {
                            String chosen = printer.getValue();
                            for (String name : found) {
                                if (!printer.getItems().contains(name)) {
                                    printer.getItems().add(name);
                                }
                            }
                            printer.setValue(chosen);
                        }
                    } finally {
                        loading = false;
                    }
                });
            }, "print-settings-printers");
            thread.setDaemon(true);
            thread.start();
        }

        /** The row of one document type. */
        private final class Row {
            final DocumentKind kind;
            final ComboBox<String> paper = new ComboBox<>();
            final ComboBox<OutputMode> mode = new ComboBox<>();
            final ComboBox<PrinterTarget> printer = new ComboBox<>();
            final Spinner<Integer> copies = new Spinner<>(1, PrintProfile.MAX_COPIES, 1);
            final Button reset = new Button();
            final Button trial = new Button();

            Row(DocumentKind kind) {
                this.kind = kind;
                Texts texts = texts();
                String key = kind.key();
                paper.setId("paper-" + key);
                mode.setId("mode-" + key);
                printer.setId("printer-" + key);
                copies.setId("copies-" + key);
                reset.setId("reset-" + key);
                trial.setId("trial-" + key);
                paper.getItems().setAll(kind.papers());
                mode.getItems().setAll(OutputMode.values());
                mode.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(OutputMode value) {
                        return value == null ? "" : texts.mode(value);
                    }

                    @Override
                    public OutputMode fromString(String text) {
                        return null;
                    }
                });
                printer.getItems().add(PrinterTarget.systemDefault());
                for (String role : settings.roles()) {
                    printer.getItems().add(PrinterTarget.role(role));
                }
                printer.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(PrinterTarget value) {
                        return switch (value) {
                            case null -> "";
                            case PrinterTarget.SystemDefault ignored -> texts.systemDefault();
                            case PrinterTarget.Role role -> texts.rolePrefix() + roleName(role.name());
                            case PrinterTarget.Named named -> named.printer();
                        };
                    }

                    @Override
                    public PrinterTarget fromString(String text) {
                        return null;
                    }
                });
                copies.setEditable(true);
                copies.setPrefWidth(80);
                reset.setText(texts.reset());
                trial.setText(texts.trial());
                load(settings.profile(key));

                paper.valueProperty().addListener((observable, old, value) -> save());
                mode.valueProperty().addListener((observable, old, value) -> save());
                printer.valueProperty().addListener((observable, old, value) -> save());
                copies.valueProperty().addListener((observable, old, value) -> save());
                reset.setOnAction(event -> {
                    settings.reset(key);
                    load(settings.profile(key));
                    status.setText("");
                });
                trial.setOnAction(event -> trialPrint());
            }

            /** Fills the nodes from the profile, without that counting as a change. */
            void load(PrintProfile profile) {
                loading = true;
                try {
                    paper.setValue(profile.paper());
                    mode.setValue(profile.mode());
                    if (!printer.getItems().contains(profile.printer())) {
                        printer.getItems().add(profile.printer());
                    }
                    printer.setValue(profile.printer());
                    copies.getValueFactory().setValue(profile.copies());
                } finally {
                    loading = false;
                }
            }

            void addPrinters(List<String> names) {
                PrinterTarget chosen = printer.getValue();
                for (String name : names) {
                    PrinterTarget target = PrinterTarget.named(name);
                    if (!printer.getItems().contains(target)) {
                        printer.getItems().add(target);
                    }
                }
                printer.setValue(chosen);
            }

            PrintProfile current() {
                return PrintProfile.of(paper.getValue(), mode.getValue(), printer.getValue(), copies.getValue());
            }

            private void save() {
                if (loading || paper.getValue() == null || mode.getValue() == null || printer.getValue() == null
                        || copies.getValue() == null) {
                    return;
                }
                settings.save(kind.key(), current());
            }

            /** Prints one trial page on the printer and paper chosen right now. */
            private void trialPrint() {
                PrintProfile profile = current();
                Texts texts = texts();
                trial.setDisable(true);
                Thread thread = new Thread(() -> {
                    String message;
                    boolean failed = false;
                    try {
                        Paper chosen = Paper.parse(profile.paper());
                        String name = settings.printerName(profile)
                                .or(() -> system.defaultPrinter())
                                .orElseThrow(() -> new ReportException("No printer is set up on this computer."));
                        Function<Paper, PdfDocument> sample = samples.get(kind.key());
                        PdfDocument document = sample != null ? sample.apply(chosen) : testPage(profile, chosen, name, texts);
                        system.print(document.toBytes(), name, chosen, 1);
                        message = String.format(texts.sent(), name);
                    } catch (ReportException | RuntimeException e) {
                        log.warn("The trial print of {} failed", kind.key(), e);
                        message = texts.failed();
                        failed = true;
                    }
                    String shown = message;
                    boolean error = failed;
                    Platform.runLater(() -> {
                        trial.setDisable(false);
                        status.setText(shown);
                        status.setStyle(error ? "-fx-text-fill: #b00020;" : "");
                    });
                }, "print-settings-trial");
                thread.setDaemon(true);
                thread.start();
            }

            private PdfDocument testPage(PrintProfile profile, Paper chosen, String name, Texts texts) {
                PdfDocument page = rightToLeft ? PdfDocument.rtl(chosen) : PdfDocument.ltr(chosen);
                return page.title(texts.trialTitle())
                        .field(texts.document(), kind.label())
                        .field(texts.paper(), profile.paper())
                        .field(texts.printer(), name)
                        .field(texts.time(), LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }
        }
    }
}
